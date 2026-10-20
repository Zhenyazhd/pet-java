package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.common.Hashes;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * The parts of the resume chat's system prompt a user may rewrite. To add one: a constant here and its default text in
 * {@code src/main/resources/prompts/<key>.txt}; then place it in {@link SystemPromptComposer}. The prompts API and the
 * profile page list whatever is declared here.
 */
public enum PromptPart {

	COMMITMENTS("commitments", "Commitments",
			"How the assistant promises and delivers edits"),
	TRUTHFULNESS("truthfulness", "Truthfulness",
			"What the assistant may and may not claim, and what it does when facts are missing"),
	VACANCY("vacancy", "Vacancy targeting",
			"How small edits are aimed at the target vacancy"),
	TAILORING("tailoring", "Tailoring the whole CV",
			"The two-stage analysis and rewrite when you ask to tailor the CV to a vacancy");

	private final String key;
	private final String title;
	private final String description;
	private final String defaultText;

	PromptPart(String key, String title, String description) {
		this.key = key;
		this.title = title;
		this.description = description;
		this.defaultText = readResource("prompts/" + key + ".txt").strip();
	}

	public String key() {
		return key;
	}

	public String title() {
		return title;
	}

	public String description() {
		return description;
	}

	/** The built-in wording, stripped of surrounding whitespace. */
	public String defaultText() {
		return defaultText;
	}

	/** Hex sha256 of the default text; stored with a customised part to tell later that the default has changed. */
	public String defaultHash() {
		return Hashes.sha256(defaultText);
	}

	public static Optional<PromptPart> byKey(String key) {
		return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
	}

	/**
	 * Reads a UTF-8 classpath text file with LF line endings; a missing one is a packaging bug, and
	 * {@link PromptStartupCheck} makes it fail the start.
	 */
	static String readResource(String path) {
		try (InputStream in = PromptPart.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing prompt resource " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
