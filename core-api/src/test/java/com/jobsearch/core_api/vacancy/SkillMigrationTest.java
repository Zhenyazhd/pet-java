package com.jobsearch.core_api.vacancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V3 on data written by V2: spellings of one skill collapse, per-vacancy duplicates merge. */
class SkillMigrationTest {

	@Test
	void existingRequirementsMoveIntoTheDictionary() throws Exception {
		try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")) {
			postgres.start();
			Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
					.target("2").load().migrate();

			try (Connection connection = DriverManager.getConnection(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
					Statement statement = connection.createStatement()) {
				statement.execute("insert into app_user (id, email, display_name) values (900, 'm@test.local', 'M')");
				statement.execute("insert into vacancy (id, url, title, user_id) values (901, 'u1', 'T', 900), (902, 'u2', 'T', 900)");
				statement.execute("""
						insert into vacancy_requirement (id, vacancy_id, name, required) values
						(1, 901, 'Java', false), (2, 901, 'java ', true), (3, 901, 'Английский', true),
						(4, 902, 'JAVA', false), (5, 902, 'Spring\u00a0 \tBoot', true), (6, 902, 'ЯЗЫК', true),
						(7, 902, E'\t ', false)
						""");

				Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
						.load().migrate();

				Map<String, String> keyByName = new HashMap<>();
				try (ResultSet rs = statement.executeQuery("select name, name_key from skill")) {
					while (rs.next()) {
						keyByName.put(rs.getString(1), rs.getString(2));
					}
				}
				assertEquals(Set.of("Java", "Spring Boot", "Английский", "ЯЗЫК"), keyByName.keySet());
				// One definition of "the same skill": what SQL stored equals what the application computes.
				keyByName.forEach((name, key) -> assertEquals(SkillKey.of(name), key));

				Map<String, Boolean> requiredByVacancyAndName = new HashMap<>();
				try (ResultSet rs = statement.executeQuery("""
						select r.vacancy_id, s.name, r.required from vacancy_requirement r
						join skill s on s.id = r.skill_id
						""")) {
					while (rs.next()) {
						requiredByVacancyAndName.put(rs.getLong(1) + "/" + rs.getString(2), rs.getBoolean(3));
					}
				}
				assertEquals(Map.of(
						"901/Java", true,
						"901/Английский", true,
						"902/Java", false,
						"902/Spring Boot", true,
						"902/ЯЗЫК", true), requiredByVacancyAndName);

				try (ResultSet rs = statement.executeQuery("""
						select count(*) from information_schema.columns
						where table_name = 'vacancy_requirement' and column_name = 'name'
						""")) {
					rs.next();
					assertEquals(0, rs.getInt(1), "the text column is gone");
				}
			}
		}
	}
}
