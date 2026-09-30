package com.jobsearch.core_api.cv;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.cv.CvDtos.ApplicationCvResponse;
import com.jobsearch.core_api.cv.CvDtos.CvVersionResponse;
import com.jobsearch.core_api.cv.CvDtos.SendCvRequest;
import com.jobsearch.core_api.jobapplication.JobApplicationRepository;
import com.jobsearch.core_api.storage.ObjectStorageService;
import com.jobsearch.core_api.storage.ObjectStorageService.StoredObject;
import com.jobsearch.core_api.vacancy.Vacancy;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** CV file versions in S3 and sendings linked to vacancies. */
@Service
@Transactional
public class CvService {

	private static final Logger log = LoggerFactory.getLogger(CvService.class);

	static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
	static final int MAX_LABEL_LENGTH = 255;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			".pdf",
			".doc",
			".docx",
			".tex"
	);

	private final CurrentUserService currentUserService;
	private final ObjectStorageService objectStorageService;
	private final CvVersionRepository cvVersionRepository;
	private final ApplicationCvRepository applicationCvRepository;
	private final VacancyRepository vacancyRepository;
	private final JobApplicationRepository jobApplicationRepository;

	public CvService(
			CurrentUserService currentUserService,
			ObjectStorageService objectStorageService,
			CvVersionRepository cvVersionRepository,
			ApplicationCvRepository applicationCvRepository,
			VacancyRepository vacancyRepository,
			JobApplicationRepository jobApplicationRepository
	) {
		this.currentUserService = currentUserService;
		this.objectStorageService = objectStorageService;
		this.cvVersionRepository = cvVersionRepository;
		this.applicationCvRepository = applicationCvRepository;
		this.vacancyRepository = vacancyRepository;
		this.jobApplicationRepository = jobApplicationRepository;
	}

	@Transactional(readOnly = true)
	public List<CvVersionResponse> listVersions() {
		return cvVersionRepository.findByUserIdOrderByCreatedAtDesc(currentUserId()).stream()
				.map(this::toVersionResponse)
				.toList();
	}

	public CvVersionResponse upload(String label, MultipartFile file) {
		String normalizedLabel = requireLabel(label);
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is required");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new IllegalArgumentException("File exceeds maximum size of 10 MB");
		}
		if (!isAllowedFile(file)) {
			throw new IllegalArgumentException("Only PDF, Word (.doc/.docx) or TeX (.tex) files are allowed");
		}

		StoredObject stored = objectStorageService.upload(currentUserId(), file);
		try {
			CvVersion version = new CvVersion();
			version.setUserId(currentUserId());
			version.setLabel(normalizedLabel);
			version.setOriginalFilename(stored.originalFilename());
			version.setContentType(resolveContentType(file));
			version.setStorageKey(stored.storageKey());
			version.setSizeBytes(stored.sizeBytes());
			CvVersion saved = cvVersionRepository.saveAndFlush(version);
			log.info("Uploaded CV version id={} label={}", saved.getId(), saved.getLabel());
			return toVersionResponse(saved);
		}
		catch (RuntimeException ex) {
			cleanupUploadedObject(stored.storageKey());
			throw ex;
		}
	}

	// NOT_SUPPORTED: getOwnedVersion() below runs its own short read-only transaction
	// (Spring Data JPA repository methods are self-transactional), which closes before
	// the S3 GetObject call — so no pooled DB connection is held across the network hop.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ResponseEntity<InputStreamResource> download(Long cvVersionId) {
		CvVersion version = getOwnedVersion(cvVersionId);
		InputStream stream = objectStorageService.download(version.getStorageKey());
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(version.getOriginalFilename(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.contentType(MediaType.parseMediaType(version.getContentType()))
				.contentLength(version.getSizeBytes())
				.body(new InputStreamResource(stream));
	}

	public void deleteVersion(Long cvVersionId) {
		long userId = currentUserId();
		CvVersion version = getOwnedVersion(cvVersionId);
		if (applicationCvRepository.existsByCvVersionIdAndUserId(cvVersionId, userId)) {
			throw new ConflictException("CV version is linked to vacancy sendings and cannot be deleted");
		}
		String storageKey = version.getStorageKey();
		// DB first so a failed S3 delete cannot leave a live row pointing at a missing object.
		cvVersionRepository.delete(version);
		cvVersionRepository.flush();
		try {
			objectStorageService.delete(storageKey);
		}
		catch (RuntimeException ex) {
			log.warn("CV version id={} removed from DB but S3 cleanup failed key={}", cvVersionId, storageKey, ex);
		}
		log.info("Deleted CV version id={}", cvVersionId);
	}

	public ApplicationCvResponse sendToVacancy(Long vacancyId, SendCvRequest request) {
		long userId = currentUserId();
		Vacancy vacancy = vacancyRepository.findByIdAndUserId(vacancyId, userId)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + vacancyId));
		CvVersion version = getOwnedVersion(request.cvVersionId());

		ApplicationCv sending = new ApplicationCv();
		sending.setUserId(userId);
		sending.setVacancyId(vacancy.getId());
		sending.setCvVersionId(version.getId());
		sending.setCompany(vacancy.getCompany());
		sending.setNotes(blankToNull(request.notes()));
		jobApplicationRepository.findByVacancyIdAndUserId(vacancyId, userId)
				.ifPresent(application -> sending.setJobApplicationId(application.getId()));

		ApplicationCv saved = applicationCvRepository.save(sending);
		log.info("Sent CV versionId={} to vacancyId={} sendingId={}", version.getId(), vacancyId, saved.getId());
		return toSendingResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<ApplicationCvResponse> listSendingsForVacancy(Long vacancyId) {
		if (!vacancyRepository.existsByIdAndUserId(vacancyId, currentUserId())) {
			throw new NotFoundException("Vacancy not found: " + vacancyId);
		}
		return applicationCvRepository.findByVacancyIdAndUserIdOrderBySentAtDesc(vacancyId, currentUserId())
				.stream()
				.map(this::toSendingResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ApplicationCvResponse> listAllSendings() {
		return applicationCvRepository.findByUserIdOrderBySentAtDesc(currentUserId()).stream()
				.map(this::toSendingResponse)
				.toList();
	}

	private void cleanupUploadedObject(String storageKey) {
		try {
			objectStorageService.delete(storageKey);
			log.warn("Cleaned up orphan S3 object after CV DB save failure key={}", storageKey);
		}
		catch (RuntimeException cleanupEx) {
			log.error("Failed to clean up S3 object after CV DB save failure key={}", storageKey, cleanupEx);
		}
	}

	private CvVersion getOwnedVersion(Long cvVersionId) {
		return cvVersionRepository.findByIdAndUserId(cvVersionId, currentUserId())
				.orElseThrow(() -> new NotFoundException("CV version not found: " + cvVersionId));
	}

	private long currentUserId() {
		return currentUserService.requireUserId();
	}

	private CvVersionResponse toVersionResponse(CvVersion version) {
		return new CvVersionResponse(
				version.getId(),
				version.getLabel(),
				version.getOriginalFilename(),
				version.getContentType(),
				version.getSizeBytes(),
				version.getCreatedAt().toString()
		);
	}

	private ApplicationCvResponse toSendingResponse(ApplicationCv sending) {
		return new ApplicationCvResponse(
				sending.getId(),
				sending.getVacancyId(),
				sending.getJobApplicationId(),
				sending.getCvVersionId(),
				sending.getCompany(),
				sending.getNotes(),
				sending.getSentAt().toString()
		);
	}

	private static String requireLabel(String label) {
		if (label == null || label.isBlank()) {
			throw new IllegalArgumentException("Label is required");
		}
		String trimmed = label.trim();
		if (trimmed.length() > MAX_LABEL_LENGTH) {
			throw new IllegalArgumentException("Label must be at most " + MAX_LABEL_LENGTH + " characters");
		}
		return trimmed;
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static boolean isAllowedFile(MultipartFile file) {
		String filename = file.getOriginalFilename();
		if (filename == null || filename.isBlank()) {
			return false;
		}
		String lower = filename.toLowerCase(Locale.ROOT);
		return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
	}

	private static String resolveContentType(MultipartFile file) {
		String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
		if (filename.endsWith(".tex")) {
			return "application/x-tex";
		}
		String contentType = file.getContentType();
		if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equals(contentType)) {
			return contentType;
		}
		if (filename.endsWith(".pdf")) {
			return "application/pdf";
		}
		if (filename.endsWith(".doc")) {
			return "application/msword";
		}
		if (filename.endsWith(".docx")) {
			return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		}
		return "application/octet-stream";
	}
}
