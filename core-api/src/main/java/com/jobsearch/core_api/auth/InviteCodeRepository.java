package com.jobsearch.core_api.auth;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

	boolean existsByCodeIgnoreCase(String code);

	List<InviteCode> findAllByOrderByCreatedAtDesc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from InviteCode c where lower(c.code) = lower(:code) and c.usedAt is null")
	Optional<InviteCode> findAvailableForUpdate(@Param("code") String code);
}
