package com.jobsearch.joblist.job;

import com.jobsearch.joblist.source.SourceName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A normalised vacancy, shared by every profile. Immutable after insert. */
@Entity
@Table(name = "job")
public class Job {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private SourceName source;

	@Column(name = "external_id")
	private String externalId;

	private String fingerprint;
	private String url;
	private String title;
	private String company;

	@Column(name = "description_text")
	private String descriptionText;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> countries = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> cities = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	private Remote remote = Remote.UNKNOWN;

	@Column(name = "contract_type")
	@Enumerated(EnumType.STRING)
	private ContractType contractType = ContractType.UNKNOWN;

	@Column(name = "salary_min")
	private BigDecimal salaryMin;

	@Column(name = "salary_max")
	private BigDecimal salaryMax;

	@Column(name = "salary_currency")
	private String salaryCurrency;

	@Column(name = "salary_period")
	private String salaryPeriod;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> skills = new ArrayList<>();

	@Column(name = "posted_at")
	private Instant postedAt;

	@Column(name = "fetched_at")
	private Instant fetchedAt;

	public Long getId() {
		return id;
	}

	public String locationText() {
		String cities = String.join(", ", getCities());
		String countries = String.join(", ", getCountries());
		return cities.isEmpty() ? countries : cities + " (" + countries + ")";
	}

	public String salaryText() {
		if (salaryMin == null && salaryMax == null) {
			return null;
		}
		String range = salaryMin != null && salaryMax != null && !salaryMin.equals(salaryMax)
				? salaryMin.toPlainString() + "-" + salaryMax.toPlainString()
				: (salaryMin != null ? salaryMin : salaryMax).toPlainString();
		return range + (salaryCurrency == null ? "" : " " + salaryCurrency) + (salaryPeriod == null ? "" : " per " + salaryPeriod);
	}

	public SourceName getSource() {
		return source;
	}

	public void setSource(SourceName source) {
		this.source = source;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public void setFingerprint(String fingerprint) {
		this.fingerprint = fingerprint;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getDescriptionText() {
		return descriptionText;
	}

	public void setDescriptionText(String descriptionText) {
		this.descriptionText = descriptionText;
	}

	public List<String> getCountries() {
		return countries;
	}

	public void setCountries(List<String> countries) {
		this.countries = countries;
	}

	public List<String> getCities() {
		return cities;
	}

	public void setCities(List<String> cities) {
		this.cities = cities;
	}

	public Remote getRemote() {
		return remote;
	}

	public void setRemote(Remote remote) {
		this.remote = remote;
	}

	public ContractType getContractType() {
		return contractType;
	}

	public void setContractType(ContractType contractType) {
		this.contractType = contractType;
	}

	public BigDecimal getSalaryMin() {
		return salaryMin;
	}

	public void setSalaryMin(BigDecimal salaryMin) {
		this.salaryMin = salaryMin;
	}

	public BigDecimal getSalaryMax() {
		return salaryMax;
	}

	public void setSalaryMax(BigDecimal salaryMax) {
		this.salaryMax = salaryMax;
	}

	public String getSalaryCurrency() {
		return salaryCurrency;
	}

	public void setSalaryCurrency(String salaryCurrency) {
		this.salaryCurrency = salaryCurrency;
	}

	public String getSalaryPeriod() {
		return salaryPeriod;
	}

	public void setSalaryPeriod(String salaryPeriod) {
		this.salaryPeriod = salaryPeriod;
	}

	public List<String> getSkills() {
		return skills;
	}

	public void setSkills(List<String> skills) {
		this.skills = skills;
	}

	public Instant getPostedAt() {
		return postedAt;
	}

	public void setPostedAt(Instant postedAt) {
		this.postedAt = postedAt;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}

	public void setFetchedAt(Instant fetchedAt) {
		this.fetchedAt = fetchedAt;
	}
}
