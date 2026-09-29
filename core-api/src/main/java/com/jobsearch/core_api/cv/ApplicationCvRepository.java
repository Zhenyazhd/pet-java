package com.jobsearch.core_api.cv;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationCvRepository extends JpaRepository<ApplicationCv, Long> {

	List<ApplicationCv> findByUserIdOrderBySentAtDesc(Long userId);

	List<ApplicationCv> findByVacancyIdAndUserIdOrderBySentAtDesc(Long vacancyId, Long userId);

	boolean existsByCvVersionIdAndUserId(Long cvVersionId, Long userId);
}
