package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.auth.AppUserPrincipal;
import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.auth.UserRole;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.ai.PromptDtos.PromptPartResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The editable prompt parts, for admins only: an admin's own wording, and the defaults every user gets (the shipped text,
 * or what an admin made the default with {@link #publish()}). Everyone else always gets those defaults.
 */
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
	private final PromptDefaultRepository defaultRepository;

	public UserPromptService(CurrentUserService currentUserService, UserPromptPartRepository repository,
			PromptDefaultRepository defaultRepository) {
		this.currentUserService = currentUserService;
		this.repository = repository;
		this.defaultRepository = defaultRepository;
	}

	@Transactional(readOnly = true)
	public List<PromptPartResponse> list() {
		Map<PromptPart, UserPromptPart> rows = rows(currentUserService.requireUserId());
		Map<PromptPart, String> defaults = publishedDefaults();
		return Arrays.stream(PromptPart.values()).map(p -> toResponse(p, rows.get(p), defaults)).toList();
	}

	/**
	 * What the chat uses for the current user: the published defaults, and over them the user's own parts if the user
	 * is an admin. A part missing from the map uses the shipped text. Someone who is not an admin cannot see or edit
	 * their own parts, so a row left from earlier is not used.
	 */
	@Transactional(readOnly = true)
	public Map<PromptPart, String> customTexts() {
		Map<PromptPart, String> result = publishedDefaults();
		if (isAdmin()) {
			rows(currentUserService.requireUserId()).forEach((part, row) -> result.put(part, row.getContent()));
		}
		return result;
	}

	/**
	 * Makes the admin's saved wording the default for everyone: a part the admin customised becomes the default, a part
	 * they did not goes back to the shipped text. The admin's own rows then equal the defaults, so they are removed.
	 */
	public List<PromptPartResponse> publish() {
		long userId = currentUserService.requireUserId();
		Map<PromptPart, UserPromptPart> mine = rows(userId);
		for (PromptPart part : PromptPart.values()) {
			UserPromptPart row = mine.get(part);
			if (row == null) {
				defaultRepository.deleteById(part.key());
			}
			else {
				defaultRepository.upsert(part.key(), row.getContent());
				repository.deleteByUserIdAndPartKey(userId, part.key());
			}
		}
		log.info("Prompt defaults published by userId={} customisedParts={}", userId, mine.size());
		return list();
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
		if (FORBIDDEN.matcher(clean).find() || !StandardCharsets.UTF_8.newEncoder().canEncode(clean)) {
			throw new BadRequestException("The text contains characters that are not allowed "
					+ "(control characters, line or paragraph separators, or broken Unicode).");
		}
		Map<PromptPart, String> defaults = publishedDefaults();
		String currentDefault = defaults.getOrDefault(part, part.defaultText());
		if (clean.equals(currentDefault)) {
			repository.deleteByUserIdAndPartKey(userId, part.key());
			log.info("Prompt part {} back to default for userId={}", part.key(), userId);
			return toResponse(part, null, defaults);
		}
		int others = rows(userId).entrySet().stream()
				.filter(e -> e.getKey() != part)
				.mapToInt(e -> e.getValue().getContent().length())
				.sum();
		if (others + clean.length() > MAX_TOTAL_CHARS) {
			throw new BadRequestException("Your customised parts together may have at most " + MAX_TOTAL_CHARS
					+ " UTF-16 characters; the other parts already use " + others + ". Shorten this text or reset another part.");
		}
		repository.upsert(userId, part.key(), clean, Hashes.sha256(currentDefault));
		log.info("Saved prompt part {} for userId={} chars={}", part.key(), userId, clean.length());
		return new PromptPartResponse(part.key(), part.title(), part.description(), currentDefault, clean, true, false);
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

	/** The texts admins have made the default; a part not in the map uses the shipped text. */
	private Map<PromptPart, String> publishedDefaults() {
		Map<PromptPart, String> result = new EnumMap<>(PromptPart.class);
		for (PromptDefault row : defaultRepository.findAll()) {
			PromptPart.byKey(row.getPartKey()).ifPresent(part -> result.put(part, row.getContent()));
		}
		return result;
	}

	private static boolean isAdmin() {
		AppUserPrincipal principal = AppUserPrincipal.current();
		return principal != null && principal.getRole() == UserRole.ADMIN;
	}

	private static PromptPartResponse toResponse(PromptPart part, UserPromptPart row, Map<PromptPart, String> defaults) {
		String currentDefault = defaults.getOrDefault(part, part.defaultText());
		String custom = row == null ? null : row.getContent();
		boolean changed = row != null && !row.getBaseHash().equals(Hashes.sha256(currentDefault));
		return new PromptPartResponse(
				part.key(), part.title(), part.description(), currentDefault, custom, row != null, changed);
	}
}
