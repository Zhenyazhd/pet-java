package com.jobsearch.joblist.run;

import com.jobsearch.joblist.config.JobListProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

/** What has been spent on Apify this month (UTC calendar month) against the monthly budget, from the {@link SpendLedger}. */
@Service
public class UsageService {

	private final SpendLedger ledger;
	private final JobListProperties properties;

	public UsageService(SpendLedger ledger, JobListProperties properties) {
		this.ledger = ledger;
		this.properties = properties;
	}

	/**
	 * @param llmSpentUsd model calls this month (settled costs plus reservations), against {@code llmBudgetUsd}; of it
	 *                    {@code llmReservedUsd} is for calls in flight and {@code llmLostUsd} is counted at the worst case
	 *                    because the call's cost was never reported (every call lands here when the provider omits usage.cost)
	 * @param maxPostingAgeDays the limit on the age of a vacancy ({@code job-list.max-posting-age-days}): older postings are never fetched, ranked or returned
	 * @param spentUsd    everything counted against the Apify budget: settled costs plus what is reserved or lost at the cap
	 * @param reservedUsd the part of it reserved for calls that are running now
	 * @param lostUsd     the part of it counted at the cap for calls whose end was never recorded
	 */
	public record Usage(String month, BigDecimal spentUsd, BigDecimal budgetUsd, List<SourceSpend> bySource,
			BigDecimal reservedUsd, BigDecimal lostUsd, BigDecimal llmSpentUsd, BigDecimal llmBudgetUsd,
			BigDecimal llmReservedUsd, BigDecimal llmLostUsd, int maxPostingAgeDays) {
	}

	public Usage current() {
		return new Usage(
				LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7),
				ledger.spentThisMonth(),
				properties.apify().monthlyBudgetUsd(),
				ledger.spentThisMonthBySource(),
				ledger.reservedThisMonth(),
				ledger.lostThisMonth(),
				ledger.llmSpentThisMonth(),
				properties.llm().monthlyBudgetUsd(),
				ledger.llmReservedThisMonth(),
				ledger.llmLostThisMonth(),
				properties.maxPostingAgeDays());
	}
}
