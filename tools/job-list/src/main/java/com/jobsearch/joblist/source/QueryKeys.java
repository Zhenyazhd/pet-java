package com.jobsearch.joblist.source;

import com.jobsearch.joblist.common.Hashes;
import java.util.List;
import java.util.Locale;

/**
 * The key of a search as a source sends it, used to decide that two requests are the same paid call. It is built from
 * what the source puts in its actor input (not from the whole profile) and ignores case and extra spaces, the window and
 * the item limit.
 */
public final class QueryKeys {

	private QueryKeys() {
	}

	/** For a source that sends only the first role and the first place, in that order. */
	public static String ofFirst(SourceName source, List<String> roles, List<String> places) {
		return Hashes.sha256(source.name(), first(roles), first(places));
	}

	/** For a source that sends every role and place; their order does not change the answer. */
	public static String ofAll(SourceName source, List<String> roles, List<String> places) {
		return Hashes.sha256(source.name(), sorted(roles), sorted(places));
	}

	public static String normalised(String text) {
		return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private static String first(List<String> values) {
		return values.isEmpty() ? "" : normalised(values.getFirst());
	}

	private static String sorted(List<String> values) {
		return String.join("\0", values.stream().map(QueryKeys::normalised).filter(value -> !value.isEmpty())
				.distinct().sorted().toList());
	}
}
