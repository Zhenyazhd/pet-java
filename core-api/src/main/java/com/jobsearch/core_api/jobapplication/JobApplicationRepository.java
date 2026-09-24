package com.jobsearch.core_api.jobapplication;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

	Optional<JobApplication> findByVacancyId(Long vacancyId);

	boolean existsByVacancyId(Long vacancyId);
}
