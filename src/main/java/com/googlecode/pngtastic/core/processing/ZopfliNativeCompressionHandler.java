package com.googlecode.pngtastic.core.processing;

import com.googlecode.pngtastic.core.Logger;
import com.googlecode.pngtastic.core.PngCompressionType;
import com.googlecode.pngtastic.core.SystemDetector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements PNG compression and decompression
 * Uses zopfli to compress: https://code.google.com/p/zopfli/
 *
 * @author rayvanderborght
 */
public class ZopfliNativeCompressionHandler implements PngCompressionHandler {

	private static PngCompressionType compressionMethod = PngCompressionType.ZLIB;
	private final Logger log;
	private final String iterations;
	private final static Path compressor = Paths.get("lib", "zopfli").toAbsolutePath();

	public static void setCompressionMethod(PngCompressionType method) {
		synchronized (compressor){
			compressionMethod = method;
		}
	}

	public ZopfliNativeCompressionHandler(Logger log, Integer iterations) {
		this.log = log;
		if (iterations == null || iterations < 1){
			iterations = 15;
		}
		this.iterations = iterations + "";
		try {
			if (Files.notExists(compressor)) {
				log.debug("Extracting proper native");
				Files.createDirectories(compressor.getParent());
				switch (SystemDetector.getOS()) {
					case UNIX:
						Files.copy(this.getClass().getResourceAsStream("/linuxBinary"), compressor);
						break;
					case MAC:
						Files.copy(this.getClass().getResourceAsStream("/macBinary"), compressor); //use old binary since I do not have access to a mac
						break;
					case WINDOWS:
						Files.copy(this.getClass().getResourceAsStream("/winBinary"), compressor);
						break;
					case SOLARIS:
					case UNKNOWN:
						log.info("No precompiled binaries exist for this OS.\n"
								+ "Place a precompiled binary at \"" + compressor.toString() + "\" to override.");
						System.exit(-10);
				}
			}
		} catch (IOException e){
			log.info("Failed to extract binary to \"" + compressor.toString() + "\"\n"
					+ "Error Message: " + e.getMessage());
			System.exit(-11);
		}
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
			ProcessBuilder p;
			synchronized (compressor) {
				p = new ProcessBuilder(compressor.toString(), "--i", iterations, "-c", compressionMethod.getMethod(), imageData.getCanonicalPath());
			}
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
