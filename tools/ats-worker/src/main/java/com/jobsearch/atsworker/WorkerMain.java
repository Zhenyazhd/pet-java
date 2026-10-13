package com.jobsearch.atsworker;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Starts the HTTP server: {@code POST /check}, {@code POST /score}, {@code GET /healthz}, {@code GET /readyz}. */
public final class WorkerMain {

	private static final Logger log = Logger.getLogger(WorkerMain.class.getName());
	/** Time a client gets to send its whole request (a 16 MB body included). */
	private static final int REQUEST_READ_SECONDS = 60;
	private static final int RESPONSE_MARGIN_SECONDS = 30;
	private static final int IDLE_SECONDS = 30;
	private static final int DRAIN_NOTICE_SECONDS = 5;

	private WorkerMain() {
	}

	public static void main(String[] args) throws IOException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tFT%1$tT %4$s %5$s%6$s%n");
		WorkerConfig config = WorkerConfig.fromEnv(System.getenv());
		limitServerTimes(config);
		AtomicBoolean draining = new AtomicBoolean(false);
		HttpServer server = createServer(config, draining);
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> drainAndStop(server, draining, config)));
		log.info("ats-worker listening port=" + config.port() + " concurrency=" + config.concurrency()
				+ " scoring=" + (config.llm().configured() ? config.llm().model() : "off"));
	}

	/**
	 * On SIGTERM: report not ready so the load balancer stops sending requests, give it a moment to notice, then wait
	 * for the calls in flight (up to the grace period) and stop at once if there are none. The platform must wait
	 * longer than the notice plus the grace period before it kills the process (compose: {@code stop_grace_period}).
	 */
	private static void drainAndStop(HttpServer server, AtomicBoolean draining, WorkerConfig config) {
		draining.set(true);
		try {
			Thread.sleep(DRAIN_NOTICE_SECONDS * 1000L);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		try {
			if (!PostJsonHandler.awaitIdle(config.shutdownGrace())) {
				log.warning("shutdown: requests still running after " + config.shutdownGrace().toSeconds() + "s, closing anyway");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		server.stop(0);
	}

	/**
	 * The JDK server waits for ever on a slow client unless told otherwise. These are read once, when its classes
	 * load, so they must be set before the first server is created. {@code maxRspTime} starts when the request is
	 * complete and covers the handler too, so it has to outlast the longest handler (a PDF check plus a model call).
	 */
	private static void limitServerTimes(WorkerConfig config) {
		long handlerSeconds = config.checkTimeout().toSeconds() + config.llm().timeout().toSeconds();
		setIfAbsent("sun.net.httpserver.maxReqTime", REQUEST_READ_SECONDS);
		setIfAbsent("sun.net.httpserver.maxRspTime", handlerSeconds + RESPONSE_MARGIN_SECONDS);
		setIfAbsent("sun.net.httpserver.idleInterval", IDLE_SECONDS);
	}

	/** An operator's {@code -D} on the command line wins. */
	private static void setIfAbsent(String key, long seconds) {
		if (System.getProperty(key) == null) {
			System.setProperty(key, String.valueOf(seconds));
		}
	}

	static HttpServer createServer(WorkerConfig config) throws IOException {
		return createServer(config, new AtomicBoolean(false));
	}

	static HttpServer createServer(WorkerConfig config, AtomicBoolean draining) throws IOException {
		// fail at startup, not on the first request, if the prompt resources are missing
		ScoringPrompt.build("startup check", "");
		HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.createContext("/check", new CheckHandler(new Checker(config.maxPages()), config));
		server.createContext("/score", new ScoreHandler(new LlmClient(config.llm()), config));
		server.createContext("/healthz", exchange -> {
			try (exchange) {
				Responses.json(exchange, 200, Map.of("status", "ok"));
			}
		});
		// nothing to warm up: ready as soon as it listens, until it is told to stop
		server.createContext("/readyz", exchange -> {
			try (exchange) {
				if (draining.get()) {
					Responses.error(exchange, 503, "draining", "The worker is shutting down");
				}
				else {
					Responses.json(exchange, 200, Map.of("status", "ok"));
				}
			}
		});
		return server;
	}
}
