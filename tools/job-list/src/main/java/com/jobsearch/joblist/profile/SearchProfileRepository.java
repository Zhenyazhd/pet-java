package com.jobsearch.joblist.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchProfileRepository extends JpaRepository<SearchProfile, Long> {

	Optional<SearchProfile> findByIdAndClient(Long id, String client);

	List<SearchProfile> findByClientOrderById(String client);

	List<SearchProfile> findByClientAndOwnerRefOrderById(String client, String ownerRef);

	long countByClientAndOwnerRef(String client, String ownerRef);

	long countByClient(String client);

	Optional<SearchProfile> findByClientAndOwnerRefAndName(String client, String ownerRef, String name);
}
