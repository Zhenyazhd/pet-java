package com.jobsearch.core_api.cv;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvVersionRepository extends JpaRepository<CvVersion, Long> {

	List<CvVersion> findByUserIdOrderByCreatedAtDesc(Long userId);

	Optional<CvVersion> findByIdAndUserId(Long id, Long userId);
}
