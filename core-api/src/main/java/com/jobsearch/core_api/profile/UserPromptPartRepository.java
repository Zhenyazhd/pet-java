package com.jobsearch.core_api.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPromptPartRepository extends JpaRepository<UserPromptPart, UserPromptPart.Id> {

	@Query("select p from UserPromptPart p where p.id.userId = :userId")
	List<UserPromptPart> findAllByUserId(@Param("userId") long userId);

	/** An upsert, so two saves of the same part racing cannot fail on the primary key. */
	@Modifying
	@Query(value = """
			insert into user_prompt_part (user_id, part_key, content, base_hash, updated_at)
			values (:userId, :partKey, :content, :baseHash, now())
			on conflict (user_id, part_key) do update
				set content = excluded.content, base_hash = excluded.base_hash, updated_at = now()
			""", nativeQuery = true)
	void upsert(@Param("userId") long userId, @Param("partKey") String partKey, @Param("content") String content,
			@Param("baseHash") String baseHash);

	@Modifying
	@Query("delete from UserPromptPart p where p.id.userId = :userId and p.id.partKey = :partKey")
	void deleteByUserIdAndPartKey(@Param("userId") long userId, @Param("partKey") String partKey);
}
