package com.googlecode.pngtastic.core;

/**
 * Exception type for pngtastic code
 *
 * @author rayvanderborght
 */
public class PngException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** */
	public PngException() {  }

	/** */
	public PngException(String message) {
		super(message);
	}

	/** */
	public PngException(String message, Throwable cause) {
		super(message, cause);
	}
}
