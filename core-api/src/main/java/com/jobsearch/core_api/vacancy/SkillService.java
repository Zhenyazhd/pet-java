package com.jobsearch.core_api.vacancy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The skill dictionary: finds the skill for each requirement and adds only the ones not seen before.
 * Callers must run inside their own transaction and call this before changing any managed entity, because
 * the queries here flush the persistence context.
 */
@Service
public class SkillService {

	private final SkillRepository skillRepository;

	public SkillService(SkillRepository skillRepository) {
		this.skillRepository = skillRepository;
	}

	public record Resolved(Skill skill, boolean required) {
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<Resolved> resolve(List<VacancyRequirements.Item> items) {
		if (items.isEmpty()) {
			return List.of();
		}
		// Sorted: transactions that add overlapping skills take the unique-index locks in the same order.
		Map<String, String> nameByKey = new TreeMap<>();
		for (VacancyRequirements.Item item : items) {
			nameByKey.putIfAbsent(SkillKey.of(item.name()), SkillKey.display(item.name()));
		}

		Map<String, Skill> byKey = find(nameByKey);
		if (byKey.size() < nameByKey.size()) {
			nameByKey.forEach((key, name) -> {
				if (!byKey.containsKey(key)) {
					skillRepository.insertIfAbsent(name, key);
				}
			});
			// A separate statement after the insert: it also sees skills another transaction has just committed.
			// (Relies on READ_COMMITTED, the default; under REPEATABLE READ this could miss that row.)
			byKey.putAll(find(nameByKey));
		}

		return items.stream()
				.map(item -> new Resolved(skillFor(byKey, item.name()), item.required()))
				.toList();
	}

	private Map<String, Skill> find(Map<String, String> nameByKey) {
		return skillRepository.findByNameKeyIn(nameByKey.keySet()).stream()
				.collect(Collectors.toMap(Skill::getNameKey, Function.identity(), (a, b) -> a, TreeMap::new));
	}

	private static Skill skillFor(Map<String, Skill> byKey, String name) {
		Skill skill = byKey.get(SkillKey.of(name));
		if (skill == null) {
			throw new IllegalStateException("Skill was not found after it was added: " + name);
		}
		return skill;
	}
}
