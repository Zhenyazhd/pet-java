package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns a ResumeDocument into Typst source: one line that binds the resume, as a JSON string literal, to {@code r},
 * followed by the static template {@code typst/resume.typ}. The text of the resume is only ever data inside that
 * string, so nothing a user types can become Typst markup or code; the only escaping needed is the string literal's.
 */
@Component
public class ResumeTypstRenderer {

	private static final String TEMPLATE_PATH = "typst/resume.typ";

	/** Keys whose value is a list, here and in the items; a missing one reads as an empty list, never null. */
	private static final Set<String> LISTS = Set.of("experience", "education", "achievements", "skills", "bullets");

	private final ObjectMapper objectMapper;
	private final String template;

	public ResumeTypstRenderer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.template = loadTemplate();
	}

	public String render(ResumeDocument document) {
		return "#let r = json(bytes(\"" + escape(normalized(document).toString()) + "\"))\n" + template;
	}

	/**
	 * The document as the template wants it: no nulls (strings become "", lists become []), no optimistic-lock
	 * {@code version} (it is not content and would change the PDF cache key on every save), and a locale of
	 * "en" or "fr" (anything else renders as French, as before).
	 */
	JsonNode normalized(ResumeDocument document) {
		ObjectNode root = objectMapper.valueToTree(document);
		root.remove("version");
		root.put("locale", "en".equalsIgnoreCase(root.path("locale").asString("").strip()) ? "en" : "fr");
		fillNulls(root);
		return root;
	}

	/** Null values become "" (or [] for a list key); a null item of a list is dropped, as the template would fail on it. */
	private static void fillNulls(ObjectNode obj) {
		for (String name : obj.propertyNames().stream().toList()) {
			JsonNode value = obj.get(name);
			if (value.isNull()) {
				if (LISTS.contains(name)) {
					obj.putArray(name);
				}
				else {
					obj.put(name, "");
				}
			}
			else if (value instanceof ArrayNode array) {
				for (int i = array.size() - 1; i >= 0; i--) {
					if (array.get(i).isNull()) {
						array.remove(i);
					}
				}
				array.forEach(item -> {
					if (item instanceof ObjectNode nested) {
						fillNulls(nested);
					}
				});
			}
		}
	}

	/** Escapes a value for a Typst string literal: backslash, quote, control characters and the Unicode line separators. */
	static String escape(String value) {
		StringBuilder out = new StringBuilder(value.length() + 32);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> out.append("\\\\");
				case '"' -> out.append("\\\"");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20 || c == 0x7F || c == ' ' || c == ' ') {
						out.append("\\u{").append(Integer.toHexString(c)).append('}');
					}
					else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}

	private static String loadTemplate() {
		try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot load " + TEMPLATE_PATH, ex);
		}
	}
}
