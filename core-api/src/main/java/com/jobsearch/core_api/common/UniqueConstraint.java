package com.jobsearch.core_api.common;

import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Maps unique-constraint races to {@link ConflictException} in one place.
 * Prefer an optimistic exists-check first, then wrap the write with {@link #onConflict}.
 * Only violations whose message mentions one of the given constraint names are mapped;
 * other integrity errors are rethrown unchanged (so callers do not mislabel them).
 */
public final class UniqueConstraint {

	public static final String APP_USER_EMAIL_LOWER = "uq_app_user_email_lower";
	public static final String VACANCY_USER_URL = "uq_vacancy_user_url";

	private UniqueConstraint() {
	}

	public static <T> T onConflict(
			String conflictMessage,
			Supplier<T> write,
			String constraintName,
			String... moreConstraintNames
	) {
		try {
			return write.get();
		}
		catch (DataIntegrityViolationException ex) {
			if (!matchesConstraint(ex, constraintName, moreConstraintNames)) {
				throw ex;
			}
			throw new ConflictException(conflictMessage, ex);
		}
	}

	static boolean matchesConstraint(
			DataIntegrityViolationException ex,
			String constraintName,
			String... moreConstraintNames
	) {
		String haystack = exceptionText(ex).toLowerCase(Locale.ROOT);
		if (containsConstraint(haystack, constraintName)) {
			return true;
		}
		if (moreConstraintNames != null) {
			for (String name : moreConstraintNames) {
				if (containsConstraint(haystack, name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean containsConstraint(String haystack, String constraintName) {
		return constraintName != null
				&& !constraintName.isBlank()
				&& haystack.contains(constraintName.toLowerCase(Locale.ROOT));
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
