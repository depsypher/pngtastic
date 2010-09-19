/*
 * $Id: $
 * $URL: $
 */
package com.googlecode.pngtastic.core.processing;

import com.googlecode.pngtastic.core.PngException;

/**
 * Apply PNG filtering and defiltering
 *
 * @author rayvanderborght
 */
public interface PngFilterHandler
{
	/**
	 * Do filtering as described in the png spec:
	 * The scanline starts with a filter type byte, then continues with the image data.
	 */
	public void filter(byte[] line, byte[] previousLine, int sampleBitCount) throws PngException;

	/**
	 * Do the opposite of PNG filtering:
	 * @see #filter(byte[], byte[], int)
	 */
	public void deFilter(byte[] line, byte[] previousLine, int sampleBitCount) throws PngException;
}