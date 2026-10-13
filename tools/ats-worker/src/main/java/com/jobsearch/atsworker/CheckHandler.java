package com.jobsearch.atsworker;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;

/**
 * {@code POST /check}: {@code {"pdfBase64": "...", "expected": "<plain text, optional>"}} in, a {@link Report} out.
 * Resume content is never logged.
 */
final class CheckHandler extends PostJsonHandler {

	private static final Logger log = Logger.getLogger(CheckHandler.class.getName());

	private final Checker checker;
	private final WorkerConfig config;

	CheckHandler(Checker checker, WorkerConfig config) {
		super("check", config.token(), config.maxRequestBytes(), config.concurrency(), 1);
		this.checker = checker;
		this.config = config;
	}

	@Override
	protected String usage() {
		return "Expected JSON {\"pdfBase64\": \"<base64 PDF>\", \"expected\": \"<text>\"}";
	}

	@Override
	protected Work prepare(HttpExchange exchange, JsonNode json, int bodyBytes) throws IOException {
		byte[] pdf;
		try {
			pdf = Base64.getDecoder().decode(json.path("pdfBase64").asString(""));
		}
		catch (IllegalArgumentException ex) {
			badRequest(exchange);
			return null;
		}
		if (!PdfText.looksLikePdf(pdf)) {
			Responses.error(exchange, 422, "not_a_pdf", "The file is not a PDF");
			return null;
		}
		String expected = json.path("expected").asString("");
		return () -> checkAndRespond(exchange, pdf, expected);
	}

	private void checkAndRespond(HttpExchange exchange, byte[] pdf, String expected) throws IOException {
		long started = System.nanoTime();
		Report report;
		try {
			report = checker.check(pdf, expected, Deadline.after(config.checkTimeout()));
		}
		catch (CheckFailure ex) {
			log.info("check failed code=" + ex.code());
			Responses.error(exchange, ex.status(), ex.code(), ex.getMessage());
			return;
		}
		catch (IOException ex) {
			log.info("check failed code=unreadable_pdf");
			Responses.error(exchange, 422, "unreadable_pdf", "The PDF cannot be parsed");
			return;
		}
		catch (RuntimeException ex) {
			log.log(Level.SEVERE, "check error", ex);
			Responses.error(exchange, 500, "internal", "The check failed due to a worker error");
			return;
		}
		log.info("check ok verdict=" + report.verdict() + " score=" + report.score() + " pages=" + report.pages()
				+ " pdfBytes=" + pdf.length + " millis=" + (System.nanoTime() - started) / 1_000_000);
		// outside the try: a client that went away must not be answered a second time as "unreadable"
		Responses.json(exchange, 200, report);
	}
}
