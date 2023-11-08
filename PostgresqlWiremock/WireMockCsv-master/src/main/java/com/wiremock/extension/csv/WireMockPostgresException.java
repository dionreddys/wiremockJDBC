/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

/**
 *
 * Pengecualian tunggal digunakan dalam ekstensi.
 *
 */
public class WireMockPostgresException extends Exception {

	private static final long serialVersionUID = 1L;

	public WireMockPostgresException() {
	}

	public WireMockPostgresException(final String message) {
		super(message);
	}

	public WireMockPostgresException(final Throwable cause) {
		super(cause);
	}

	public WireMockPostgresException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public WireMockPostgresException(final String message, final Throwable cause, final boolean enableSuppression,
									 final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
