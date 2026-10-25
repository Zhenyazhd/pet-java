package com.jobsearch.core_api.auth.invite;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.UnauthorizedException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteCodeService {

	private static final Logger log = LoggerFactory.getLogger(InviteCodeService.class);
	private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int CODE_LENGTH = 10;

	private final InviteCodeRepository inviteCodeRepository;
	private final CurrentUserService currentUserService;
	private final SecureRandom secureRandom = new SecureRandom();

	public InviteCodeService(InviteCodeRepository inviteCodeRepository, CurrentUserService currentUserService) {
		this.inviteCodeRepository = inviteCodeRepository;
		this.currentUserService = currentUserService;
	}

	@Transactional(readOnly = true)
	public List<InviteCodeDtos.InviteCodeResponse> list() {
		return inviteCodeRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public InviteCodeDtos.InviteCodeResponse create() {
		long creatorId = currentUserService.requireUserId();
		InviteCode invite = new InviteCode();
		invite.setCode(randomCode());
		invite.setCreatedByUserId(creatorId);
		InviteCode saved = inviteCodeRepository.save(invite);
		log.info("Created invite code id={} by userId={}", saved.getId(), creatorId);
		return toResponse(saved);
	}

	@Transactional
	public void deleteUnused(Long id) {
		InviteCode invite = inviteCodeRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Invite code not found: " + id));
		if (!invite.isAvailable()) {
			throw new ConflictException("Cannot delete a used invite code");
		}
		inviteCodeRepository.delete(invite);
		log.info("Deleted unused invite code id={}", id);
	}

	public void requireAvailable(String rawCode) {
		if (!inviteCodeRepository.existsByCodeAndUsedAtIsNull(normalize(rawCode))) {
			throw new UnauthorizedException("Invalid or already used invite code");
		}
	}

	/**
	 * Locks an unused invite for the current transaction (the caller must have one open). Call before creating the user so
	 * invalid invites fail with 401 before any email-existence checks (no enumeration oracle).
	 */
	public InviteCode lockAvailable(String rawCode) {
		return inviteCodeRepository.findByCodeAndUsedAtIsNull(normalize(rawCode))
				.orElseThrow(() -> new UnauthorizedException("Invalid or already used invite code"));
	}

	/** Marks a previously locked invite as used by {@code userId}; the open transaction flushes it. */
	public void markUsed(InviteCode invite, Long userId) {
		invite.setUsedAt(Instant.now());
		invite.setUsedByUserId(userId);
	}

	private static String normalize(String rawCode) {
		return rawCode.strip().toUpperCase(Locale.ROOT);
	}

	private String randomCode() {
		char[] chars = new char[CODE_LENGTH];
		for (int i = 0; i < CODE_LENGTH; i++) {
			chars[i] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
		}
		return new String(chars);
	}

	private InviteCodeDtos.InviteCodeResponse toResponse(InviteCode invite) {
		return new InviteCodeDtos.InviteCodeResponse(
				invite.getId(),
				invite.getCode(),
				invite.getCreatedAt().toString(),
				invite.getCreatedByUserId(),
				invite.getUsedAt() == null ? null : invite.getUsedAt().toString(),
				invite.getUsedByUserId(),
				invite.isAvailable()
		);
	}
}
