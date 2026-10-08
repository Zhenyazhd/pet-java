package com.jobsearch.core_api.vacancy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

	@EntityGraph(attributePaths = { "requirements", "application" })
	@Query("select v from Vacancy v where v.id = :id and v.userId = :userId")
	Optional<Vacancy> findDetailedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	@EntityGraph(attributePaths = { "requirements", "application" })
	@Query("select v from Vacancy v where v.userId = :userId order by v.createdAt desc")
	List<Vacancy> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

	boolean existsByUserIdAndUrl(Long userId, String url);

	Optional<Vacancy> findByUserIdAndUrl(Long userId, String url);

	boolean existsByIdAndUserId(Long id, Long userId);

	Optional<Vacancy> findByIdAndUserId(Long id, Long userId);

	@Transactional
	@Modifying
	@Query("""
			update Vacancy v set v.matchPercent = :matchPercent, v.updatedAt = :now
			where v.id = :id and v.userId = :userId
			""")
	int updateMatchPercent(
			@Param("id") long id,
			@Param("userId") long userId,
			@Param("matchPercent") int matchPercent,
			@Param("now") Instant now
	);
}
