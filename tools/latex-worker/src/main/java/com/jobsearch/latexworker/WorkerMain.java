package com.jobsearch.latexworker;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Starts the HTTP server: {@code POST /compile}, {@code GET /healthz}, {@code GET /readyz}. */
public final class WorkerMain {

	private static final Logger log = Logger.getLogger(WorkerMain.class.getName());

	private WorkerMain() {
	}

	public static void main(String[] args) throws IOException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tFT%1$tT %4$s %5$s%6$s%n");
		WorkerConfig config = WorkerConfig.fromEnv(System.getenv());
		Compiler compiler = new Compiler(config.tectonicBin(), config.compileTimeout(), config.maxPdfBytes());
		AtomicBoolean ready = new AtomicBoolean(false);

		HttpServer server = createServer(config, compiler, ready);
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(5)));
		log.info("latex-worker listening port=" + config.port() + " concurrency=" + config.concurrency());

		ready.set(warmUp(compiler));
	}

	/** {@code /readyz} reports {@code ready}; the caller decides when to set it. */
	static HttpServer createServer(WorkerConfig config, Compiler compiler, AtomicBoolean ready) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.createContext("/compile", new CompileHandler(compiler, config));
		server.createContext("/healthz", exchange -> {
			try (exchange) {
				Responses.json(exchange, 200, Map.of("status", "ok"));
			}
		});
		server.createContext("/readyz", exchange -> {
			try (exchange) {
				if (ready.get()) {
					Responses.json(exchange, 200, Map.of("status", "ready"));
				}
				else {
					Responses.error(exchange, 503, "not_ready", "Warm-up compile has not succeeded");
				}
			}
		});
		return server;
	}

	/** Proves tectonic runs and its cache holds every package the resume template needs. */
	private static boolean warmUp(Compiler compiler) {
		try (InputStream in = WorkerMain.class.getResourceAsStream("/warmup.tex")) {
			String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			CompileResult result = compiler.compile(source);
			if (result instanceof CompileResult.Ok) {
				log.info("warm-up ok");
				return true;
			}
			log.severe("warm-up failed result=" + result.getClass().getSimpleName());
			if (result instanceof CompileResult.Failed(String compileLog)) {
				log.severe(compileLog);
			}
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.log(Level.SEVERE, "warm-up interrupted", ex);
			return false;
		}
		catch (IOException | RuntimeException ex) {
			log.log(Level.SEVERE, "warm-up failed", ex);
			return false;
		}
	}
}
