package com.jobsearch.joblist.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobFingerprintTest {

	@Test
	void sameVacancyOnTwoBoardsHasOneFingerprint() {
		Job one = job("ACME, Inc.", "Développeur Python H/F", "Paris");
		Job two = job("acme inc", "DÉVELOPPEUR PYTHON (m/f/d)", "paris");

		assertEquals(JobFingerprint.of(one), JobFingerprint.of(two));
	}

	@Test
	void differentCityOrTitleGivesAnotherFingerprint() {
		Job base = job("Acme", "Python Developer", "Paris");

		assertNotEquals(JobFingerprint.of(base), JobFingerprint.of(job("Acme", "Python Developer", "Lyon")));
		assertNotEquals(JobFingerprint.of(base), JobFingerprint.of(job("Acme", "Java Developer", "Paris")));
	}

	private static Job job(String company, String title, String city) {
		Job job = TestData.job(SourceName.HELLOWORK, title);
		job.setCompany(company);
		job.setCities(List.of(city));
		return job;
	}
}
