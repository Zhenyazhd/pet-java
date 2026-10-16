package com.jobsearch.joblist.rank;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.profile.SearchProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Asks the model how well each job fits a profile and stores the answers as matches. */
@Component
public class JobRanker {

	private static final Logger log = LoggerFactory.getLogger(JobRanker.class);
	private static final String MODEL_DOWN = "The model keeps giving answers that cannot be used";
	/** An entry is {"id": 123, "score": 80, "reason": "..."} with a reason of up to 500 characters (the parser's cap): at most ~190 tokens. */
	private static final int TOKENS_PER_JOB = 200;
	private static final int ANSWER_OVERHEAD_TOKENS = 100;

	private final LlmClient llm;
	private final RankPrompt prompt;
	private final MatchRepository matches;
	private final ObjectMapper json;
	private final RankFailures failures;
	private final int batchSize;

	public JobRanker(LlmClient llm, RankPrompt prompt, MatchRepository matches, RankFailures failures, ObjectMapper json, JobListProperties properties) {
		this.llm = llm;
		this.prompt = prompt;
		this.matches = matches;
		this.failures = failures;
		this.json = json;
		this.batchSize = properties.llm().batchSize();
	}

	public String promptHash(SearchProfile profile) {
		return prompt.hash(profile.getExpectations());
	}

	/**
	 * Decides, for each model call, whether it may be made, and books what it cost. The ranker does not know about runs
	 * or money; whoever calls it does (see RunPipeline).
	 */
	public interface CallGuard {

		/** Called before every model call; it may throw to stop the whole ranking (the run was released). */
		Ticket beforeCall();

		/**
		 * Called after every call that was allowed. {@code costKnown} false means the cost is not known (the provider did
		 * not report it, or the call may have been billed without an answer): the guard must then count the worst case.
		 */
		void afterCall(Ticket ticket, BigDecimal costUsd, boolean costKnown);

		/** {@code refusal} is null when the call may be made. */
		record Ticket(Long reservationId, String refusal) {

			public boolean granted() {
				return refusal == null;
			}
		}
	}

	/**
	 * Ranks the jobs in batches; what a batch costs and who is blamed depends on what the model answered.
	 * <ul>
	 * <li>A good answer: one call.</li>
	 * <li>An answer with some usable entries: the jobs it left out are asked once more, alone (a second call). The ones
	 * still missing then failed on their own: they are recorded, and after two failures a job is not offered again for
	 * that prompt. The model was answering, so nothing else is blamed.</li>
	 * <li>An answer with <em>no</em> usable entry (not JSON, cut off, every entry bad): nothing says which job is at
	 * fault, so nobody is blamed. The batch is split once and each half is asked (with the same two steps as above, but
	 * no further split). A half that is fine is ranked. If both halves rank nothing the model is not giving usable
	 * answers now, and the pass stops ("keeps giving answers that cannot be used"); the jobs stay candidates.</li>
	 * </ul>
	 * So a batch costs one or two calls, and at most five (the batch, then two halves of two calls each). A job that fails
	 * alone is blamed only as a half whose other half was ranked (the model was answering); a lone job that is a whole batch
	 * is not blamed, since nothing shows that the model works (it is asked again on every run, one call). The split is one
	 * level deep: a half that returns garbage is not narrowed further, so healthy jobs next to a poison pair that spoils
	 * the whole answer stay unranked, at three calls per run, with nobody blamed.
	 */
	public RankReport rank(SearchProfile profile, List<Job> jobs, CallGuard guard) {
		Pass pass = new Pass();
		Outcome total = new Outcome(0, 0, BigDecimal.ZERO, null);
		for (int from = 0; from < jobs.size() && total.error() == null; from += batchSize) {
			total = total.plus(rankBatch(profile, jobs.subList(from, Math.min(from + batchSize, jobs.size())), guard, pass, true));
		}
		return new RankReport(total.ranked(), total.unranked(), total.costUsd(), total.error());
	}

	/** What is collected while one ranking pass goes on. */
	private static final class Pass {

		private final List<Long> loneFailures = new ArrayList<>();
	}

	private record Outcome(int ranked, int unranked, BigDecimal costUsd, String error) {

		Outcome plus(Outcome other) {
			return new Outcome(ranked + other.ranked, unranked + other.unranked, costUsd.add(other.costUsd),
					other.error != null ? other.error : error);
		}
	}

	private Outcome rankBatch(SearchProfile profile, List<Job> batch, CallGuard guard, Pass pass, boolean mayBisect) {
		Answered first = ask(profile, batch, guard);
		if (first.stopped() != null) {
			return new Outcome(0, batch.size(), first.cost(), first.stopped());
		}
		if (first.valid() > 0) {
			return askAgainForTheMissing(profile, batch, first, guard);
		}
		// nothing usable: nobody is blamed
		if (batch.size() == 1) {
			// Asked alone and nothing usable. As a half, the sibling half decides whether it is blamed (see below); as a
			// whole batch there is no evidence that the model works, so nobody is blamed.
			if (!mayBisect) {
				pass.loneFailures.add(batch.getFirst().getId());
			}
			return new Outcome(0, 1, first.cost(), null);
		}
		log.warn("Model answer had nothing usable jobs={} cutOff={}", batch.size(), first.cutOff());
		if (!mayBisect) {
			return new Outcome(0, batch.size(), first.cost(), null);
		}
		int mark = pass.loneFailures.size();
		int middle = batch.size() / 2;
		Outcome firstHalf = rankBatch(profile, batch.subList(0, middle), guard, pass, false);
		Outcome secondHalf = firstHalf.error() != null ? new Outcome(0, batch.size() - middle, BigDecimal.ZERO, null)
				: rankBatch(profile, batch.subList(middle, batch.size()), guard, pass, false);
		Outcome halves = firstHalf.plus(secondHalf);
		boolean modelDown = halves.error() == null && halves.ranked() == 0;
		// A job that failed alone in a half is blamed only if the other half was ranked: then the model was answering.
		List<Long> lone = new ArrayList<>(pass.loneFailures.subList(mark, pass.loneFailures.size()));
		pass.loneFailures.subList(mark, pass.loneFailures.size()).clear();
		if (halves.ranked() > 0 && !lone.isEmpty()) {
			failures.record(profile.getId(), promptHash(profile), lone);
		}
		return new Outcome(halves.ranked(), halves.unranked(), first.cost().add(halves.costUsd()),
				halves.error() != null ? halves.error() : modelDown ? MODEL_DOWN : null);
	}

	/** The answer had usable entries: ask once more about the jobs it left out; the ones still missing failed on their own. */
	private Outcome askAgainForTheMissing(SearchProfile profile, List<Job> batch, Answered first, CallGuard guard) {
		List<Job> missing = batch.stream().filter(job -> first.missing().contains(job.getId())).toList();
		if (missing.isEmpty()) {
			return new Outcome(first.valid(), 0, first.cost(), null);
		}
		Answered second = ask(profile, missing, guard);
		BigDecimal cost = first.cost().add(second.cost());
		if (second.stopped() != null) {
			return new Outcome(first.valid(), missing.size(), cost, second.stopped());
		}
		List<Job> stillMissing = missing.stream().filter(job -> second.missing().contains(job.getId())).toList();
		if (!stillMissing.isEmpty()) {
			failures.record(profile.getId(), promptHash(profile), stillMissing.stream().map(Job::getId).toList());
			log.warn("Jobs the model left out twice count={}", stillMissing.size());
		}
		return new Outcome(first.valid() + second.valid(), stillMissing.size(), cost, null);
	}

	/**
	 * One call about {@code jobs}: reserve it, make it, book its cost, store the usable entries.
	 *
	 * @param stopped why the pass must stop (a refusal or a failed call), or null
	 */
	private record Answered(int valid, Set<Long> missing, BigDecimal cost, boolean cutOff, String stopped) {
	}

	private Answered ask(SearchProfile profile, List<Job> jobs, CallGuard guard) {
		CallGuard.Ticket ticket = guard.beforeCall();
		if (!ticket.granted()) {
			return new Answered(0, idsOf(jobs), BigDecimal.ZERO, false, refusalMessage(ticket.refusal()));
		}
		LlmClient.Completion completion = llm.complete(prompt.text(), userPrompt(profile, jobs), maxTokens(jobs.size()));
		guard.afterCall(ticket, completion.costUsd(), completion.costKnown());
		if (completion.error() != null) {
			return new Answered(0, idsOf(jobs), completion.costUsd(), false, completion.error());
		}
		RankAnswerParser.Parsed answer = completion.cutOff()
				? new RankAnswerParser.Parsed(List.of(), idsOf(jobs))
				: RankAnswerParser.parse(json, completion.content(), idsOf(jobs));
		store(profile, jobs, answer.valid());
		if (!answer.missing().isEmpty()) {
			log.warn("Model answer left jobs out usable={} missing={} cutOff={}", answer.valid().size(), answer.missing().size(), completion.cutOff());
		}
		return new Answered(answer.valid().size(), answer.missing(), completion.costUsd(), completion.cutOff(), null);
	}

	private static String refusalMessage(String refusal) {
		return "model budget".equals(refusal) ? "The monthly model budget is used up" : refusal;
	}

	/** The answer is one short entry per job; this bounds what a call can cost whatever the model decides to write. */
	private static int maxTokens(int jobs) {
		return TOKENS_PER_JOB * jobs + ANSWER_OVERHEAD_TOKENS;
	}

	private static Set<Long> idsOf(List<Job> jobs) {
		return jobs.stream().map(Job::getId).collect(Collectors.toSet());
	}

	private void store(SearchProfile profile, List<Job> batch, List<RankedJob> answer) {
		Map<Long, Job> jobsById = batch.stream().collect(Collectors.toMap(Job::getId, Function.identity()));
		Map<Long, Match> existing = new HashMap<>();
		matches.findForJobs(profile.getId(), jobsById.keySet()).forEach(match -> existing.put(match.getJob().getId(), match));
		String promptHash = promptHash(profile);
		List<Match> changed = answer.stream().map(item -> {
			Match match = existing.getOrDefault(item.jobId(), Match.of(profile.getId(), jobsById.get(item.jobId())));
			match.rank(item.score(), item.reason(), promptHash);
			return match;
		}).toList();
		matches.saveAll(changed);
	}

	private static String userPrompt(SearchProfile profile, List<Job> batch) {
		StringBuilder text = new StringBuilder(Fence.wrap("<expectations>", "</expectations>", profile.getExpectations()));
		for (Job job : batch) {
			text.append("\n\n").append(Fence.wrap("<job id=\"" + job.getId() + "\">", "</job>", describe(job)));
		}
		return text.toString();
	}

	private static String describe(Job job) {
		String salary = job.salaryText();
		return """
				Title: %s
				Company: %s
				Location: %s
				Remote: %s
				Contract: %s
				Salary: %s
				Skills: %s
				Description:
				%s""".formatted(job.getTitle(), job.getCompany(), job.locationText(), job.getRemote(), job.getContractType(),
				salary == null ? "not stated" : salary, String.join(", ", job.getSkills()), job.getDescriptionText());
	}
}
