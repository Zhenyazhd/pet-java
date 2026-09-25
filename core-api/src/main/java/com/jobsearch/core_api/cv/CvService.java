package com.jobsearch.core_api.cv;

import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.cv.CvDtos.ApplicationCvResponse;
import com.jobsearch.core_api.cv.CvDtos.CvVersionResponse;
import com.jobsearch.core_api.cv.CvDtos.SendCvRequest;
import com.jobsearch.core_api.jobapplication.JobApplicationRepository;
import com.jobsearch.core_api.storage.ObjectStorageService;
import com.jobsearch.core_api.storage.ObjectStorageService.StoredObject;
import com.jobsearch.core_api.vacancy.Vacancy;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class CvService {

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			".pdf",
			".doc",
			".docx",
			".tex"
	);

	private final AppProperties appProperties;
	private final ObjectStorageService objectStorageService;
	private final CvVersionRepository cvVersionRepository;
	private final ApplicationCvRepository applicationCvRepository;
	private final VacancyRepository vacancyRepository;
	private final JobApplicationRepository jobApplicationRepository;

	public CvService(
			AppProperties appProperties,
			ObjectStorageService objectStorageService,
			CvVersionRepository cvVersionRepository,
			ApplicationCvRepository applicationCvRepository,
			VacancyRepository vacancyRepository,
			JobApplicationRepository jobApplicationRepository
	) {
		this.appProperties = appProperties;
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
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is required");
		}
		if (!isAllowedFile(file)) {
			throw new IllegalArgumentException("Only PDF, Word (.doc/.docx) or TeX (.tex) files are allowed");
		}

		StoredObject stored = objectStorageService.upload(currentUserId(), file);
		CvVersion version = new CvVersion();
		version.setUserId(currentUserId());
		version.setLabel(label.trim());
		version.setOriginalFilename(stored.originalFilename());
		version.setContentType(resolveContentType(file));
		version.setStorageKey(stored.storageKey());
		version.setSizeBytes(stored.sizeBytes());
		return toVersionResponse(cvVersionRepository.save(version));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<InputStreamResource> download(Long cvVersionId) {
		CvVersion version = getOwnedVersion(cvVersionId);
		InputStream stream = objectStorageService.download(version.getStorageKey());
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + version.getOriginalFilename() + "\"")
				.contentType(MediaType.parseMediaType(version.getContentType()))
				.contentLength(version.getSizeBytes())
				.body(new InputStreamResource(stream));
	}

	public void deleteVersion(Long cvVersionId) {
		CvVersion version = getOwnedVersion(cvVersionId);
		if (applicationCvRepository.existsByCvVersionId(cvVersionId)) {
			throw new ConflictException("CV version is linked to vacancy sendings and cannot be deleted");
		}
		objectStorageService.delete(version.getStorageKey());
		cvVersionRepository.delete(version);
	}

	public ApplicationCvResponse sendToVacancy(Long vacancyId, SendCvRequest request) {
		Vacancy vacancy = vacancyRepository.findById(vacancyId)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + vacancyId));
		CvVersion version = getOwnedVersion(request.cvVersionId());

		ApplicationCv sending = new ApplicationCv();
		sending.setUserId(currentUserId());
		sending.setVacancyId(vacancy.getId());
		sending.setCvVersionId(version.getId());
		sending.setCompany(vacancy.getCompany());
		sending.setNotes(blankToNull(request.notes()));
		jobApplicationRepository.findByVacancyId(vacancyId)
				.ifPresent(application -> sending.setJobApplicationId(application.getId()));

		return toSendingResponse(applicationCvRepository.save(sending));
	}

	@Transactional(readOnly = true)
	public List<ApplicationCvResponse> listSendingsForVacancy(Long vacancyId) {
		if (!vacancyRepository.existsById(vacancyId)) {
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

	private CvVersion getOwnedVersion(Long cvVersionId) {
		return cvVersionRepository.findByIdAndUserId(cvVersionId, currentUserId())
				.orElseThrow(() -> new NotFoundException("CV version not found: " + cvVersionId));
	}

	private long currentUserId() {
		return appProperties.getCurrentUserId();
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
