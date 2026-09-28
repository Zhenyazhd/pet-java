package com.jobsearch.core_api.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class S3Config {

	@Bean
	public S3Client s3Client(AppProperties appProperties) {
		AppProperties.S3 s3 = appProperties.getS3();
		return S3Client.builder()
				.endpointOverride(URI.create(s3.getEndpoint()))
				.region(Region.of(s3.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())
				))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(s3.isPathStyleAccess())
						.build())
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.build();
	}
}
