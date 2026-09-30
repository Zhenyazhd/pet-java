package com.jobsearch.core_api.common;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed-window in-memory rate limiter (per key). Fine for single-instance deploys;
 * multi-instance would need a shared store.
 */
@Component
public class FixedWindowRateLimiter {

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	/**
	 * @return retry-after seconds if limited, or {@code -1} if the request is allowed
	 */
	public long tryAcquire(String key, int limitPerWindow, long windowMillis) {
		if (limitPerWindow <= 0) {
			return -1;
		}
		long now = System.currentTimeMillis();
		long[] deniedRetryAfter = { -1 };
		windows.compute(key, (k, existing) -> {
			if (existing == null || now >= existing.resetAtMs) {
				return new Window(1, now + windowMillis);
			}
			if (existing.count >= limitPerWindow) {
				deniedRetryAfter[0] = Math.max(1L, (existing.resetAtMs - now + 999) / 1000);
				return existing;
			}
			return new Window(existing.count + 1, existing.resetAtMs);
		});
		maybeCleanup(now);
		return deniedRetryAfter[0];
	}

	private void maybeCleanup(long now) {
		if (windows.size() < 2_048) {
			return;
		}
		Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Window> entry = it.next();
			if (now >= entry.getValue().resetAtMs) {
				it.remove();
			}
		}
	}

	private record Window(int count, long resetAtMs) {
	}
}
