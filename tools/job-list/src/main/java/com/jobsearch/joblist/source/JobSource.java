package com.jobsearch.joblist.source;

/** One job board, reached through an Apify actor. */
public interface JobSource {

	SourceName name();

	/** False when the source cannot answer this query at all (for example a French-only board and no French region). */
	boolean supports(SourceQuery query);

	/**
	 * Identifies the paid call this query would make (see {@link QueryKeys}); two queries with the same key are the
	 * same call, so the second is not paid for within the minimum interval.
	 */
	String queryKey(SourceQuery query);

	/** Blocks until the actor has finished; failures come back as a {@link SourceResult}, not as exceptions. */
	SourceResult fetch(SourceQuery query);
}
