package com.googlecode.pngtastic.core.processing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

import com.googlecode.pngtastic.core.Logger;

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
	public byte[] inflate(ByteArrayOutputStream imageBytes) throws IOException {
		InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(imageBytes.toByteArray()));
		ByteArrayOutputStream inflatedOut = new ByteArrayOutputStream();

		int readLength;
		byte[] block = new byte[8192];
		while ((readLength = inflater.read(block)) != -1) {
			inflatedOut.write(block, 0, readLength);
		}

		byte[] inflatedImageData = inflatedOut.toByteArray();
		return inflatedImageData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] deflate(byte[] inflatedImageData, Integer compressionLevel, boolean concurrent) throws IOException {
		List<byte[]> results = deflateImageDataSerially(inflatedImageData, compressionLevel);

		byte[] result = null;
		for (int i = 0; i < results.size(); i++) {
			byte[] data = results.get(i);
			if (result == null || (data.length < result.length)) {
				result = data;
			}
		}
		this.log.debug("Image bytes=%d", (result == null) ? -1 : result.length);

		return result;
	}

	/* */
	private List<byte[]> deflateImageDataSerially(byte[] inflatedImageData, Integer compressionLevel) {
		List<byte[]> results = new ArrayList<byte[]>();

		try {
			results.add(deflateImageData(inflatedImageData, compressionLevel));
		} catch (Throwable e) {
			log.error("Uncaught Exception: %s", e.getMessage());
		}

		return results;
	}

	/* */
	private byte[] deflateImageData(byte[] inflatedImageData, Integer compressionLevel) throws IOException {
		byte[] result = deflate(inflatedImageData).toByteArray();
		log.debug("Compression strategy: zopfli, compression level=%d, bytes=%d", compressionLevel, (result == null) ? -1 : result.length);

		return result;
	}

	/* */
	private ByteArrayOutputStream deflate(byte[] inflatedImageData) throws IOException {
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

	private FileOutputStream writeFileOutputStream(File out, byte[] bytes) throws IOException {
		FileOutputStream outs = null;
		try {
			outs = new FileOutputStream(out);
			outs.write(bytes);
		} finally {
			if (outs != null) {
				outs.close();
			}
		}
		return outs;
	}
}
