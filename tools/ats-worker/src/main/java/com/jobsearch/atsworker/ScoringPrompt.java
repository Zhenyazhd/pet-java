package com.jobsearch.atsworker;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The prompt that makes a model score a resume on six ATS platforms at once. The text is taken from ATS Screener
 * (MIT, Copyright (c) 2026 Sunny Patel), {@code src/lib/engine/llm/prompts.ts}; see THIRD_PARTY_NOTICES.md.
 * The template is {@code prompts/full-score.md}; the wording that differs between scoring against a vacancy and
 * scoring the resume alone is in {@code full-score.targeted.properties} and {@code full-score.general.properties}.
 */
final class ScoringPrompt {

	static final int RESUME_CHARS = 6000;
	static final int JOB_DESCRIPTION_CHARS = 4000;

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");
	private static final Pattern USER_DATA = Pattern.compile("\\{\\{(RESUME|JOB_DESCRIPTION)}}");
	private static final String TEMPLATE = resource("full-score.md");
	private static final Properties TARGETED = properties("full-score.targeted.properties");
	private static final Properties GENERAL = properties("full-score.general.properties");

	private ScoringPrompt() {
	}

	/** @param jobDescription the vacancy text, or blank to score the resume on its own */
	static String build(String resume, String jobDescription) {
		String resumeSlice = slice(resume, RESUME_CHARS);
		String jobSlice = jobDescription == null ? "" : slice(jobDescription, JOB_DESCRIPTION_CHARS);
		Properties wording = jobSlice.isBlank() ? GENERAL : TARGETED;
		// The wording is ours; the resume and vacancy are user text and are inserted last, in one pass, so a
		// "{{...}}" inside them is never expanded.
		String withWording = replace(PLACEHOLDER, TEMPLATE, key -> wording.getProperty(key));
		return replace(USER_DATA, withWording, key -> key.equals("RESUME") ? resumeSlice : jobSlice);
	}

	private static String slice(String text, int max) {
		String stripped = text.strip();
		return stripped.length() > max ? stripped.substring(0, max) : stripped;
	}

	private static String replace(Pattern pattern, String text, java.util.function.Function<String, String> values) {
		Matcher matcher = pattern.matcher(text);
		StringBuilder out = new StringBuilder(text.length() + 8192);
		while (matcher.find()) {
			String value = values.apply(matcher.group(1));
			matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String resource(String name) {
		try (InputStream in = ScoringPrompt.class.getResourceAsStream("/prompts/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static Properties properties(String name) {
		Properties properties = new Properties();
		try (InputStream in = ScoringPrompt.class.getResourceAsStream("/prompts/" + name)) {
			properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			return properties;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
