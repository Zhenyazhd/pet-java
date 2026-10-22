package com.jobsearch.core_api.jobsearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A small in-memory job-list: scopes by bearer token only (like the real one, no owner check on ids), keeps
 * profiles, runs and matches, and records every request so tests can see what core-api sent.
 */
final class JobListStub {

	static final String TOKEN = "stub-token";
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern PROFILE = Pattern.compile("/v1/profiles/(\\d+)");
	private static final Pattern RUNS = Pattern.compile("/v1/profiles/(\\d+)/runs");
	private static final Pattern UNRANKED = Pattern.compile("/v1/profiles/(\\d+)/unranked");
	private static final Pattern MATCHES = Pattern.compile("/v1/profiles/(\\d+)/matches");
	private static final Pattern ACK = Pattern.compile("/v1/matches/(\\d+)/ack");

	record Profile(long id, String ownerRef, JsonNode body) {
	}

	record Match(long id, long profileId, int score, boolean[] delivered) {
	}

	final HttpServer server;
	final List<String> requests = new CopyOnWriteArrayList<>();
	final List<Profile> profiles = new CopyOnWriteArrayList<>();
	final List<Match> matches = new CopyOnWriteArrayList<>();
	final List<String> acked = new CopyOnWriteArrayList<>();
	final Map<Long, List<Long>> runIds = new LinkedHashMap<>();
	/** Unranked postings per profile id; a rank-only run needs at least one and clears it. */
	final Map<Long, Integer> unranked = new java.util.concurrent.ConcurrentHashMap<>();
	/** Run ids started in rank mode. */
	final java.util.Set<Long> rankRuns = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** When true, a started run stays active: the same mode returns it, the other mode is 409 (like job-list). */
	volatile boolean dedupe;
	private final Map<Long, long[]> active = new java.util.concurrent.ConcurrentHashMap<>();
	/** When set, GET unranked answers this status instead of the count. */
	volatile int unrankedStatus = 0;
	/** When true, runs come back without the {@code mode} field (an older job-list). */
	volatile boolean omitMode;
	/** When set, POST runs answers this status and body instead of creating a run. */
	volatile int runStatus = 0;
	volatile String runBody = "";
	volatile String lastToken;
	/** GET /v1/profiles ignores ownerRef and lists every profile (a loose filter). */
	volatile boolean ignoreOwnerRef;
	/** The next n profile lists come back empty although profiles exist (a parallel create the caller has not seen). */
	volatile int hideProfileLists;
	private long nextId = 100;

	JobListStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
	}

	String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void stop() {
		server.stop(0);
	}

	synchronized void reset() {
		requests.clear();
		profiles.clear();
		matches.clear();
		acked.clear();
		runIds.clear();
		runStatus = 0;
		runBody = "";
		unranked.clear();
		rankRuns.clear();
		unrankedStatus = 0;
		omitMode = false;
		dedupe = false;
		active.clear();
		ignoreOwnerRef = false;
		hideProfileLists = 0;
	}

	synchronized Match addMatch(long profileId, int score, boolean delivered) {
		Match match = new Match(nextId++, profileId, score, new boolean[] { delivered });
		matches.add(match);
		return match;
	}

	synchronized Profile addProfile(String ownerRef) {
		Profile profile = new Profile(nextId++, ownerRef, JSON.createObjectNode().put("name", "Search"));
		profiles.add(profile);
		return profile;
	}

	private synchronized void handle(HttpExchange ex) throws IOException {
		String path = ex.getRequestURI().getRawPath();
		String query = ex.getRequestURI().getRawQuery();
		String method = ex.getRequestMethod();
		requests.add(method + " " + path + (query == null ? "" : "?" + query));
		String auth = ex.getRequestHeaders().getFirst("Authorization");
		lastToken = auth;
		if (!("Bearer " + TOKEN).equals(auth)) {
			reply(ex, 401, "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"A valid bearer token is required\"}");
			return;
		}
		String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Map<String, String> params = params(query);
		Matcher m;
		if (path.equals("/v1/profiles") && method.equals("GET")) {
			List<Object> out = new ArrayList<>();
			boolean hidden = hideProfileLists > 0;
			if (hidden) {
				hideProfileLists--;
			}
			for (Profile p : hidden ? List.<Profile>of() : profiles) {
				if (ignoreOwnerRef || !params.containsKey("ownerRef") || params.get("ownerRef").equals(p.ownerRef())) {
					out.add(profileJson(p));
				}
			}
			reply(ex, 200, JSON.writeValueAsString(out));
		}
		else if (path.equals("/v1/profiles") && method.equals("POST")) {
			JsonNode in = JSON.readTree(body);
			if (profiles.stream().anyMatch(x -> x.ownerRef().equals(in.path("ownerRef").asString())
					&& x.body().path("name").asString().equals(in.path("name").asString()))) {
				reply(ex, 400, error(400, "Owner already has a profile named 'Search'"));
				return;
			}
			Profile p = new Profile(nextId++, in.path("ownerRef").asString(), in);
			profiles.add(p);
			reply(ex, 201, JSON.writeValueAsString(profileJson(p)));
		}
		else if ((m = PROFILE.matcher(path)).matches() && method.equals("PUT")) {
			long id = Long.parseLong(m.group(1));
			Profile old = profiles.stream().filter(p -> p.id() == id).findFirst().orElse(null);
			if (old == null) {
				reply(ex, 404, error(404, "Profile not found: " + id));
				return;
			}
			Profile updated = new Profile(id, old.ownerRef(), JSON.readTree(body));
			profiles.set(profiles.indexOf(old), updated);
			reply(ex, 200, JSON.writeValueAsString(profileJson(updated)));
		}
		else if ((m = UNRANKED.matcher(path)).matches() && method.equals("GET")) {
			if (unrankedStatus != 0) {
				reply(ex, unrankedStatus, error(unrankedStatus, "unranked failed"));
				return;
			}
			long unrankedId = Long.parseLong(m.group(1));
			if (profiles.stream().noneMatch(p -> p.id() == unrankedId)) {
				reply(ex, 404, error(404, "Profile not found: " + unrankedId));
				return;
			}
			reply(ex, 200, "{\"count\":" + unranked.getOrDefault(Long.parseLong(m.group(1)), 0) + "}");
		}
		else if ((m = RUNS.matcher(path)).matches()) {
			long id = Long.parseLong(m.group(1));
			if (method.equals("POST")) {
				if (runStatus != 0) {
					reply(ex, runStatus, runBody);
					return;
				}
				boolean rank = "rank".equals(params.get("mode"));
				long[] current = dedupe ? active.get(id) : null;
				if (current != null) {
					if ((current[1] == 1) == rank) {
						reply(ex, 202, run(current[0], "QUEUED"));
					}
					else {
						reply(ex, 409, error(409, current[1] == 1 ? "A ranking run is already in progress for this profile"
								: "A full run is already in progress for this profile"));
					}
					return;
				}
				if (rank && unranked.getOrDefault(id, 0) == 0) {
					reply(ex, 409, error(409, "Nothing to rank"));
					return;
				}
				long runId = nextId++;
				if (rank) {
					rankRuns.add(runId);
				}
				if (dedupe) {
					active.put(id, new long[] { runId, rank ? 1 : 0 });
				}
				runIds.computeIfAbsent(id, k -> new ArrayList<>()).add(runId);
				reply(ex, 202, run(runId, "QUEUED"));
			}
			else {
				List<Long> ids = runIds.getOrDefault(id, List.of());
				// newest first like the real one, but core-api must not depend on the order
				List<String> out = new ArrayList<>();
				for (Long runId : ids) {
					out.add(run(runId, "SUCCEEDED"));
				}
				reply(ex, 200, "[" + String.join(",", out) + "]");
			}
		}
		else if ((m = MATCHES.matcher(path)).matches() && method.equals("GET")) {
			long id = Long.parseLong(m.group(1));
			boolean delivered = Boolean.parseBoolean(params.getOrDefault("delivered", "false"));
			int limit = Integer.parseInt(params.getOrDefault("limit", "20"));
			int from;
			try {
				from = params.containsKey("cursor") ? Integer.parseInt(params.get("cursor").substring(1)) : 0;
			}
			catch (NumberFormatException e) {
				reply(ex, 400, error(400, "cursor must be the nextCursor of a previous page"));
				return;
			}
			List<Match> all = matches.stream().filter(x -> x.profileId() == id && x.delivered()[0] == delivered
					&& x.score() >= Integer.parseInt(params.getOrDefault("minScore", "0"))).toList();
			List<Match> page = all.subList(Math.min(from, all.size()), Math.min(from + limit, all.size()));
			String next = from + limit < all.size() ? "\"c" + (from + limit) + "\"" : "null";
			List<String> items = page.stream().map(JobListStub::matchJson).toList();
			reply(ex, 200, "{\"items\":[" + String.join(",", items) + "],\"nextCursor\":" + next + "}");
		}
		else if ((m = ACK.matcher(path)).matches() && method.equals("POST")) {
			long id = Long.parseLong(m.group(1));
			Match match = matches.stream().filter(x -> x.id() == id).findFirst().orElse(null);
			if (match == null) {
				reply(ex, 404, error(404, "Match not found: " + id));
				return;
			}
			match.delivered()[0] = true;
			acked.add(String.valueOf(id));
			ex.sendResponseHeaders(204, -1);
			ex.close();
		}
		else {
			reply(ex, 404, error(404, "no route"));
		}
	}

	private static Map<String, Object> profileJson(Profile p) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("id", p.id());
		out.put("ownerRef", p.ownerRef());
		out.put("name", p.body().path("name").asString());
		out.put("expectations", p.body().path("expectations").asString(""));
		out.put("roles", p.body().path("roles"));
		out.put("regions", p.body().path("regions").isMissingNode() ? List.of() : p.body().path("regions"));
		out.put("sources", List.of("LINKEDIN"));
		out.put("minScore", p.body().path("minScore").asInt(70));
		out.put("createdAt", "2026-10-19T10:00:00Z");
		out.put("updatedAt", "2026-10-19T10:00:00Z");
		return out;
	}

	private String run(long id, String status) {
		boolean rank = rankRuns.contains(id);
		return "{\"id\":" + id + ",\"status\":\"" + status + "\",\"createdAt\":\"2026-10-19T10:00:00Z\",\"startedAt\":null,"
				+ "\"finishedAt\":null,\"fetched\":3,\"newJobs\":2,\"ranked\":1,\"costUsd\":0.01,\"error\":null,"
				+ (omitMode ? "" : "\"mode\":\"" + (rank ? "RANK_ONLY" : "FULL") + "\",")
				+ (rank ? "\"sources\":[]}" : "\"sources\":[{\"source\":\"LINKEDIN\",\"status\":\"SUCCEEDED\",\"apifyRunId\":\"secret-apify-id\",\"fetched\":3,\"newJobs\":2,\"dropped\":0,\"costUsd\":0.01,\"error\":null}]}");
	}

	private static String matchJson(Match m) {
		return "{\"id\":" + m.id() + ",\"score\":" + m.score() + ",\"reason\":\"fits\",\"rankedAt\":\"2026-10-19T10:00:00Z\","
				+ "\"deliveredAt\":" + (m.delivered()[0] ? "\"2026-10-19T11:00:00Z\"" : "null") + ","
				+ "\"job\":{\"id\":" + (m.id() + 1000) + ",\"source\":\"LINKEDIN\",\"title\":\"Java dev\",\"company\":\"Acme\","
				+ "\"location\":\"Paris\",\"remote\":\"HYBRID\",\"contractType\":\"FULL_TIME\",\"salary\":null,"
				+ "\"url\":\"https://example.com/j\",\"postedAt\":null,\"skills\":[\"Java\"],\"snippet\":\"text\"}}";
	}

	private static String error(int status, String message) {
		return "{\"status\":" + status + ",\"error\":\"x\",\"message\":\"" + message + "\"}";
	}

	private static Map<String, String> params(String query) {
		Map<String, String> out = new LinkedHashMap<>();
		if (query != null) {
			for (String pair : query.split("&")) {
				int i = pair.indexOf('=');
				if (i < 0) {
					out.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
					continue;
				}
				out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
						URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
			}
		}
		return out;
	}

	private static void reply(HttpExchange ex, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(status, bytes.length);
		ex.getResponseBody().write(bytes);
		ex.close();
	}
}
