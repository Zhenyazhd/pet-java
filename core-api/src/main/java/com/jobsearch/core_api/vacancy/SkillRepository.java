package com.jobsearch.core_api.vacancy;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Only reads and the race-free insert: skills are never changed or deleted. */
public interface SkillRepository extends Repository<Skill, Long> {

	long count();

	List<Skill> findByNameKeyIn(Collection<String> nameKeys);

	@Modifying
	@Query(value = "insert into skill (name, name_key) values (:name, :key) on conflict (name_key) do nothing",
			nativeQuery = true)
	int insertIfAbsent(@Param("name") String name, @Param("key") String key);
}
