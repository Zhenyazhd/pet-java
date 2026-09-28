package com.jobsearch.core_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private long currentUserId = 1L;
	private final S3 s3 = new S3();
	private final OpenRouter openRouter = new OpenRouter();
	private final AtsScreener atsScreener = new AtsScreener();

	public long getCurrentUserId() {
		return currentUserId;
	}

	public void setCurrentUserId(long currentUserId) {
		this.currentUserId = currentUserId;
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

	public static class OpenRouter {
		private String apiKey = "";
		private String baseUrl = "https://openrouter.ai/api/v1";
		private String model = "openai/gpt-4o-mini";
		private String siteUrl = "http://localhost:5173";
		private String siteName = "Job Search";

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
