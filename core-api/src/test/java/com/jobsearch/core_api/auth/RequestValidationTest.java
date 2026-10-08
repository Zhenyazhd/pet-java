package com.jobsearch.core_api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.auth.AuthDtos.RegisterRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyImportRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void aPasswordIsLimitedInBytesNotCharacters() {
		assertEquals(0, validator.validate(register("a".repeat(72))).size());
		assertEquals(1, validator.validate(register("a".repeat(73))).size());
		// Cyrillic letters take two bytes each: 36 fit, 40 (80 bytes) pass @Size(200) but not bcrypt.
		assertEquals(0, validator.validate(register("я".repeat(36))).size());
		assertEquals(1, validator.validate(register("я".repeat(40))).size());
	}

	@Test
	void theBytesMessageSaysWhatToDo() {
		String message = validator.validate(register("я".repeat(40))).iterator().next().getMessage();

		assertEquals("is too long: at most 72 bytes (about 72 Latin letters, fewer for other alphabets)", message);
	}

	@Test
	void onlyHttpLinksAreAcceptedAsAVacancyUrl() {
		assertTrue(validator.validate(vacancy("https://example.com/jobs/1")).isEmpty());
		assertTrue(validator.validate(vacancy("HTTP://EXAMPLE.COM")).isEmpty());
		assertEquals(1, validator.validate(vacancy("javascript:alert(1)")).size());
		assertEquals(1, validator.validate(vacancy("data:text/html,<script>")).size());
		assertEquals(1, validator.validate(vacancy("ftp://example.com/x")).size());
		assertEquals(1, validator.validate(vacancy("example.com/job")).size());
		assertEquals(1, validator.validate(vacancy("https:// ")).size());
		assertEquals(1, validator.validate(vacancy("https://example.com\n<script>")).size());
	}

	@Test
	void theImportRequestHasTheSameUrlRule() {
		assertTrue(validator.validate(new VacancyImportRequest("https://example.com/jobs/1", "text")).isEmpty());
		assertEquals(1, validator.validate(new VacancyImportRequest("javascript:alert(1)", "text")).size());
	}

	private static RegisterRequest register(String password) {
		return new RegisterRequest("ada@example.com", password, "Ada", "invite");
	}

	private static VacancyRequest vacancy(String url) {
		return new VacancyRequest(url, "Engineer", "Acme", "desc", null, List.of());
	}
}
