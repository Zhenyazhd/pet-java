package com.jobsearch.core_api.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.core_api.profile.ProfileService;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Resume saves are rejected when stale, and resume and profile saves never undo each other. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ResumeSaveTest {

	@Autowired
	private ResumeService resumeService;
	@Autowired
	private ProfileService profileService;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private long userId;
	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	@BeforeEach
	void setUp() {
		userId = TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		SecurityContextHolder.clearContext();
	}

	@Test
	void saveBumpsTheVersionAndAStaleSaveIsRejected() {
		ResumeDocument loaded = resumeService.get();

		ResumeDocument saved = resumeService.save(renamed(loaded, "First"));

		assertEquals(loaded.version() + 1, saved.version());
		assertEquals("First", resumeService.get().name());
		assertThrows(ConflictException.class, () -> resumeService.save(renamed(loaded, "Second")));
		assertEquals("First", resumeService.get().name());
	}

	@Test
	void twoSavesOfTheSameVersionAtOnceLetOnlyOneThrough() throws Exception {
		ResumeDocument loaded = resumeService.get();
		CyclicBarrier bothReady = new CyclicBarrier(2);
		List<Future<ResumeDocument>> saves = new ArrayList<>();
		for (String name : List.of("Tab A", "Tab B")) {
			saves.add(executor.submit(asCurrentUser(() -> {
				bothReady.await();
				return resumeService.save(renamed(loaded, name));
			})));
		}

		List<String> savedNames = new ArrayList<>();
		int conflicts = 0;
		for (Future<ResumeDocument> save : saves) {
			try {
				savedNames.add(save.get().name());
			}
			catch (ExecutionException ex) {
				assertInstanceOf(ConflictException.class, ex.getCause());
				conflicts++;
			}
		}

		assertEquals(1, savedNames.size());
		assertEquals(1, conflicts);
		ResumeDocument stored = resumeService.get();
		assertEquals(savedNames.getFirst(), stored.name());
		assertEquals(loaded.version() + 1, stored.version());
	}

	@Test
	void profileSaveDoesNotUndoAResumeSavedAfterTheUserWasRead() {
		ResumeDocument loaded = resumeService.get();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			// The profile save's transaction reads the user before the resume save commits...
			AppUser readBeforeResumeSave = userRepository.findById(userId).orElseThrow();
			assertEquals(loaded.version(), readBeforeResumeSave.getResumeVersion());
			await(executor.submit(asCurrentUser(() -> resumeService.save(renamed(loaded, "Saved meanwhile")))));
			// ...and writes after it.
			profileService.saveProfile(new ProfileRequest("New Name", readBeforeResumeSave.getEmail(), "Career"));
		});

		ResumeDocument stored = resumeService.get();
		assertEquals("Saved meanwhile", stored.name());
		assertEquals(loaded.version() + 1, stored.version());
		assertEquals("New Name", profileService.getProfile().displayName());
	}

	/** Runs the task on another thread as the user signed in on this one. */
	private static <T> Callable<T> asCurrentUser(Callable<T> task) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return () -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {
				return task.call();
			}
			finally {
				SecurityContextHolder.clearContext();
			}
		};
	}

	private static <T> T await(Future<T> future) {
		try {
			return future.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static ResumeDocument renamed(ResumeDocument doc, String name) {
		return new ResumeDocument(
				name, doc.headline(), doc.phone(), doc.email(), doc.linkedinUrl(), doc.linkedinLabel(),
				doc.profile(), doc.experience(), doc.education(), doc.achievements(), doc.skills(), doc.locale(),
				doc.version()
		);
	}
}
