package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.ai.PromptPart;
import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.profile.PromptDtos.PromptPartResponse;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The authenticated user's own wording of the editable prompt parts; every call touches only that user's rows. */
@Service
@Transactional
public class UserPromptService {

	/** Counted in UTF-16 characters (Java's and JavaScript's string length), on the stripped text. */
	static final int MAX_CHARS = 6000;
	/** All of one user's customised parts together, so the prompt cannot be made arbitrarily large. */
	static final int MAX_TOTAL_CHARS = 12_000;
	/** Control characters other than newline, carriage return and tab, and the Unicode line and paragraph separators. */
	private static final Pattern FORBIDDEN = Pattern.compile("[[\\p{Cc}\\u2028\\u2029]&&[^\\n\\r\\t]]");

	private static final Logger log = LoggerFactory.getLogger(UserPromptService.class);

	private final CurrentUserService currentUserService;
	private final UserPromptPartRepository repository;

	public UserPromptService(CurrentUserService currentUserService, UserPromptPartRepository repository) {
		this.currentUserService = currentUserService;
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<PromptPartResponse> list() {
		Map<PromptPart, UserPromptPart> rows = rows(currentUserService.requireUserId());
		return Arrays.stream(PromptPart.values()).map(p -> toResponse(p, rows.get(p))).toList();
	}

	/** The current user's customised parts only (one query); a part missing from the map uses its default. */
	@Transactional(readOnly = true)
	public Map<PromptPart, String> customTexts() {
		Map<PromptPart, String> result = new EnumMap<>(PromptPart.class);
		rows(currentUserService.requireUserId()).forEach((part, row) -> result.put(part, row.getContent()));
		return result;
	}

	public PromptPartResponse save(String key, String text) {
		PromptPart part = requirePart(key);
		long userId = currentUserService.requireUserId();
		String clean = text.replace("\r\n", "\n").strip();
		if (clean.isEmpty()) {
			throw new BadRequestException("The text must not be blank. Use reset to go back to the default.");
		}
		if (clean.length() > MAX_CHARS) {
			throw new BadRequestException("The text is too long: at most " + MAX_CHARS + " UTF-16 characters.");
		}
		if (FORBIDDEN.matcher(clean).find() || hasUnpairedSurrogate(clean)) {
			throw new BadRequestException("The text contains characters that are not allowed "
					+ "(control characters, line or paragraph separators, or broken Unicode).");
		}
		if (clean.equals(part.defaultText())) {
			repository.deleteByUserIdAndPartKey(userId, part.key());
			log.info("Prompt part {} back to default for userId={}", part.key(), userId);
			return toResponse(part, null);
		}
		int others = rows(userId).entrySet().stream()
				.filter(e -> e.getKey() != part)
				.mapToInt(e -> e.getValue().getContent().length())
				.sum();
		if (others + clean.length() > MAX_TOTAL_CHARS) {
			throw new BadRequestException("Your customised parts together may have at most " + MAX_TOTAL_CHARS
					+ " UTF-16 characters; the other parts already use " + others + ". Shorten this text or reset another part.");
		}
		repository.upsert(userId, part.key(), clean, part.defaultHash());
		log.info("Saved prompt part {} for userId={} chars={}", part.key(), userId, clean.length());
		return new PromptPartResponse(part.key(), part.title(), part.description(), part.defaultText(), clean, true, false);
	}

	/** Idempotent: resetting a part that is not customised changes nothing. */
	public void reset(String key) {
		PromptPart part = requirePart(key);
		repository.deleteByUserIdAndPartKey(currentUserService.requireUserId(), part.key());
	}

	/** One query; rows whose key is no longer a {@link PromptPart} are ignored. */
	private Map<PromptPart, UserPromptPart> rows(long userId) {
		Map<PromptPart, UserPromptPart> result = new EnumMap<>(PromptPart.class);
		for (UserPromptPart row : repository.findAllByUserId(userId)) {
			PromptPart.byKey(row.getId().partKey()).ifPresent(part -> result.put(part, row));
		}
		return result;
	}

	private static PromptPart requirePart(String key) {
		return PromptPart.byKey(key).orElseThrow(() -> new NotFoundException("Unknown prompt part"));
	}

	private static boolean hasUnpairedSurrogate(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
				i++;
			} else if (Character.isSurrogate(c)) {
				return true;
			}
		}
		return false;
	}

	private static PromptPartResponse toResponse(PromptPart part, UserPromptPart row) {
		String custom = row == null ? null : row.getContent();
		boolean changed = row != null && !row.getBaseHash().equals(part.defaultHash());
		return new PromptPartResponse(
				part.key(), part.title(), part.description(), part.defaultText(), custom, row != null, changed);
	}
}
