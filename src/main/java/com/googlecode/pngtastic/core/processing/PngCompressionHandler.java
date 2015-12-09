package com.googlecode.pngtastic.core.processing;

import java.io.IOException;

/**
 * Apply PNG compression and decompression. Implies zlib format, aka LZ77.
 *
 * @author rayvanderborght
 */
public interface PngCompressionHandler {

	/**
	 * Deflate (compress) the inflated data using the given compression level.
	 * If compressionLevel is null then do a brute force trial of all
	 * compression levels to find the best one.
	 *
	 * @param inflatedImageData A PngByteArrayOutputStream containing the uncompressed image data
	 * @param compressionLevel The compression level to use
	 * @param concurrent Whether to test scanlines for best compressing filter type
	 *        concurrently. Should be set to true for performance, or false in
	 *        environments like google app engine that don't allow thread creation.
	 * @return A byte array containing the compressed image data
	 */
	public byte[] deflate(PngByteArrayOutputStream inflatedImageData, Integer compressionLevel, boolean concurrent) throws IOException;

	public String encodeBytes(byte[] bytes);
}
