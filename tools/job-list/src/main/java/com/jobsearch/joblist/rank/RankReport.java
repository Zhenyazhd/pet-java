package com.jobsearch.joblist.rank;

import java.math.BigDecimal;

/**
 * What a ranking pass did: how many jobs got a match, how many the model left without a usable answer (they stay
 * candidates), what the model calls cost, and {@code error} when it stopped early.
 */
public record RankReport(int ranked, int unranked, BigDecimal costUsd, String error) {

	public static RankReport none() {
		return new RankReport(0, 0, BigDecimal.ZERO, null);
	}

	/** The message a run shows for a pass that did not rank everything, or null when it did. */
	public String problem() {
		if (error != null) {
			return error;
		}
		return unranked > 0 ? unranked + " jobs could not be ranked (the model gave no usable answer); they stay candidates" : null;
	}
}
