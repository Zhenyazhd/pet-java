package com.jobsearch.core_api.common;

import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Maps unique-constraint races to {@link ConflictException} in one place.
 * Prefer an optimistic exists-check first, then wrap the write with {@link #onConflict}.
 * Only violations whose message mentions the given constraint name are mapped;
 * other integrity errors are rethrown unchanged (so callers do not mislabel them).
 */
public final class UniqueConstraint {

	public static final String APP_USER_EMAIL_LOWER = "uq_app_user_email_lower";
	public static final String VACANCY_USER_URL = "uq_vacancy_user_url";

	private UniqueConstraint() {
	}

	public static <T> T onConflict(String conflictMessage, Supplier<T> write, String constraintName) {
		try {
			return write.get();
		}
		catch (DataIntegrityViolationException ex) {
			if (!exceptionText(ex).toLowerCase(Locale.ROOT).contains(constraintName.toLowerCase(Locale.ROOT))) {
				throw ex;
			}
			throw new ConflictException(conflictMessage, ex);
		}
	}

	private static String exceptionText(Throwable ex) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t.getMessage() != null) {
				sb.append(t.getMessage()).append('\n');
			}
		}
		return sb.toString();
	}
}
