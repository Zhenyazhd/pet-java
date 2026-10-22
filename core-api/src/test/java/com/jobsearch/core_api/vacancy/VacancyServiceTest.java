package com.jobsearch.core_api.vacancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.auth.AppUserRepository;
import com.jobsearch.core_api.vacancy.VacancyDtos.RequirementRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.RequirementResponse;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** Editing a vacancy's requirements against the real schema (UNIQUE(vacancy_id, skill_id)). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VacancyServiceTest {

	@Autowired
	private VacancyService service;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private SkillRepository skillRepository;

	@BeforeEach
	void setUp() {
		TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void updateKeepsExistingRequirementsAddsNewAndDropsMissing() {
		VacancyResponse created = service.create(request(req("Java", true), req("Kafka", false)));
		Long javaId = created.requirements().stream()
				.filter(r -> r.name().equals("Java")).findFirst().orElseThrow().id();

		VacancyResponse updated = service.update(created.id(), request(req("Java", false), req("SQL", true)));

		assertEquals(List.of("Java", "SQL"), updated.requirements().stream().map(RequirementResponse::name).sorted().toList());
		RequirementResponse java = updated.requirements().stream()
				.filter(r -> r.name().equals("Java")).findFirst().orElseThrow();
		assertEquals(javaId, java.id(), "the kept requirement is updated in place");
		assertEquals(false, java.required());
		assertEquals(2, service.findById(created.id()).requirements().size());
	}

	@Test
	void updateWithOnlyACaseChangeKeepsTheSkill() {
		VacancyResponse created = service.create(request(req("java", true)));

		VacancyResponse updated = service.update(created.id(), request(req("Java", true)));

		assertEquals(created.requirements().get(0).id(), updated.requirements().get(0).id());
		assertEquals(1, updated.requirements().size());
	}

	@Test
	void sameSkillInDifferentSpellingsIsStoredOnce() {
		String name = "Spring Boot " + System.nanoTime();

		VacancyResponse first = service.create(request(req(name, true)));
		VacancyResponse second = service.create(request(req("  " + name.toUpperCase() + "\u00a0", false)));

		assertEquals(1, skillRepository.findByNameKeyIn(List.of(name.toLowerCase())).size());
		assertEquals(first.requirements().get(0).name(), second.requirements().get(0).name());
	}

	@Test
	void updateToAnExistingUrlWithNewSkillsIsAConflict() {
		VacancyResponse first = service.create(request(req("Java", true)));
		VacancyResponse second = service.create(request(req("Java", true)));
		VacancyRequest clash = new VacancyRequest(
				service.findById(first.id()).url(), "Backend Engineer", "Acme", "Build APIs.", null,
				List.of(req("Brand new skill " + System.nanoTime(), true)));

		assertThrows(ConflictException.class, () -> service.update(second.id(), clash));
	}

	@Test
	void updateWithNoRequirementsClearsThem() {
		VacancyResponse created = service.create(request(req("Java", true)));

		VacancyResponse updated = service.update(created.id(), request());

		assertEquals(0, updated.requirements().size());
	}

	private static RequirementRequest req(String name, boolean required) {
		return new RequirementRequest(name, required);
	}

	private static VacancyRequest request(RequirementRequest... requirements) {
		return new VacancyRequest(
				"https://jobs.example.com/" + System.nanoTime(), "Backend Engineer", "Acme", "Build APIs.", null,
				List.of(requirements));
	}
}
