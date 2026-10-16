package com.jobsearch.joblist.run;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunSourceRepository extends JpaRepository<RunSource, Long> {

	List<RunSource> findByRunIdIn(Collection<Long> runIds);
}
