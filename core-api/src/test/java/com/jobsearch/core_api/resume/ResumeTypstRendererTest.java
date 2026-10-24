package com.jobsearch.core_api.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jobsearch.core_api.resume.ResumeDtos.AchievementItem;
import com.jobsearch.core_api.resume.ResumeDtos.EducationItem;
import com.jobsearch.core_api.resume.ResumeDtos.ExperienceItem;
import com.jobsearch.core_api.resume.ResumeDtos.LayoutSettings;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeDtos.SkillItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ResumeTypstRendererTest {

	private static final String HOSTILE = "# $ * _ ` < > @ [ ] ~ \\ \\\\ \" { } %";

	private final ObjectMapper mapper = JsonMapper.builder().build();
	private final ResumeTypstRenderer renderer = new ResumeTypstRenderer(mapper);

	private static ResumeDocument doc(String text, String bullet) {
		return new ResumeDocument("Ada " + text, text, "+33 1 23 45 67 89", "ada@example.com",
				"https://www.linkedin.com/in/ada", "linkedin.com/in/ada", text,
				List.of(new ExperienceItem("Engineer " + text, "Acme", "2020 - 2024", List.of(bullet))),
				List.of(new EducationItem("MSc", "University", "Paris", text)),
				List.of(new AchievementItem("Prize", text)),
				List.of(new SkillItem("Languages", text)), "en", 3);
	}

	/** Reads the Typst string literal on the first line back the way Typst does. */
	private static String parseLiteral(String source) {
		String line = source.substring(0, source.indexOf('\n'));
		String prefix = "#let r = json(bytes(\"";
		assertTrue(line.startsWith(prefix) && line.endsWith("\"))"), line);
		String body = line.substring(prefix.length(), line.length() - 3);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (c != '\\') {
				assertFalse(c == '"' || c < 0x20, "unescaped char " + (int) c);
				out.append(c);
				continue;
			}
			char n = body.charAt(++i);
			switch (n) {
				case '\\', '"' -> out.append(n);
				case 'n' -> out.append('\n');
				case 'r' -> out.append('\r');
				case 't' -> out.append('\t');
				case 'u' -> {
					int end = body.indexOf('}', i);
					out.appendCodePoint(Integer.parseInt(body.substring(i + 2, end), 16));
					i = end;
				}
				default -> throw new AssertionError("bad escape \\" + n);
			}
		}
		return out.toString();
	}

	@Test
	void sourceIsOneLiteralLineThenTheTemplate() {
		String source = renderer.render(doc("x", "y"));
		assertTrue(source.startsWith("#let r = json(bytes(\""));
		String rest = source.substring(source.indexOf('\n') + 1);
		assertTrue(rest.contains("#set page"), rest.substring(0, 200));
		assertFalse(source.substring(0, source.indexOf('\n')).contains("\u2028"));
	}

	@Test
	void literalRoundTripsToTheJson() throws Exception {
		String nasty = HOSTILE + "\nline2\r\n\ttab \u0001\u001f \u2028 \u2029 \u007f emoji 🚀 Привет";
		ResumeDocument d = doc(nasty, nasty);
		JsonNode parsed = mapper.readTree(parseLiteral(renderer.render(d)));
		assertEquals(renderer.normalized(d), parsed);
		assertEquals(nasty, parsed.path("profile").asString());
		assertEquals(nasty, parsed.path("experience").path(0).path("bullets").path(0).asString());
		assertFalse(parsed.has("version"));
	}

	@Test
	void nullsBecomeEmptyStringsAndLists() {
		ResumeDocument d = new ResumeDocument("Ada", null, null, null, null, null, null, null, null, null, null, null, null);
		JsonNode n = renderer.normalized(d);
		for (String key : List.of("headline", "phone", "email", "linkedinUrl", "linkedinLabel", "profile")) {
			assertEquals("", n.path(key).asString(), key);
		}
		for (String key : List.of("experience", "education", "achievements", "skills")) {
			assertTrue(n.path(key).isArray() && n.path(key).isEmpty(), key);
		}
		assertEquals("fr", n.path("locale").asString());
		assertFalse(renderer.render(d).contains("null"));

		ResumeDocument inner = new ResumeDocument("Ada", "", "", "", "", "", "",
				List.of(new ExperienceItem(null, null, null, null)), List.of(new EducationItem(null, null, null, null)),
				List.of(new AchievementItem(null, null)), List.of(new SkillItem(null, null)), "EN", null);
		JsonNode m = renderer.normalized(inner);
		assertEquals("", m.path("experience").path(0).path("title").asString());
		assertTrue(m.path("experience").path(0).path("bullets").isArray());
		assertEquals("", m.path("education").path(0).path("details").asString());
		assertEquals("", m.path("skills").path(0).path("items").asString());
		assertEquals("en", m.path("locale").asString());
	}

	@Test
	void noLayoutLeavesTheSourceAsItWas() {
		ResumeDocument plain = doc("x", "y");
		ResumeDocument emptyLayout = new ResumeDocument(plain.name(), plain.headline(), plain.phone(), plain.email(),
				plain.linkedinUrl(), plain.linkedinLabel(), plain.profile(), plain.experience(), plain.education(),
				plain.achievements(), plain.skills(), plain.locale(), plain.version(), null);
		assertFalse(renderer.normalized(plain).has("layout"));
		assertEquals(renderer.render(plain), renderer.render(emptyLayout));
	}

	@Test
	void layoutValuesReachTheTemplateAndUnsetOnesAreLeftOut() {
		ResumeDocument plain = doc("x", "y");
		ResumeDocument custom = new ResumeDocument(plain.name(), plain.headline(), plain.phone(), plain.email(),
				plain.linkedinUrl(), plain.linkedinLabel(), plain.profile(), plain.experience(), plain.education(),
				plain.achievements(), plain.skills(), plain.locale(), plain.version(),
				new LayoutSettings(11.0, null, null, null, null, null, null, null, null, null, false));
		JsonNode layout = renderer.normalized(custom).path("layout");
		assertEquals(11.0, layout.path("fontSize").asDouble());
		assertFalse(layout.path("autoFit").asBoolean(true));
		assertFalse(layout.has("lineSpacing"));
	}

	@Test
	void layoutSurvivesStorageAndAResumeStoredBeforeItReadsAsNone() throws Exception {
		ResumeDocument plain = doc("x", "y");
		LayoutSettings layout = new LayoutSettings(11.5, 0.7, null, null, null, null, null, 1.5, null, null, false);
		ResumeDocument custom = new ResumeDocument(plain.name(), plain.headline(), plain.phone(), plain.email(),
				plain.linkedinUrl(), plain.linkedinLabel(), plain.profile(), plain.experience(), plain.education(),
				plain.achievements(), plain.skills(), plain.locale(), plain.version(), layout);

		String stored = mapper.writeValueAsString(custom);
		assertEquals(layout, mapper.readValue(stored, ResumeDocument.class).layout());

		String legacy = mapper.writeValueAsString(plain).replace(",\"layout\":null", "");
		assertFalse(legacy.contains("layout"));
		assertEquals(null, mapper.readValue(legacy, ResumeDocument.class).layout());
	}

	@Test
	void versionDoesNotChangeTheSource() {
		assertEquals(renderer.render(doc("x", "y")), renderer.render(doc("x", "y")));
	}

	@Test
	void hostileTextComesOutLiterallyInTheCompiledPdf(@TempDir Path dir) throws Exception {
		if ("true".equalsIgnoreCase(System.getenv("CI"))) {
			assertTrue(typstOnPath(), "typst must be on PATH in CI");
		}
		else {
			assumeTrue(typstOnPath(), "typst is not on PATH");
		}
		String code = "#set text(fill: red) #panic(\"x\") *bold* `raw` #import \"x\": y";
		ResumeDocument d = new ResumeDocument("Ada Lovelace", HOSTILE, "+33 1 23", "ada@example.com",
				"https://www.linkedin.com/in/ada", "linkedin.com/in/ada", code + " emoji 🚀 Привет, мир",
				List.of(new ExperienceItem("Engineer", "Acme", "2020 - 2024",
						List.of("multi\nline bullet " + HOSTILE, "Кириллица и *звёздочки* _x_"))),
				List.of(new EducationItem("MSc", "University", "Paris", "$math$ @ref <label>")),
				List.of(new AchievementItem("Prize", "100% {braces} [brackets] ~tilde")),
				List.of(new SkillItem("Languages", "C# F# a_b")), "en", 1);
		Files.writeString(dir.resolve("main.typ"), renderer.render(d));
		Process p = new ProcessBuilder("typst", "compile", "--root", dir.toString(), "--ignore-system-fonts",
				dir.resolve("main.typ").toString(), dir.resolve("out.pdf").toString())
				.redirectErrorStream(true).start();
		String log = new String(p.getInputStream().readAllBytes());
		assertTrue(p.waitFor(60, TimeUnit.SECONDS));
		assertEquals(0, p.exitValue(), log);
		try (PDDocument pdf = Loader.loadPDF(Files.readAllBytes(dir.resolve("out.pdf")))) {
			assertEquals(1, pdf.getNumberOfPages());
			// Emoji is in the source but the embedded fonts have no glyph for it: it compiles, and is simply not drawn.
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			for (String expected : List.of(HOSTILE, code, "Привет, мир", "multi line bullet",
					"Кириллица и *звёздочки* _x_", "$math$ @ref <label>", "100% {braces} [brackets] ~tilde", "C# F# a_b")) {
				assertTrue(text.contains(expected.replaceAll("\\s+", " ")), "missing: " + expected + "\n" + text);
			}
		}
	}

	private static boolean typstOnPath() {
		try {
			Process p = new ProcessBuilder("typst", "--version").redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
		}
		catch (IOException | InterruptedException ex) {
			return false;
		}
	}
}
