package com.jobsearch.core_api.jobs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** The claim loop itself, without a database: slots must survive a failing claim. */
class JobDispatcherPollTest {

	@Test
	void failedClaimGivesTheSlotBack() {
		JobRepository jobRepository = mock(JobRepository.class);
		when(jobRepository.claimNext(anyString(), anyLong()))
				.thenThrow(new DataAccessResourceFailureException("connection refused"))
				.thenReturn(Optional.empty());
		// Stubbed before the dispatcher is built: it reads concurrency() once, in its constructor.
		JobHandler oneSlot = mock(JobHandler.class);
		when(oneSlot.type()).thenReturn(JobType.RESUME_PDF);
		when(oneSlot.concurrency()).thenReturn(1);
		when(oneSlot.lease()).thenReturn(Duration.ofMinutes(1));
		JobDispatcher dispatcher = new JobDispatcher(jobRepository, List.of(oneSlot));

		// The first poll must survive the failure, and the second must still have its one slot to claim with.
		assertDoesNotThrow(dispatcher::poll);
		dispatcher.poll();

		verify(jobRepository, times(2)).claimNext(anyString(), anyLong());
	}

	@Test
	void aDisabledTypeIsNeverClaimed() {
		JobRepository jobRepository = mock(JobRepository.class);
		JobHandler off = mock(JobHandler.class);
		when(off.type()).thenReturn(JobType.ATS_MATCH);
		when(off.concurrency()).thenReturn(2);
		when(off.lease()).thenReturn(Duration.ofMinutes(1));
		when(off.disabled()).thenReturn(true);
		JobDispatcher dispatcher = new JobDispatcher(jobRepository, List.of(off));

		dispatcher.poll();

		verify(jobRepository, never()).claimNext(anyString(), anyLong());
	}
}
