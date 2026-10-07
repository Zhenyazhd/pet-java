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
	private final Cors cors = new Cors();
	private final AuthRateLimit authRateLimit = new AuthRateLimit();
	private final ExpensiveOpsRateLimit expensiveOpsRateLimit = new ExpensiveOpsRateLimit();
	private final S3 s3 = new S3();
	private final OpenRouter openRouter = new OpenRouter();
	private final AtsScreener atsScreener = new AtsScreener();
	private final LatexWorker latexWorker = new LatexWorker();

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

	public Cors getCors() {
		return cors;
	}

	public AuthRateLimit getAuthRateLimit() {
		return authRateLimit;
	}

	public ExpensiveOpsRateLimit getExpensiveOpsRateLimit() {
		return expensiveOpsRateLimit;
	}

	public S3 getS3() {
		return s3;
	}

	public OpenRouter getOpenRouter() {
		return openRouter;
	}

	public AtsScreener getAtsScreener() {
		return atsScreener;
	}

	public LatexWorker getLatexWorker() {
		return latexWorker;
	}

	public static class ExpensiveOpsRateLimit {
		/**
		 * Max combined POST /api/ai/**, /api/vacancies/import and /api/resume/compile calls
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
		/**
		 * When true, read client IP from {@code X-Forwarded-For} / {@code X-Real-IP}
		 * only if {@code request.getRemoteAddr()} is listed in {@link #trustedProxies}.
		 * Keep false unless the API sits behind a reverse proxy (Vite/nginx) that sets those headers.
		 * Enabling this without a trusted proxy lets clients spoof IPs and bypass the limit.
		 */
		private boolean trustForwardedHeaders = false;
		/** Comma-separated proxy remote addresses allowed to supply forwarded client IPs. */
		private String trustedProxies = "127.0.0.1,::1";

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

		public boolean isTrustForwardedHeaders() {
			return trustForwardedHeaders;
		}

		public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
			this.trustForwardedHeaders = trustForwardedHeaders;
		}

		public String getTrustedProxies() {
			return trustedProxies;
		}

		public void setTrustedProxies(String trustedProxies) {
			this.trustedProxies = trustedProxies;
		}

		public List<String> trustedProxyList() {
			if (trustedProxies == null || trustedProxies.isBlank()) {
				return List.of();
			}
			return Arrays.stream(trustedProxies.split(","))
					.map(String::strip)
					.filter(ip -> !ip.isEmpty())
					.toList();
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

	public static class AtsScreener {
		/** Base URL of tools/ats-screener Vite/SvelteKit app (e.g. http://127.0.0.1:5174). */
		private String baseUrl = "http://127.0.0.1:5174";
		/** Read timeout for /api/analyze — full Gemini scoring often exceeds 90s. */
		private int timeoutSeconds = 180;

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public int getTimeoutSeconds() {
			return timeoutSeconds;
		}

		public void setTimeoutSeconds(int timeoutSeconds) {
			this.timeoutSeconds = timeoutSeconds;
		}
	}

	public static class LatexWorker {
		/** Base URL of tools/latex-worker. */
		private String baseUrl = "http://127.0.0.1:8090";
		/** Shared secret sent as X-Worker-Token; required (core-api refuses to start without it). */
		private String token = "";
		private int connectTimeoutSeconds = 3;
		/** Must be longer than the worker's own compile timeout (60s by default). */
		private int readTimeoutSeconds = 75;
		/** Jobs this instance sends to workers at once. */
		private int dispatchConcurrency = 4;
		/** Tries per job before it fails with worker_unavailable; a busy worker does not count. */
		private int maxAttempts = 3;
		/** A job still queued after this long fails with queue_timeout — the user has stopped waiting. */
		private int queueMaxAgeSeconds = 300;

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

		public int getDispatchConcurrency() {
			return dispatchConcurrency;
		}

		public void setDispatchConcurrency(int dispatchConcurrency) {
			this.dispatchConcurrency = dispatchConcurrency;
		}

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}

		public int getQueueMaxAgeSeconds() {
			return queueMaxAgeSeconds;
		}

		public void setQueueMaxAgeSeconds(int queueMaxAgeSeconds) {
			this.queueMaxAgeSeconds = queueMaxAgeSeconds;
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
		/** AI chat calls ({@code OpenRouterClient.complete}) one instance sends at once; more are answered "busy". */
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

	public static class S3 {
		private String endpoint = "http://localhost:9090";
		private String region = "us-east-1";
		private String accessKey = "local";
		private String secretKey = "local";
		private String bucket = "job-search-cvs";
		private boolean pathStyleAccess = true;

		public String getEndpoint() {
			return endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public String getAccessKey() {
			return accessKey;
		}

		public void setAccessKey(String accessKey) {
			this.accessKey = accessKey;
		}

		public String getSecretKey() {
			return secretKey;
		}

		public void setSecretKey(String secretKey) {
			this.secretKey = secretKey;
		}

		public String getBucket() {
			return bucket;
		}

		public void setBucket(String bucket) {
			this.bucket = bucket;
		}

		public boolean isPathStyleAccess() {
			return pathStyleAccess;
		}

		public void setPathStyleAccess(boolean pathStyleAccess) {
			this.pathStyleAccess = pathStyleAccess;
		}
	}
}
