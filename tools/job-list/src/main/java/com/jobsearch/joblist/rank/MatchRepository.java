package com.jobsearch.joblist.rank;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

	/** Jobs already ranked for this profile with the current prompt and expectations. */
	@Query("select m.job.id from Match m where m.profileId = :profileId and m.promptHash = :promptHash")
	List<Long> rankedJobIds(@Param("profileId") long profileId, @Param("promptHash") String promptHash);

	@Query("select m from Match m where m.profileId = :profileId and m.job.id in :jobIds")
	List<Match> findForJobs(@Param("profileId") long profileId, @Param("jobIds") Collection<Long> jobIds);

	/** Best first. The cursor is the (score, id) of the last item of the previous page: score 101 starts from the top. */
	@Query("""
			select m from Match m join fetch m.job
			where m.profileId = :profileId and m.score >= :minScore and m.rankedAt >= :since
			  and ((:delivered = true and m.deliveredAt is not null) or (:delivered = false and m.deliveredAt is null))
			  and (m.score < :cursorScore or (m.score = :cursorScore and m.id < :cursorId))
			order by m.score desc, m.id desc
			""")
	List<Match> page(
			@Param("profileId") long profileId,
			@Param("minScore") int minScore,
			@Param("since") Instant since,
			@Param("delivered") boolean delivered,
			@Param("cursorScore") int cursorScore,
			@Param("cursorId") long cursorId,
			Pageable pageable);

	/** The match if its profile belongs to the client. */
	@Query("select m from Match m join fetch m.job where m.id = :id and m.profileId in (select p.id from SearchProfile p where p.client = :client)")
	Optional<Match> findOwned(@Param("id") long id, @Param("client") String client);
}
