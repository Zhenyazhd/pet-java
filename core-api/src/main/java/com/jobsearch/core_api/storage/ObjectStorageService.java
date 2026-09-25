package com.jobsearch.core_api.storage;

import com.jobsearch.core_api.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3-compatible object storage for uploaded CV files. */
@Service
public class ObjectStorageService {

	private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

	private final S3Client s3Client;
	private final AppProperties appProperties;

	public ObjectStorageService(S3Client s3Client, AppProperties appProperties) {
		this.s3Client = s3Client;
		this.appProperties = appProperties;
	}

	public StoredObject upload(long userId, MultipartFile file) {
		String originalFilename = file.getOriginalFilename() == null ? "cv.bin" : file.getOriginalFilename();
		String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
		String storageKey = "users/%d/cv/%s-%s".formatted(userId, UUID.randomUUID(), sanitize(originalFilename));

		try (InputStream inputStream = file.getInputStream()) {
			s3Client.putObject(
					PutObjectRequest.builder()
							.bucket(appProperties.getS3().getBucket())
							.key(storageKey)
							.contentType(contentType)
							.contentLength(file.getSize())
							.build(),
					RequestBody.fromInputStream(inputStream, file.getSize())
			);
		}
		catch (IOException | S3Exception ex) {
			log.error("S3 upload failed key={}", storageKey, ex);
			throw new IllegalStateException("Failed to upload file to object storage", ex);
		}

		log.info("Uploaded object key={} size={}", storageKey, file.getSize());
		return new StoredObject(storageKey, originalFilename, contentType, file.getSize());
	}

	public InputStream download(String storageKey) {
		log.debug("Downloading object key={}", storageKey);
		return s3Client.getObject(GetObjectRequest.builder()
				.bucket(appProperties.getS3().getBucket())
				.key(storageKey)
				.build());
	}

	public void delete(String storageKey) {
		log.info("Deleting object key={}", storageKey);
		s3Client.deleteObject(DeleteObjectRequest.builder()
				.bucket(appProperties.getS3().getBucket())
				.key(storageKey)
				.build());
	}

	private static String sanitize(String filename) {
		return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	public record StoredObject(
			String storageKey,
			String originalFilename,
			String contentType,
			long sizeBytes
	) {
	}
}
