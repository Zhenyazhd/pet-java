package com.jobsearch.core_api.common;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestPaths {

	private RequestPaths() {
	}


	public static String routed(HttpServletRequest request) {
		String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
		String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
		return servletPath + pathInfo;
	}
}
