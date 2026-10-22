package com.jobsearch.joblist.rank;

import java.util.regex.Pattern;

final class Fence {

	private static final Pattern CLOSING_TAG = Pattern.compile("</\\s*(job|expectations)", Pattern.CASE_INSENSITIVE);

	private Fence() {
	}

	static String wrap(String openTag, String closeTag, String text) {
		return openTag + "\n" + CLOSING_TAG.matcher(text).replaceAll("< /$1") + "\n" + closeTag;
	}
}
