/*
 * $Id$
 * $URL$
 */
package com.googlecode.pngtastic.core.processing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Apply PNG compression and decompression.  Implies zlib format, aka LZ77.
 *
 * @author rayvanderborght
 */
public interface PngCompressionHandler
{
	/**
	 * Inflate (decompress) the compressed image data
	 *
	 * @param deflatedImageData A stream containing the compressed image data
	 * @return A byte array containing the uncompressed data
	 * @throws IOException
	 */
	public byte[] inflate(ByteArrayOutputStream deflatedImageData) throws IOException;

	/**
	 * Deflate (compress) the inflated data using the given compression level.
	 * If compressionLevel is null then do a brute force trial of all
	 * compression levels to find the best one.
	 *
	 * @param inflatedImageData A byte array containing the uncompressed image data
	 * @param compressionLevel The compression level to use
	 * @return A byte array containing the compressed image data
	 * @throws IOException
	 */
	public byte[] deflate(byte[] inflatedImageData, Integer compressionLevel) throws IOException;
}