package com.jobsearch.core_api.jobapplication;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

	Optional<JobApplication> findByVacancyId(Long vacancyId);

	boolean existsByVacancyId(Long vacancyId);

	@Query("select a from JobApplication a join a.vacancy v where v.userId = :userId")
	List<JobApplication> findAllByVacancyUserId(@Param("userId") Long userId);

	@Query("select a from JobApplication a join a.vacancy v where a.id = :id and v.userId = :userId")
	Optional<JobApplication> findByIdAndVacancyUserId(@Param("id") Long id, @Param("userId") Long userId);
}
