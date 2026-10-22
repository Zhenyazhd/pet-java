package com.jobsearch.atsworker;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code POST /score}: {@code {"pdfBase64": "...", "jobDescription": "<optional>"}} in, the model's score of the
 * resume on six ATS platforms out (the {@code results} array of the ATS Screener format) plus {@code _model}.
 * The model is given the text an ATS would read from the PDF, extracted the way {@code /check} does. A plain
 * {@code "resumeText"} may be sent instead of the PDF. Resume and vacancy text are never logged.
 */
final class ScoreHandler extends PostJsonHandler {

	static final int PLATFORMS = 6;
	private static final Logger log = Logger.getLogger(ScoreHandler.class.getName());
	private static final int MAX_TEXT_BODY_BYTES = 256 * 1024;
	private static final int MIN_TEXT_CHARS = 50;

	private final LlmClient llm;
	private final WorkerConfig config;

	ScoreHandler(LlmClient llm, WorkerConfig config) {
		super("scoring", config.token(), config.maxRequestBytes(), config.llm().concurrency(), 2);
		this.llm = llm;
		this.config = config;
	}

	@Override
	protected String usage() {
		return "Expected JSON {\"pdfBase64\": \"<base64 PDF>\", \"jobDescription\": \"<optional>\"}";
	}

	@Override
	protected boolean preflight(HttpExchange exchange) throws IOException {
		if (!llm.configured()) {
			Responses.error(exchange, 503, "not_configured", "No LLM API key is set on the worker");
			return false;
		}
		return true;
	}

	@Override
	protected Work prepare(HttpExchange exchange, JsonNode json, int bodyBytes) throws IOException {
		String resumeText = json.path("resumeText").asString("");
		String jobDescription = json.path("jobDescription").asString("");
		String pdfBase64 = json.path("pdfBase64").asString("");
		byte[] pdf = null;
		if (!pdfBase64.isBlank()) {
			try {
				pdf = Base64.getDecoder().decode(pdfBase64);
			}
			catch (IllegalArgumentException ex) {
				badRequest(exchange);
				return null;
			}
		}
		if (resumeText.isBlank() && pdf == null) {
			badRequest(exchange);
			return null;
		}
		if (pdf == null && bodyBytes > MAX_TEXT_BODY_BYTES) {
			Responses.error(exchange, 413, "too_large", "Request body exceeds " + MAX_TEXT_BODY_BYTES + " bytes");
			return null;
		}
		if (pdf != null && !PdfText.looksLikePdf(pdf)) {
			Responses.error(exchange, 422, "not_a_pdf", "The file is not a PDF");
			return null;
		}
		byte[] source = pdf;
		return () -> scoreAndRespond(exchange, source, resumeText, jobDescription);
	}

	/** What an ATS reads from the PDF: the content-stream text, as {@code /check} extracts it. */
	private String textOf(byte[] pdf) throws IOException {
		String text = PdfText.readStreamText(pdf, config.maxPages(), Deadline.after(config.checkTimeout()));
		if (text.strip().length() < MIN_TEXT_CHARS) {
			throw new CheckFailure(422, "no_text_layer", "The PDF has no readable text, so there is nothing to score");
		}
		return text;
	}

	private void scoreAndRespond(HttpExchange exchange, byte[] pdf, String resumeText, String jobDescription)
			throws IOException {
		long started = System.nanoTime();
		ObjectNode report;
		try {
			String resume = pdf == null ? resumeText : textOf(pdf);
			JsonNode answer = llm.completeJson(ScoringPrompt.build(resume, jobDescription));
			JsonNode results = answer.path("results");
			if (!results.isArray() || results.size() != PLATFORMS) {
				throw new CheckFailure(502, "bad_model_answer",
						"The model did not return the " + PLATFORMS + " platform scores");
			}
			report = (ObjectNode) answer;
			report.put("_model", llm.model());
			report.put("_resumeSource", pdf == null ? "text" : "pdf");
		}
		catch (IOException ex) {
			log.info("score failed code=unreadable_pdf");
			Responses.error(exchange, 422, "unreadable_pdf", "The PDF cannot be parsed");
			return;
		}
		catch (CheckFailure ex) {
			log.info("score failed code=" + ex.code());
			Responses.error(exchange, ex.status(), ex.code(), ex.getMessage());
			return;
		}
		catch (RuntimeException ex) {
			log.log(Level.SEVERE, "score error", ex);
			Responses.error(exchange, 500, "internal", "The scoring failed due to a worker error");
			return;
		}
		log.info("score ok model=" + llm.model() + " targeted=" + !jobDescription.isBlank()
				+ " millis=" + (System.nanoTime() - started) / 1_000_000);
		Responses.json(exchange, 200, report);
	}
}
