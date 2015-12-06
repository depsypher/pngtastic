package com.googlecode.pngtastic.core;

import com.googlecode.pngtastic.core.processing.Base64;
import com.googlecode.pngtastic.core.processing.ZopfliCompressionHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Optimizes PNG images for smallest possible filesize.
 *
 * @author rayvanderborght
 */
public class PngOptimizer extends PngProcessor {

	private boolean generateDataUriCss = false;
	public void setGenerateDataUriCss(boolean generateDataUriCss) { this.generateDataUriCss = generateDataUriCss; }

	private final List<Stats> stats = new ArrayList<>();
	public List<Stats> getStats() { return stats; }

	public PngOptimizer() {
		this(Logger.NONE);
	}

	public PngOptimizer(String logLevel) {
		super(logLevel);
	}

	/** */
	public void optimize(PngImage image, String outputFileName, boolean removeGamma, Integer compressionLevel)
			throws IOException {

		log.debug("=== OPTIMIZING ===");

		long start = System.currentTimeMillis();
		PngImage optimized = optimize(image, removeGamma, compressionLevel);

		ByteArrayOutputStream optimizedBytes = new ByteArrayOutputStream();
		long optimizedSize = optimized.writeDataOutputStream(optimizedBytes).size();

		File originalFile = new File(image.getFileName());
		long originalFileSize = originalFile.length();

		byte[] optimalBytes = (optimizedSize < originalFileSize)
				? optimizedBytes.toByteArray() : getFileBytes(originalFile, originalFileSize);

		File exported = optimized.export(outputFileName, optimalBytes);

		long optimizedFileSize = exported.length();
		long time = System.currentTimeMillis() - start;

		log.debug("Optimized in %d milliseconds, size %d", time, optimizedSize);
		log.debug("Original length in bytes: %d (%s)", originalFileSize, image.getFileName());
		log.debug("Final length in bytes: %d (%s)", optimizedFileSize, outputFileName);

		long fileSizeDifference = (optimizedFileSize <= originalFileSize)
				? (originalFileSize - optimizedFileSize) : -(optimizedFileSize - originalFileSize);

		log.info("%5.2f%% :%6dB ->%6dB (%5dB saved) - %s",
				fileSizeDifference / Float.valueOf(originalFileSize) * 100,
				originalFileSize, optimizedFileSize, fileSizeDifference, outputFileName);

		String dataUri = (generateDataUriCss) ? Base64.encodeBytes(optimalBytes) : null;

		stats.add(new Stats(image.getFileName(), originalFileSize, optimizedFileSize, image.getWidth(), image.getHeight(), dataUri));
	}

	/** */
	public PngImage optimize(PngImage image) throws IOException {
		return this.optimize(image, false, null);
	}

	/** */
	public PngImage optimize(PngImage image, boolean removeGamma, Integer compressionLevel) throws IOException {
		// FIXME: support low bit depth interlaced images
		if (image.getInterlace() == 1 && image.getSampleBitCount() < 8) {
			return image;
		}

		PngImage result = new PngImage(log);
		result.setInterlace((short) 0);

		Iterator<PngChunk> itChunks = image.getChunks().iterator();
		PngChunk chunk = processHeadChunks(result, removeGamma, itChunks);

		// collect image data chunks
		byte[] inflatedImageData = getInflatedImageData(chunk, itChunks);

		int scanlineLength = (int)(Math.ceil(image.getWidth() * image.getSampleBitCount() / 8F)) + 1;

		List<byte[]> originalScanlines = (image.getInterlace() == 1)
				? pngInterlaceHandler.deInterlace((int) image.getWidth(), (int) image.getHeight(), image.getSampleBitCount(), inflatedImageData)
				: getScanlines(inflatedImageData, image.getSampleBitCount(), scanlineLength, image.getHeight());

		// TODO: use this for bit depth reduction
//		Map<PngPixel, Integer> colors = getColors(image, originalScanlines, 32);

		// apply each type of filtering
		Map<PngFilterType, List<byte[]>> filteredScanlines = new HashMap<>();
		for (PngFilterType filterType : PngFilterType.standardValues()) {
			log.debug("Applying filter: %s", filterType);
			List<byte[]> scanlines = copyScanlines(originalScanlines);
			pngFilterHandler.applyFiltering(filterType, scanlines, image.getSampleBitCount());

			filteredScanlines.put(filterType, scanlines);
		}

		// pick the filter that compresses best
		PngFilterType bestFilterType = null;
		byte[] deflatedImageData = null;
		for (Entry<PngFilterType, List<byte[]>> entry : filteredScanlines.entrySet()) {
			byte[] imageResult = pngCompressionHandler.deflate(serialize(entry.getValue()), compressionLevel, true);
			if (deflatedImageData == null || imageResult.length < deflatedImageData.length) {
				deflatedImageData = imageResult;
				bestFilterType = entry.getKey();
			}
		}

		// see if adaptive filtering results in even better compression
		List<byte[]> scanlines = copyScanlines(originalScanlines);
		pngFilterHandler.applyAdaptiveFiltering(inflatedImageData, scanlines, filteredScanlines, image.getSampleBitCount());

		byte[] adaptiveImageData = pngCompressionHandler.deflate(inflatedImageData, compressionLevel, true);
		log.debug("Original=%d, Adaptive=%d, %s=%d", image.getImageData().length, adaptiveImageData.length,
				bestFilterType, (deflatedImageData == null) ? 0 : deflatedImageData.length);

		if (deflatedImageData == null || adaptiveImageData.length < deflatedImageData.length) {
			deflatedImageData = adaptiveImageData;
			bestFilterType = PngFilterType.ADAPTIVE;
		}

		PngChunk imageChunk = new PngChunk(PngChunk.IMAGE_DATA.getBytes(), deflatedImageData);
		result.addChunk(imageChunk);

		// finish it
		while (chunk != null) {
			if (chunk.isCritical() && !PngChunk.IMAGE_DATA.equals(chunk.getTypeString())) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(chunk.getLength());
				DataOutputStream data = new DataOutputStream(bytes);

				data.write(chunk.getData());
				data.close();

				PngChunk newChunk = new PngChunk(chunk.getType(), bytes.toByteArray());
				result.addChunk(newChunk);
			}
			chunk = itChunks.hasNext() ? itChunks.next() : null;
		}

		// make sure we have the IEND chunk
		List<PngChunk> chunks = result.getChunks();
		if (chunks != null && !PngChunk.IMAGE_TRAILER.equals(chunks.get(chunks.size() - 1).getTypeString())) {
			result.addChunk(new PngChunk(PngChunk.IMAGE_TRAILER.getBytes(), new byte[] { }));
		}

		return result;
	}

	/* */
	private List<byte[]> copyScanlines(List<byte[]> original) {
		List<byte[]> copy = new ArrayList<>(original.size());
		for (byte[] scanline : original) {
			copy.add(scanline.clone());
		}

		return copy;
	}

	/* */
	private byte[] serialize(List<byte[]> scanlines) {
		int scanlineLength = scanlines.get(0).length;
		byte[] imageData = new byte[scanlineLength * scanlines.size()];
		for (int i = 0; i < scanlines.size(); i++) {
			int offset = i * scanlineLength;
			byte[] scanline = scanlines.get(i);
			System.arraycopy(scanline, 0, imageData, offset, scanlineLength);
		}

		return imageData;
	}

	/**
	 * Holds info about an image file optimization
	 */
	public static class Stats {
		private long originalFileSize;
		public long getOriginalFileSize() { return originalFileSize; }

		private long optimizedFileSize;
		public long getOptimizedFileSize() { return optimizedFileSize; }

		private String fileName;
		private long width;
		private long height;
		private String dataUri;

		public Stats(String fileName, long originalFileSize, long optimizedFileSize, long width, long height, String dataUri) {
			this.originalFileSize = originalFileSize;
			this.optimizedFileSize = optimizedFileSize;
			this.fileName = fileName;
			this.width = width;
			this.height = height;
			this.dataUri = dataUri;
		}
	}

	/**
	 * Get the number of bytes saved in all images processed so far
	 *
	 * @return The number of bytes saved
	 */
	public long getTotalSavings() {
		long totalSavings = 0;
		for (PngOptimizer.Stats stat : stats) {
			totalSavings += (stat.getOriginalFileSize() - stat.getOptimizedFileSize());
		}

		return totalSavings;
	}

	/**
	 * Get the css containing data uris of the images processed by the optimizer
	 */
	public void generateDataUriCss(String dir) throws IOException {
		String path = (dir == null) ? "" : dir + "/";
		PrintWriter out = new PrintWriter(path + "DataUriCss.html");

		try {
			out.append("<html>\n<head>\n\t<style>");

			for (PngOptimizer.Stats stat : stats) {
				String name = stat.fileName.replaceAll("[^A-Za-z0-9]", "_");
				out.append('#').append(name).append(" {\n")
						.append("\tbackground: url(\"data:image/png;base64,")
						.append(stat.dataUri).append("\") no-repeat left top;\n")
						.append("\twidth: ").append(String.valueOf(stat.width)).append("px;\n")
						.append("\theight: ").append(String.valueOf(stat.height)).append("px;\n")
						.append("}\n");
			}
			out.append("\t</style>\n</head>\n<body>\n");

			for (PngOptimizer.Stats stat : stats) {
				String name = stat.fileName.replaceAll("[^A-Za-z0-9]", "_");
				out.append("\t<div id=\"").append(name).append("\"></div>\n");
			}

			out.append("</body>\n</html>");
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	private byte[] getFileBytes(File originalFile, long originalFileSize) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate((int) originalFileSize);
		FileInputStream ins = null;
		try {
			ins = new FileInputStream(originalFile);
			ins.getChannel().read(buffer);
		} finally {
			if (ins != null) {
				ins.close();
			}
		}
		return buffer.array();
	}

	/* */
	@SuppressWarnings("unused")
	private void printData(byte[] inflatedImageData) {
		StringBuilder result = new StringBuilder();
		for (byte b : inflatedImageData) {
			result.append(String.format("%2x|", b));
		}
		log.debug(result.toString());
	}

	public void setCompressor(String compressor) {
		if (compressor != null && compressor.contains("zopfli")) {
			this.pngCompressionHandler = new ZopfliCompressionHandler(log, compressor);
		}
	}
}
