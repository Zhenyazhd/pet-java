package com.jobsearch.core_api.vacancy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

	@EntityGraph(attributePaths = { "requirements", "application" })
	@Query("select v from Vacancy v where v.id = :id")
	Optional<Vacancy> findDetailedById(@Param("id") Long id);

	@EntityGraph(attributePaths = { "requirements", "application" })
	@Query("select v from Vacancy v order by v.createdAt desc")
	List<Vacancy> findAllByOrderByCreatedAtDesc();

	boolean existsByUrl(String url);
}
