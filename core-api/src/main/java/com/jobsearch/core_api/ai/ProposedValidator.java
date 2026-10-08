package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.resume.ResumeDtos.AchievementItem;
import com.jobsearch.core_api.resume.ResumeDtos.EducationItem;
import com.jobsearch.core_api.resume.ResumeDtos.ExperienceItem;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeDtos.SkillItem;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks what the model offers as an edit against the same rules a saved resume must meet, so the page never
 * receives an edit it cannot apply or that the save would refuse. The model only proposes; nothing here stores it.
 * <p>
 * A field the model leaves out or sets to null counts as empty (the prompt tells it to leave unknown facts empty),
 * but a name or a title must still be there. The result is always the checked value in its plain shape, never the
 * model's own JSON, or its extra keys and loosely typed values would travel on to the page. {@code null} means
 * the edit is unusable.
 */
@Component
class ProposedValidator {

	private static final Logger log = LoggerFactory.getLogger(ProposedValidator.class);

	private static final Map<String, Integer> HEADER_FIELDS = Map.of(
			"name", 200, "headline", 300, "phone", 100, "email", 320, "linkedinUrl", 500, "linkedinLabel", 200);
	private static final List<String> OPTIONAL_DOCUMENT_TEXT = List.of(
			"headline", "phone", "email", "linkedinUrl", "linkedinLabel", "profile");
	private static final int PROFILE_MAX = 5_000;
	private static final Map<String, Integer> LIST_MAX = Map.of(
			"experience", 50, "education", 30, "achievements", 50, "skills", 50);
	private static final Map<String, Class<?>> ITEM_TYPES = Map.of(
			"experience", ExperienceItem.class,
			"education", EducationItem.class,
			"achievements", AchievementItem.class,
			"skills", SkillItem.class);
	private static final Map<String, List<String>> OPTIONAL_ITEM_TEXT = Map.of(
			"experience", List.of("subtitle", "dates"),
			"education", List.of("subtitle", "location", "details"),
			"achievements", List.of("text"),
			"skills", List.of("items"));

	private final ObjectMapper objectMapper;
	private final Validator validator;

	ProposedValidator(ObjectMapper objectMapper, Validator validator) {
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	/** @param itemIndex set when the question was about one entry of a list section */
	JsonNode check(String section, Integer itemIndex, JsonNode proposed) {
		try {
			JsonNode checked = switch (section) {
				case "all" -> whole(proposed);
				case "header" -> header(proposed);
				case "profile" -> profile(proposed);
				default -> list(section, itemIndex, proposed);
			};
			if (checked == null) {
				log.warn("AI proposal rejected section={} itemIndex={} shape={}", section, itemIndex, proposed.getNodeType());
			}
			return checked;
		}
		catch (JacksonException ex) {
			log.warn("AI proposal rejected section={} itemIndex={}: {}", section, itemIndex, ex.getOriginalMessage());
			return null;
		}
	}

	private JsonNode whole(JsonNode proposed) {
		if (!proposed.isObject()) {
			return null;
		}
		ObjectNode copy = (ObjectNode) proposed.deepCopy();
		fillText(copy, OPTIONAL_DOCUMENT_TEXT);
		for (Map.Entry<String, Class<?>> list : ITEM_TYPES.entrySet()) {
			JsonNode entries = copy.get(list.getKey());
			if (entries == null || entries.isNull()) {
				copy.set(list.getKey(), objectMapper.createArrayNode());
			}
			else if (entries.isArray()) {
				entries.forEach(entry -> fillItem(list.getKey(), entry));
			}
		}
		ResumeDocument document = objectMapper.treeToValue(copy, ResumeDocument.class);
		if (document == null || !validator.validate(document).isEmpty()) {
			return null;
		}
		ResumeDocument plain = new ResumeDocument(document.name(), document.headline(), document.phone(), document.email(),
				document.linkedinUrl(), document.linkedinLabel(), document.profile(), document.experience(),
				document.education(), document.achievements(), document.skills(), null, null);
		return objectMapper.valueToTree(plain);
	}

	private JsonNode header(JsonNode proposed) {
		if (!proposed.isObject()) {
			return null;
		}
		ObjectNode checked = objectMapper.createObjectNode();
		for (Map.Entry<String, JsonNode> field : proposed.properties()) {
			JsonNode value = field.getValue();
			if (value.isNull()) {
				continue;
			}
			Integer max = HEADER_FIELDS.get(field.getKey());
			if (max == null || !value.isString() || value.asString().length() > max) {
				return null;
			}
			checked.set(field.getKey(), value);
		}
		if (checked.isEmpty() || (checked.has("name") && checked.get("name").asString().isBlank())) {
			return null;
		}
		return checked;
	}

	private JsonNode profile(JsonNode proposed) {
		String text = text(proposed);
		return text != null && !text.isBlank() && text.length() <= PROFILE_MAX ? objectMapper.valueToTree(text) : null;
	}

	private static String text(JsonNode node) {
		if (node.isString()) {
			return node.asString();
		}
		if (node.isArray() && node.size() == 1) {
			return text(node.get(0));
		}
		if (node.isObject() && node.has("profile") && node.get("profile").isString()) {
			return node.get("profile").asString();
		}
		if (node.isObject() && node.size() == 1) {
			return text(node.properties().iterator().next().getValue());
		}
		return null;
	}

	private JsonNode list(String section, Integer itemIndex, JsonNode proposed) {
		if (!ITEM_TYPES.containsKey(section)) {
			return null;
		}
		if (itemIndex != null) {
			JsonNode entry = proposed.isArray() && proposed.size() == 1 ? proposed.get(0) : proposed;
			return entry.isObject() ? item(section, entry) : null;
		}
		if (!proposed.isArray() || proposed.size() > LIST_MAX.get(section)) {
			return null;
		}
		ArrayNode checked = objectMapper.createArrayNode();
		for (JsonNode entry : proposed) {
			JsonNode item = entry.isObject() ? item(section, entry) : null;
			if (item == null) {
				return null;
			}
			checked.add(item);
		}
		return checked;
	}

	private JsonNode item(String section, JsonNode entry) {
		ObjectNode copy = (ObjectNode) entry.deepCopy();
		fillItem(section, copy);
		Object item = objectMapper.treeToValue(copy, ITEM_TYPES.get(section));
		return item != null && validator.validate(item).isEmpty() ? objectMapper.valueToTree(item) : null;
	}

	/** Leaves out-or-null text fields and an absent bullet list empty. Anything not an object is left for the type check to refuse. */
	private void fillItem(String section, JsonNode entry) {
		if (!entry.isObject()) {
			return;
		}
		ObjectNode node = (ObjectNode) entry;
		fillText(node, OPTIONAL_ITEM_TEXT.get(section));
		if ("experience".equals(section)) {
			JsonNode bullets = node.get("bullets");
			if (bullets == null || bullets.isNull()) {
				node.set("bullets", objectMapper.createArrayNode());
			}
		}
	}

	private static void fillText(ObjectNode node, List<String> names) {
		for (String name : names) {
			JsonNode value = node.get(name);
			if (value == null || value.isNull()) {
				node.put(name, "");
			}
		}
	}
}
