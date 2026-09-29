package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.UnauthorizedException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
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
		String code = generateUniqueCode();
		InviteCode invite = new InviteCode();
		invite.setCode(code);
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

	/** Locks and consumes an unused code; throws if missing/used. */
	@Transactional
	public InviteCode consume(String rawCode, Long userId) {
		String code = rawCode == null ? "" : rawCode.strip();
		if (code.isBlank()) {
			throw new UnauthorizedException("Invalid invite code");
		}
		InviteCode invite = inviteCodeRepository.findAvailableForUpdate(code)
				.orElseThrow(() -> new UnauthorizedException("Invalid or already used invite code"));
		invite.setUsedAt(Instant.now());
		invite.setUsedByUserId(userId);
		return inviteCodeRepository.save(invite);
	}

	private String generateUniqueCode() {
		for (int attempt = 0; attempt < 20; attempt++) {
			String candidate = randomCode();
			if (!inviteCodeRepository.existsByCodeIgnoreCase(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Failed to generate a unique invite code");
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
