package com.jobsearch.core_api.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	/** Bootstrap ADMIN email — must be set via {@code APP_ADMIN_EMAIL} (no hardcoded default). */
	private String adminEmail = "";
	/** If set and bootstrap admin has no password yet, hash this onto that account on startup. */
	private String bootstrapPassword = "";
	private boolean strictSecrets = false;
	/** Where the web app is reached from outside; the links in emails and the return from Google are built on it. */
	private String publicBaseUrl = "http://localhost:5173";
	private final Mail mail = new Mail();
	private final Google google = new Google();
	private final Cors cors = new Cors();
	private final AuthRateLimit authRateLimit = new AuthRateLimit();
	private final ExpensiveOpsRateLimit expensiveOpsRateLimit = new ExpensiveOpsRateLimit();
	private final OpenRouter openRouter = new OpenRouter();
	private final PdfWorker pdfWorker = new PdfWorker();
	private final AtsWorker atsWorker = new AtsWorker();
	private final JobList jobList = new JobList();

	public boolean isStrictSecrets() {
		return strictSecrets;
	}

	public void setStrictSecrets(boolean strictSecrets) {
		this.strictSecrets = strictSecrets;
	}

	public String getAdminEmail() {
		return adminEmail;
	}

	public void setAdminEmail(String adminEmail) {
		this.adminEmail = adminEmail;
	}

	public String getBootstrapPassword() {
		return bootstrapPassword;
	}

	public void setBootstrapPassword(String bootstrapPassword) {
		this.bootstrapPassword = bootstrapPassword;
	}

	public String getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}

	public Mail getMail() {
		return mail;
	}

	public Google getGoogle() {
		return google;
	}

	public Cors getCors() {
		return cors;
	}

	public AuthRateLimit getAuthRateLimit() {
		return authRateLimit;
	}

	public ExpensiveOpsRateLimit getExpensiveOpsRateLimit() {
		return expensiveOpsRateLimit;
	}

	public OpenRouter getOpenRouter() {
		return openRouter;
	}

	public PdfWorker getPdfWorker() {
		return pdfWorker;
	}

	public AtsWorker getAtsWorker() {
		return atsWorker;
	}

	public JobList getJobList() {
		return jobList;
	}

	/** The job-list service (tools/job-list) that finds and ranks vacancies. Without a token the feature is off. */
	public static class JobList {
		private String baseUrl = "http://127.0.0.1:8100";
		/** The client bearer token job-list issued for core-api; blank switches the feature off. */
		private String token = "";
		private int connectTimeoutSeconds = 3;
		private int readTimeoutSeconds = 15;

		public boolean isConfigured() {
			return token != null && !token.isBlank();
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getToken() {
			return token;
		}

		public void setToken(String token) {
			this.token = token;
		}

		public int getConnectTimeoutSeconds() {
			return connectTimeoutSeconds;
		}

		public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
			this.connectTimeoutSeconds = connectTimeoutSeconds;
		}

		public int getReadTimeoutSeconds() {
			return readTimeoutSeconds;
		}

		public void setReadTimeoutSeconds(int readTimeoutSeconds) {
			this.readTimeoutSeconds = readTimeoutSeconds;
		}
	}

	public static class ExpensiveOpsRateLimit {
		/**
		 * Max combined POST /api/ai/**, /api/vacancies/import, /api/resume/compile,
		 * /api/resume/readability and /api/job-search/runs (full or rank-only) calls
		 * per authenticated user per minute ({@code 0} = disabled).
		 */
		private int perUserPerMinute = 10;

		public int getPerUserPerMinute() {
			return perUserPerMinute;
		}

		public void setPerUserPerMinute(int perUserPerMinute) {
			this.perUserPerMinute = perUserPerMinute;
		}
	}

	public static class AuthRateLimit {
		/** Max POST /api/auth/login attempts per client IP per minute (0 = disabled). */
		private int loginPerMinute = 10;
		/** Max POST /api/auth/register attempts per client IP per minute (0 = disabled). */
		private int registerPerMinute = 5;

		public int getLoginPerMinute() {
			return loginPerMinute;
		}

		public void setLoginPerMinute(int loginPerMinute) {
			this.loginPerMinute = loginPerMinute;
		}

		public int getRegisterPerMinute() {
			return registerPerMinute;
		}

		public void setRegisterPerMinute(int registerPerMinute) {
			this.registerPerMinute = registerPerMinute;
		}
	}

	public static class Cors {
		/**
		 * Comma-separated browser origins for credentialed CORS (SPA).
		 * Example: {@code http://localhost:5173,https://app.example.com}
		 */
		private String allowedOrigins = "http://localhost:5173";

		public String getAllowedOrigins() {
			return allowedOrigins;
		}

		public void setAllowedOrigins(String allowedOrigins) {
			this.allowedOrigins = allowedOrigins;
		}

		public List<String> allowedOriginList() {
			if (allowedOrigins == null || allowedOrigins.isBlank()) {
				return List.of();
			}
			return Arrays.stream(allowedOrigins.split(","))
					.map(String::strip)
					.filter(origin -> !origin.isEmpty())
					.toList();
		}
	}

	public static class PdfWorker {
		/** Base URL of tools/pdf-worker. */
		private String baseUrl = "http://127.0.0.1:8090";
		/** Shared secret sent as X-Worker-Token; required (core-api refuses to start without it). */
		private String token = "";
		private int connectTimeoutSeconds = 3;
		/** Must be longer than the worker's own compile timeout (60s by default). */
		private int readTimeoutSeconds = 75;
		/** Read timeout of the quick compile the AI chat makes to measure page fit; the chat must not wait for long. */
		private int layoutReadTimeoutSeconds = 6;

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getToken() {
			return token;
		}

		public void setToken(String token) {
			this.token = token;
		}

		public int getConnectTimeoutSeconds() {
			return connectTimeoutSeconds;
		}

		public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
			this.connectTimeoutSeconds = connectTimeoutSeconds;
		}

		public int getReadTimeoutSeconds() {
			return readTimeoutSeconds;
		}

		public void setReadTimeoutSeconds(int readTimeoutSeconds) {
			this.readTimeoutSeconds = readTimeoutSeconds;
		}

		public int getLayoutReadTimeoutSeconds() {
			return layoutReadTimeoutSeconds;
		}

		public void setLayoutReadTimeoutSeconds(int layoutReadTimeoutSeconds) {
			this.layoutReadTimeoutSeconds = layoutReadTimeoutSeconds;
		}
	}

	public static class AtsWorker {
		/**
		 * Whether the ATS features are offered: the PDF readability check and the vacancy match score. Off by
		 * default: they need the separate tools/ats-worker service, which must be reachable only from core-api.
		 */
		private boolean enabled = false;
		/** Base URL of tools/ats-worker. */
		private String baseUrl = "http://127.0.0.1:8091";
		/** Shared secret sent as X-Worker-Token; required while the check is enabled. */
		private String token = "";
		private int connectTimeoutSeconds = 3;
		/** Must be longer than the worker's own check timeout (20s by default). */
		private int readTimeoutSeconds = 30;
		/**
		 * Read timeout for POST /score, a model call; must be longer than the worker's SCORE_TIMEOUT_SECONDS (90).
		 * It covers the wait for the response only: if it expires the worker still finishes the call (and its cost).
		 */
		private int scoreTimeoutSeconds = 100;
		/** Vacancy matches one user may queue in a rolling 24 hours; each one is a paid model call. 0 = no limit. */
		private int maxMatchesPerUserPerDay = 20;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getToken() {
			return token;
		}

		public void setToken(String token) {
			this.token = token;
		}

		public int getConnectTimeoutSeconds() {
			return connectTimeoutSeconds;
		}

		public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
			this.connectTimeoutSeconds = connectTimeoutSeconds;
		}

		public int getReadTimeoutSeconds() {
			return readTimeoutSeconds;
		}

		public void setReadTimeoutSeconds(int readTimeoutSeconds) {
			this.readTimeoutSeconds = readTimeoutSeconds;
		}

		public int getScoreTimeoutSeconds() {
			return scoreTimeoutSeconds;
		}

		public void setScoreTimeoutSeconds(int scoreTimeoutSeconds) {
			this.scoreTimeoutSeconds = scoreTimeoutSeconds;
		}

		public int getMaxMatchesPerUserPerDay() {
			return maxMatchesPerUserPerDay;
		}

		public void setMaxMatchesPerUserPerDay(int maxMatchesPerUserPerDay) {
			this.maxMatchesPerUserPerDay = maxMatchesPerUserPerDay;
		}
	}

	public static class OpenRouter {
		private String apiKey = "";
		private String baseUrl = "https://openrouter.ai/api/v1";
		private String model = "openai/gpt-4o-mini";
		private String siteUrl = "http://localhost:5173";
		private String siteName = "Job Search";
		private int connectTimeoutSeconds = 5;
		/** How long to wait for a model's answer; long prompts on slow models need tens of seconds. */
		private int readTimeoutSeconds = 90;
		/** OpenRouter calls (AI chat and vacancy import) one instance sends at once; more are answered "busy". */
		private int maxConcurrentRequests = 20;
		/** Caps one answer's length and cost; a full-resume edit in the AI chat needs several thousand tokens. */
		private int maxTokens = 8000;

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		public String getSiteUrl() {
			return siteUrl;
		}

		public void setSiteUrl(String siteUrl) {
			this.siteUrl = siteUrl;
		}

		public String getSiteName() {
			return siteName;
		}

		public void setSiteName(String siteName) {
			this.siteName = siteName;
		}

		public int getConnectTimeoutSeconds() {
			return connectTimeoutSeconds;
		}

		public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
			this.connectTimeoutSeconds = connectTimeoutSeconds;
		}

		public int getReadTimeoutSeconds() {
			return readTimeoutSeconds;
		}

		public void setReadTimeoutSeconds(int readTimeoutSeconds) {
			this.readTimeoutSeconds = readTimeoutSeconds;
		}

		public int getMaxConcurrentRequests() {
			return maxConcurrentRequests;
		}

		public void setMaxConcurrentRequests(int maxConcurrentRequests) {
			this.maxConcurrentRequests = maxConcurrentRequests;
		}

		public int getMaxTokens() {
			return maxTokens;
		}

		public void setMaxTokens(int maxTokens) {
			this.maxTokens = maxTokens;
		}
	}

	public static class Mail {

		/** The From address of every mail we send; the SMTP server itself is set with {@code spring.mail.*}. */
		private String from = "";

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}
	}

	/** Sign-in with Google; off while {@code clientId} is blank. */
	public static class Google {

		private String clientId = "";
		private String clientSecret = "";

		public String getClientId() {
			return clientId;
		}

		public void setClientId(String clientId) {
			this.clientId = clientId;
		}

		public String getClientSecret() {
			return clientSecret;
		}

		public void setClientSecret(String clientSecret) {
			this.clientSecret = clientSecret;
		}

		public boolean isEnabled() {
			return clientId != null && !clientId.isBlank();
		}
	}
}
