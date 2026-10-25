package com.jobsearch.core_api.auth.invite;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

	List<InviteCode> findAllByOrderByCreatedAtDesc();

	boolean existsByCodeAndUsedAtIsNull(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<InviteCode> findByCodeAndUsedAtIsNull(String code);
}
