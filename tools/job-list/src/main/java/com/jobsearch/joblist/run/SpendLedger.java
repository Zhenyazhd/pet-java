package com.jobsearch.joblist.run;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The record of what was spent on Apify, and the only place that decides whether a paid call may start.
 *
 * <p>A call first <em>reserves</em> its worst case (the per-source cap) and only then goes to Apify; when it ends the
 * reservation is settled with the real cost. The checks (this run is still ours, the same search was not fetched
 * recently, the monthly budget has room) and the reservation happen in one short transaction under a transaction-level
 * advisory lock, so two runs, in this process or another, cannot both pass the same check. Nothing slow happens while
 * the lock is held: the call to Apify comes after the transaction has ended.
 */
@Service
public class SpendLedger {

	/** One number for the whole service: the advisory lock that serialises reservations. */
	private static final long RESERVATION_LOCK = 7_140_001L;

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;
	private final JobListProperties properties;

	public SpendLedger(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, JobListProperties properties) {
		this.jdbc = jdbc;
		this.transaction = new TransactionTemplate(transactionManager);
		this.properties = properties;
	}

	/** Why a reservation was not made, or the id of the one that was. */
	public record Reservation(Long id, String refusal, boolean runReleased) {

		public boolean granted() {
			return id != null;
		}
	}

	/**
	 * @param runId the run asking; when it is no longer RUNNING (the janitor released it) nothing is reserved
	 */
	public Reservation reserveApify(long runId, long profileId, SourceName source, String queryKey, Duration minInterval) {
		BigDecimal cap = properties.apify().maxCostPerSourceUsd();
		return transaction.execute(status -> {
			// A holder that never lets go must not freeze every worker for ever.
			jdbc.execute("set local lock_timeout = '10s'");
			jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, RESERVATION_LOCK);
			Instant now = Instant.now();
			Integer running = jdbc.queryForObject("select count(*) from run where id = ? and status = 'RUNNING'", Integer.class, runId);
			if (running == null || running == 0) {
				return new Reservation(null, "run was released", true);
			}
			if (fetchedRecently(source, queryKey, now.minus(minInterval))) {
				return new Reservation(null, "fetched less than " + minInterval.toHours() + " hours ago", false);
			}
			BigDecimal spent = spent("APIFY", monthStart());
			if (spent.add(cap).compareTo(properties.apify().monthlyBudgetUsd()) > 0) {
				return new Reservation(null, "monthly budget", false);
			}
			Long id = jdbc.queryForObject("""
					insert into spend_ledger (kind, source, query_key, profile_id, run_id, status, cost_usd, created_at)
					values ('APIFY', ?, ?, ?, ?, 'RESERVED', ?, ?) returning id
					""", Long.class, source.name(), queryKey, profileId, runId, cap, Timestamp.from(now));
			return new Reservation(id, null, false);
		});
	}

	/**
	 * Reserves one model call at {@code llm.max-cost-per-call-usd} before it is made. Refused when the run was released
	 * or when the month's model spending plus this reservation would pass {@code llm.monthly-budget-usd}.
	 */
	public Reservation reserveLlmCall(long runId, long profileId) {
		BigDecimal cap = properties.llm().maxCostPerCallUsd();
		return transaction.execute(status -> {
			jdbc.execute("set local lock_timeout = '10s'");
			jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, RESERVATION_LOCK);
			Integer running = jdbc.queryForObject("select count(*) from run where id = ? and status = 'RUNNING'", Integer.class, runId);
			if (running == null || running == 0) {
				return new Reservation(null, "run was released", true);
			}
			if (spent("LLM", monthStart()).add(cap).compareTo(properties.llm().monthlyBudgetUsd()) > 0) {
				return new Reservation(null, "model budget", false);
			}
			Long id = jdbc.queryForObject("""
					insert into spend_ledger (kind, profile_id, run_id, status, cost_usd, created_at)
					values ('LLM', ?, ?, 'RESERVED', ?, now()) returning id
					""", Long.class, profileId, runId, cap);
			return new Reservation(id, null, false);
		});
	}

	/** Replaces a model call's reservation with what OpenRouter reported. */
	public void settleLlm(long reservationId, BigDecimal costUsd) {
		jdbc.update("update spend_ledger set status = 'SUCCEEDED', cost_usd = ?, settled_at = now() where id = ? and status = 'RESERVED'",
				costUsd, reservationId);
	}

	/** The call's cost is not known (no cost reported, or it may have been billed without an answer): the reserved worst case stays. */
	public void settleLlmUnknown(long reservationId) {
		jdbc.update("update spend_ledger set status = 'LOST', settled_at = now() where id = ? and status = 'RESERVED'", reservationId);
	}

	/** True when one more model call still fits in the month's model budget. */
	public boolean llmHasRoom() {
		return spent("LLM", monthStart()).add(properties.llm().maxCostPerCallUsd()).compareTo(properties.llm().monthlyBudgetUsd()) <= 0;
	}

	public BigDecimal llmSpentThisMonth() {
		return spent("LLM", monthStart());
	}

	/** Replaces the reservation with what the call really cost. */
	public void settle(long reservationId, boolean succeeded, BigDecimal costUsd, String apifyRunId) {
		jdbc.update("update spend_ledger set status = ?, cost_usd = ?, apify_run_id = ?, settled_at = now() where id = ? and status = 'RESERVED'",
				succeeded ? "SUCCEEDED" : "FAILED", costUsd, apifyRunId, reservationId);
	}

	/** The run died with calls in flight: their cost stays counted at the reserved worst case. */
	public void markLost(long runId) {
		jdbc.update("update spend_ledger set status = 'LOST', settled_at = now() where run_id = ? and status = 'RESERVED'", runId);
	}

	/**
	 * Closes reservations nobody will settle: their run is no longer RUNNING (it ended, was released, or the settle
	 * itself failed). They stay counted at the cap.
	 */
	public int closeOrphanedReservations() {
		return jdbc.update("""
				update spend_ledger set status = 'LOST', settled_at = now()
				where status = 'RESERVED' and not exists (
				  select 1 from run where run.id = spend_ledger.run_id and run.status = 'RUNNING')
				""");
	}

	public BigDecimal reservedThisMonth() {
		return spentWithStatus("APIFY", "RESERVED");
	}

	public BigDecimal lostThisMonth() {
		return spentWithStatus("APIFY", "LOST");
	}

	public BigDecimal llmReservedThisMonth() {
		return spentWithStatus("LLM", "RESERVED");
	}

	public BigDecimal llmLostThisMonth() {
		return spentWithStatus("LLM", "LOST");
	}

	private BigDecimal spentWithStatus(String kind, String status) {
		BigDecimal total = jdbc.queryForObject("select coalesce(sum(cost_usd), 0) from spend_ledger where kind = ? and status = ? and created_at >= ?",
				BigDecimal.class, kind, status, Timestamp.from(monthStart()));
		return total == null ? BigDecimal.ZERO : total;
	}

	/** When this search last worked, for any profile; the next fetch only needs what was posted since. */
	public Instant lastSuccess(SourceName source, String queryKey) {
		Timestamp last = jdbc.queryForObject("""
				select max(created_at) from spend_ledger
				where kind = 'APIFY' and source = ? and query_key = ? and status = 'SUCCEEDED'
				""", Timestamp.class, source.name(), queryKey);
		return last == null ? null : last.toInstant();
	}

	public BigDecimal spentThisMonth() {
		return spent("APIFY", monthStart());
	}

	public List<SourceSpend> spentThisMonthBySource() {
		return jdbc.query("""
				select source, sum(cost_usd) as spent from spend_ledger
				where kind = 'APIFY' and created_at >= ? group by source order by source
				""", (rs, row) -> new SourceSpend(SourceName.valueOf(rs.getString("source")), rs.getBigDecimal("spent")),
				Timestamp.from(monthStart()));
	}

	public int deleteBefore(Instant before) {
		return jdbc.update("delete from spend_ledger where created_at < ?", Timestamp.from(before));
	}

	/**
	 * A fetch that counts against the wait: one still running or lost (its cost is unknown), one that worked, or one that
	 * cost money. A call Apify refused to start settles at zero and does not block a retry.
	 */
	private boolean fetchedRecently(SourceName source, String queryKey, Instant since) {
		Integer count = jdbc.queryForObject("""
				select count(*) from spend_ledger
				where kind = 'APIFY' and source = ? and query_key = ? and created_at > ?
				  and (status in ('RESERVED', 'LOST', 'SUCCEEDED') or cost_usd > 0)
				""", Integer.class, source.name(), queryKey, Timestamp.from(since));
		return count != null && count > 0;
	}

	private BigDecimal spent(String kind, Instant since) {
		BigDecimal total = jdbc.queryForObject("select coalesce(sum(cost_usd), 0) from spend_ledger where kind = ? and created_at >= ?",
				BigDecimal.class, kind, Timestamp.from(since));
		return total == null ? BigDecimal.ZERO : total;
	}

	static Instant monthStart() {
		return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
	}
}
