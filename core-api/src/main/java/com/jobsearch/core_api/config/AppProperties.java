package com.jobsearch.core_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private long currentUserId = 1L;
	private final S3 s3 = new S3();

	public long getCurrentUserId() {
		return currentUserId;
	}

	public void setCurrentUserId(long currentUserId) {
		this.currentUserId = currentUserId;
	}

	public S3 getS3() {
		return s3;
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
