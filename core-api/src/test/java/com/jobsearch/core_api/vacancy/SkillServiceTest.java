package com.jobsearch.core_api.vacancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobsearch.core_api.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SkillServiceTest {

	@Autowired
	private SkillService skillService;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private List<Long> resolveIds(List<String> names) {
		return new TransactionTemplate(transactionManager).execute(status -> skillService
				.resolve(names.stream().map(name -> new VacancyRequirements.Item(name, true)).toList())
				.stream().map(resolved -> resolved.skill().getId()).toList());
	}

	@Test
	void knownSkillsAreReusedAndOnlyNewOnesAreAdded() {
		String tag = Long.toString(System.nanoTime());

		List<Long> first = resolveIds(List.of("Kotlin " + tag, "Gradle " + tag));
		List<Long> second = resolveIds(List.of("gradle  " + tag, "Kotlin " + tag, "Ktor " + tag));

		assertEquals(3, skillRepository.findByNameKeyIn(
				List.of("kotlin " + tag, "gradle " + tag, "ktor " + tag)).size());
		assertEquals(first.get(0), second.get(1));
		assertEquals(first.get(1), second.get(0));
	}

	@Test
	void overlappingSetsInOppositeOrderResolveConcurrentlyToTheSameIds() throws Exception {
		String tag = Long.toString(System.nanoTime());
		List<String> forward = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			forward.add("Skill " + i + " " + tag);
		}
		List<String> backward = new ArrayList<>(forward);
		java.util.Collections.reverse(backward);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<List<Long>> a = pool.submit(() -> {
				start.await();
				return resolveIds(forward);
			});
			Future<List<Long>> b = pool.submit(() -> {
				start.await();
				return resolveIds(backward);
			});
			start.countDown();

			List<Long> idsA = a.get();
			List<Long> idsB = new ArrayList<>(b.get());
			java.util.Collections.reverse(idsB);
			assertEquals(idsA, idsB);
		}
		finally {
			pool.shutdownNow();
		}
	}
}
