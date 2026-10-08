package com.jobsearch.core_api.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What the model offers is checked against the rules a saved resume must meet before the page sees it. */
class ProposedValidatorTest {

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final ProposedValidator validator =
			new ProposedValidator(mapper, Validation.buildDefaultValidatorFactory().getValidator());

	private JsonNode json(String text) {
		return mapper.readTree(text);
	}

	private static final String JOB = "{\"title\":\"Engineer\",\"subtitle\":\"Acme\",\"dates\":\"2020\",\"bullets\":[\"Built it\"]}";

	@Test
	void aWholeResumeMustBeAValidResume() {
		String valid = "{\"name\":\"Ada\",\"headline\":\"\",\"phone\":\"\",\"email\":\"\",\"linkedinUrl\":\"\","
				+ "\"linkedinLabel\":\"\",\"profile\":\"\",\"experience\":[" + JOB + "],\"education\":[],"
				+ "\"achievements\":[],\"skills\":[],\"locale\":\"en\"}";

		assertNotNull(validator.check("all", null, json(valid)));
		assertNull(validator.check("all", null, json(valid.replace("\"name\":\"Ada\"", "\"name\":\"\""))));
		assertNull(validator.check("all", null, json(valid.replace("\"experience\":[" + JOB + "]", "\"experience\":[null]"))));
		assertNull(validator.check("all", null, json("{\"profile\":\"only this\"}")));
		assertNull(validator.check("all", null, json("\"text\"")));
	}

	@Test
	void aProfileIsOneTextWhateverItIsWrappedIn() {
		assertEquals("New", validator.check("profile", null, json("\"New\"")).asString());
		assertEquals("New", validator.check("profile", null, json("{\"profile\":\"New\"}")).asString());
		assertEquals("New", validator.check("profile", null, json("[\"New\"]")).asString());
		assertEquals("New", validator.check("profile", null, json("{\"summary\":\"New\"}")).asString());
		assertNull(validator.check("profile", null, json("{\"a\":\"x\",\"b\":\"y\"}")));
		assertNull(validator.check("profile", null, json("42")));
		assertNull(validator.check("profile", null, json("\"" + "x".repeat(5_001) + "\"")));
	}

	@Test
	void aHeaderHasOnlyKnownTextFieldsWithinTheirLimits() {
		assertNotNull(validator.check("header", null, json("{\"headline\":\"Staff engineer\"}")));
		assertNull(validator.check("header", null, json("{}")));
		assertNull(validator.check("header", null, json("{\"nickname\":\"Ada\"}")));
		assertNull(validator.check("header", null, json("{\"name\":\"\"}")));
		assertNull(validator.check("header", null, json("{\"phone\":42}")));
		assertNull(validator.check("header", null, json("{\"headline\":\"" + "x".repeat(301) + "\"}")));
	}

	@Test
	void aListAnswerForOneEntryMustBeThatEntry() {
		assertNotNull(validator.check("experience", 1, json(JOB)));
		assertEquals(json(JOB), validator.check("experience", 1, json("[" + JOB + "]")));
		assertNull(validator.check("experience", 1, json("[" + JOB + "," + JOB + "]")));
		assertNull(validator.check("experience", 1, json("{\"title\":\"\"}")));
	}

	@Test
	void aListAnswerForAWholeSectionMustBeAValidList() {
		assertNotNull(validator.check("experience", null, json("[" + JOB + "]")));
		assertNotNull(validator.check("skills", null, json("[{\"category\":\"Ops\",\"items\":\"k8s\"}]")));
		assertNull(validator.check("experience", null, json(JOB)));
		assertNull(validator.check("experience", null, json("[null]")));
		assertNull(validator.check("experience", null, json("[{\"title\":{\"a\":1}}]")));
		assertNull(validator.check("skills", null, json("[{\"category\":\"\",\"items\":\"x\"}]")));
	}

	@Test
	void aListLongerThanTheSaveAllowsIsRefused() {
		String many = "[" + String.join(",", java.util.Collections.nCopies(31, "{\"title\":\"BSc\",\"subtitle\":\"\",\"location\":\"\",\"details\":\"\"}")) + "]";

		assertNull(validator.check("education", null, json(many)));
	}

	@Test
	void fieldsTheModelLeavesOutOrNullsAreEmptyButANameOrTitleIsRequired() {
		String withoutOptionals = "{\"name\":\"Ada\",\"headline\":null,\"experience\":[{\"title\":\"Engineer\"}],\"skills\":null}";

		JsonNode checked = validator.check("all", null, json(withoutOptionals));

		assertNotNull(checked);
		assertEquals("", checked.get("headline").asString());
		assertEquals("", checked.get("email").asString());
		assertEquals("", checked.get("experience").get(0).get("subtitle").asString());
		assertEquals(0, checked.get("experience").get(0).get("bullets").size());
		assertEquals(0, checked.get("skills").size());
		assertNull(validator.check("all", null, json("{\"name\":\"Ada\",\"experience\":[{\"subtitle\":\"no title\"}]}")));
	}

	@Test
	void theVersionAndLanguageOfTheModelsAnswerDoNotTravelOn() {
		JsonNode checked = validator.check("all", null, json("{\"name\":\"Ada\",\"locale\":\"fr\",\"version\":99}"));

		assertNotNull(checked);
		assertTrue(checked.get("version").isNull());
		assertTrue(checked.get("locale").isNull());
	}

	@Test
	void whatComesBackIsTheCheckedValueNotTheModelsOwnJson() {
		JsonNode checked = validator.check("experience", null,
				json("[{\"title\":\"T\",\"subtitle\":\"\",\"dates\":\"\",\"bullets\":[1,true],\"extra\":\"x\"}]"));

		assertNotNull(checked);
		assertTrue(checked.get(0).get("bullets").get(0).isString());
		assertTrue(checked.get(0).get("bullets").get(1).isString());
		assertFalse(checked.get(0).has("extra"));
	}

	@Test
	void aHeaderNullMeansLeaveItAlone() {
		JsonNode checked = validator.check("header", null, json("{\"name\":\"Ada\",\"phone\":null}"));

		assertEquals(json("{\"name\":\"Ada\"}"), checked);
		assertNull(validator.check("header", null, json("{\"phone\":null}")));
	}

	@Test
	void aBlankProfileIsNotAnEdit() {
		assertNull(validator.check("profile", null, json("\"\"")));
		assertNull(validator.check("profile", null, json("\"   \"")));
	}
}
