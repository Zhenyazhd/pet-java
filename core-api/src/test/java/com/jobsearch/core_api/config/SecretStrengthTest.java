package com.jobsearch.core_api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecretStrengthTest {

	@Test
	void shortAndPlaceholderSecretsAreWeak() {
		assertTrue(SecretStrength.isWeak(null));
		assertTrue(SecretStrength.isWeak(""));
		assertTrue(SecretStrength.isWeak("short"));
		assertTrue(SecretStrength.isWeak("job_search"));
		assertTrue(SecretStrength.isWeak("CHANGE-ME"));
		assertTrue(SecretStrength.isWeak("changeme"));
	}

	@Test
	void aPlaceholderWithDigitsAddedOrOneRepeatedCharacterIsStillWeak() {
		assertTrue(SecretStrength.isWeak("changeme1234"));
		assertTrue(SecretStrength.isWeak("Password123456"));
		assertTrue(SecretStrength.isWeak("aaaaaaaaaaaa"));
	}

	@Test
	void aLongRandomLookingSecretIsFine() {
		assertFalse(SecretStrength.isWeak("c0ffee-4f2a9d1b7e3a"));
	}

	@Test
	void theErrorNamesTheVariable() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> SecretStrength.require("LATEX_WORKER_TOKEN", "x"));

		assertTrue(ex.getMessage().startsWith("LATEX_WORKER_TOKEN"));
	}

	@Test
	void strictModeRefusesAWeakDatabasePasswordOrWorkerToken() {
		assertThrows(IllegalStateException.class, () -> check(env(true, "job_search", "c0ffee-4f2a9d1b7e3a")));
		assertThrows(IllegalStateException.class, () -> check(env(true, "c0ffee-4f2a9d1b7e3a", "short")));
		assertThrows(IllegalStateException.class, () -> check(env(true, null, "c0ffee-4f2a9d1b7e3a")));
		assertDoesNotThrow(() -> check(env(true, "c0ffee-4f2a9d1b7e3a", "d1ce0b5a-9a3c-4e")));
	}

	@Test
	void itDoesNothingUnlessStrictModeIsOn() {
		assertDoesNotThrow(() -> check(env(false, "job_search", "short")));
		assertDoesNotThrow(() -> check(new MockEnvironment()));
	}

	private static MockEnvironment env(boolean strict, String dbPassword, String workerToken) {
		MockEnvironment env = new MockEnvironment().withProperty("app.strict-secrets", String.valueOf(strict));
		if (dbPassword != null) {
			env.setProperty("spring.datasource.password", dbPassword);
		}
		env.setProperty("app.latex-worker.token", workerToken);
		return env;
	}

	private static void check(MockEnvironment env) {
		new StrictSecretsPostProcessor().postProcessEnvironment(env, null);
	}
}
