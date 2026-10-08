package com.jobsearch.core_api.profile;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByEmailIgnoreCase(String email);

	@Transactional
	@Modifying
	@Query("""
			update AppUser u
			set u.resumeJson = :json, u.resumeVersion = u.resumeVersion + 1
			where u.id = :id and u.resumeVersion = :expectedVersion
			""")
	int updateResume(@Param("id") long id, @Param("json") String json, @Param("expectedVersion") int expectedVersion);
}
