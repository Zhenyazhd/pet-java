package com.jobsearch.core_api.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromptDefaultRepository extends JpaRepository<PromptDefault, String> {

	@Modifying
	@Query(value = """
			insert into prompt_default (part_key, content, updated_at) values (:partKey, :content, now())
			on conflict (part_key) do update set content = excluded.content, updated_at = now()
			""", nativeQuery = true)
	void upsert(@Param("partKey") String partKey, @Param("content") String content);
}
