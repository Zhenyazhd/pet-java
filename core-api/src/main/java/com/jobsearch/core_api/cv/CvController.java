package com.jobsearch.core_api.cv;

import com.jobsearch.core_api.cv.CvDtos.ApplicationCvResponse;
import com.jobsearch.core_api.cv.CvDtos.CvVersionResponse;
import com.jobsearch.core_api.cv.CvDtos.SendCvRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** CV file upload/download and linking versions to vacancies. */
@RestController
@RequestMapping("/api")
public class CvController {

	private final CvService cvService;

	public CvController(CvService cvService) {
		this.cvService = cvService;
	}

	@GetMapping("/cvs")
	public List<CvVersionResponse> list() {
		return cvService.listVersions();
	}

	@PostMapping(value = "/cvs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public CvVersionResponse upload(
			@RequestPart("label") String label,
			@RequestPart("file") MultipartFile file
	) {
		return cvService.upload(label, file);
	}

	@GetMapping("/cvs/{id}/download")
	public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
		return cvService.download(id);
	}

	@DeleteMapping("/cvs/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		cvService.deleteVersion(id);
	}

	@GetMapping("/cv-sendings")
	public List<ApplicationCvResponse> listAllSendings() {
		return cvService.listAllSendings();
	}

	@GetMapping("/vacancies/{vacancyId}/cv-sendings")
	public List<ApplicationCvResponse> listSendings(@PathVariable Long vacancyId) {
		return cvService.listSendingsForVacancy(vacancyId);
	}

	@PostMapping("/vacancies/{vacancyId}/cv-sendings")
	@ResponseStatus(HttpStatus.CREATED)
	public ApplicationCvResponse send(
			@PathVariable Long vacancyId,
			@Valid @RequestBody SendCvRequest request
	) {
		return cvService.sendToVacancy(vacancyId, request);
	}
}
