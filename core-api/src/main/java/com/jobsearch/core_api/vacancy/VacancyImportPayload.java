package com.jobsearch.core_api.vacancy;

/** Job payload for {@link VacancyImportHandler}: what the user submitted, already stripped. */
record VacancyImportPayload(String url, String pastedText) {
}
