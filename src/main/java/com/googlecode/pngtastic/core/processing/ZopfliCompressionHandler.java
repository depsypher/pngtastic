package com.googlecode.pngtastic.core.processing;

import com.googlecode.pngtastic.core.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements PNG compression and decompression
 * Uses zopfli to compress: https://code.google.com/p/zopfli/
 *
 * @author rayvanderborght
 */
public class ZopfliCompressionHandler implements PngCompressionHandler {

	private final Logger log;
	private final String compressor;	// e.g. /Users/ray/projects/pngtastic/lib/zopfli

	public ZopfliCompressionHandler(Logger log, String compressor) {
		this.log = log;
		this.compressor = compressor;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] deflate(PngByteArrayOutputStream inflatedImageData, Integer compressionLevel, boolean concurrent) throws IOException {
		final List<byte[]> results = deflateImageDataSerially(inflatedImageData);

		byte[] result = null;
		for (int i = 0; i < results.size(); i++) {
			byte[] data = results.get(i);
			if (result == null || (data.length < result.length)) {
				result = data;
			}
		}
		log.debug("Image bytes=%d", (result == null) ? -1 : result.length);

		return result;
	}

	@Override
	public String encodeBytes(byte[] bytes) {
		return Base64.encodeBytes(bytes);
	}

	/* */
	private List<byte[]> deflateImageDataSerially(final PngByteArrayOutputStream inflatedImageData) {
		final List<byte[]> results = new ArrayList<>();

		try {
			results.add(deflateImageData(inflatedImageData));
		} catch (Throwable e) {
			log.error("Uncaught Exception: %s", e.getMessage());
		}

		return results;
	}

	/* */
	private byte[] deflateImageData(final PngByteArrayOutputStream inflatedImageData) throws IOException {
		final byte[] result = deflate(inflatedImageData).toByteArray();
		log.debug("Compression strategy: zopfli, bytes=%d", (result == null) ? -1 : result.length);

		return result;
	}

	/* */
	private ByteArrayOutputStream deflate(PngByteArrayOutputStream inflatedImageData) throws IOException {
		File imageData = null;
		try {
			imageData = File.createTempFile("imagedata", ".zopfli");
			writeFileOutputStream(imageData, inflatedImageData);

			ProcessBuilder p = new ProcessBuilder(compressor, "-c", "--zlib", imageData.getCanonicalPath());
			Process process = p.start();

			ByteArrayOutputStream deflatedOut = new ByteArrayOutputStream();

			int byteCount;
			byte[] data = new byte[8192];

			InputStream s = process.getInputStream();
			while ((byteCount = s.read(data, 0, data.length)) != -1) {
				deflatedOut.write(data, 0, byteCount);
			}
			deflatedOut.flush();
			return deflatedOut;
		} finally {
			if (imageData != null) {
				imageData.delete();
			}
		}
	}

	private FileOutputStream writeFileOutputStream(File out, PngByteArrayOutputStream bytes) throws IOException {
		FileOutputStream outs = null;
		try {
			outs = new FileOutputStream(out);
			outs.write(bytes.get(), 0, bytes.len());
		} finally {
			if (outs != null) {
				outs.close();
			}
		}
		return outs;
	}
}
