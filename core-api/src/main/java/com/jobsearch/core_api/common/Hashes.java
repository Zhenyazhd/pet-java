package com.jobsearch.core_api.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashes {

	private Hashes() {
	}

	/** Hex sha256 of the parts joined with NUL, so ("ab", "c") and ("a", "bc") hash differently. */
	public static String sha256(String... parts) {
		try {
			byte[] input = String.join("\0", parts).getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
