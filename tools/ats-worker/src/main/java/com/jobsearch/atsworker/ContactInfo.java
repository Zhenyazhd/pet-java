package com.jobsearch.atsworker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which contact details a text contains. */
record ContactInfo(boolean email, boolean phone, boolean url) {

	/** The text is user-controlled and can be huge; the patterns below are linear, this just bounds the work. */
	private static final int MAX_CHARS = 200_000;

	// Every repetition is bounded, so a long unbroken run cannot make the scan quadratic.
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]{1,64}+@[\\w-]{1,255}+(?:\\.[\\w-]{1,63}+)++");
	/** Digits, spaces and phone punctuation on one line: two date ranges on separate lines are not a number. */
	private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d \\t().\\-–]{7,25}\\d");
	private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.|linkedin\\.com/|github\\.com/)\\S");
	private static final int MIN_PHONE_DIGITS = 9;

	static ContactInfo of(String text) {
		String bounded = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
		return new ContactInfo(EMAIL.matcher(bounded).find(), hasPhone(bounded), URL.matcher(bounded).find());
	}

	private static boolean hasPhone(String text) {
		Matcher matcher = PHONE.matcher(text);
		while (matcher.find()) {
			if (matcher.group().chars().filter(Character::isDigit).count() >= MIN_PHONE_DIGITS) {
				return true;
			}
		}
		return false;
	}
}
