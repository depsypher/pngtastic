package com.googlecode.pngtastic.core.processing;

import java.io.ByteArrayOutputStream;

/**
 * Allows access to the underlying buf without doing deep copies on it
 *
 * @author ray
 */
public class PngByteArrayOutputStream extends ByteArrayOutputStream {

	private final int initialSize;

	public PngByteArrayOutputStream() {
		this(32);
	}

	public PngByteArrayOutputStream(int size) {
		super(size);
		this.initialSize = size;
	}

	public PngByteArrayOutputStream(byte[] initial) {
		buf = initial;
		count = initial.length;
		initialSize = count;
	}

	public byte[] get() {
		return buf;
	}

	public void reset() {
		super.reset();
		if (buf.length > initialSize) {
			buf = new byte[initialSize];
		}
	}

	public int len() {
		return count;
	}
}
