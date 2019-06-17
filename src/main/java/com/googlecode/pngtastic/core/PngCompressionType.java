package com.googlecode.pngtastic.core;

public enum PngCompressionType {
	ZLIB("--zlib"),
	DEFLATE("--deflate");

	private final String method;

	PngCompressionType(String method) {
		this.method = method;
	}

	public String getMethod(){
		return method;
	}
}
