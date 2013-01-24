package com.googlecode.pngtastic.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.googlecode.pngtastic.core.processing.Base64;
import com.googlecode.pngtastic.core.processing.PngCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngFilterHandler;
import com.googlecode.pngtastic.core.processing.PngInterlaceHandler;
import com.googlecode.pngtastic.core.processing.PngtasticCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngtasticFilterHandler;
import com.googlecode.pngtastic.core.processing.PngtasticInterlaceHandler;

/**
 * Optimizes PNG images for smallest possible filesize.
 *
 * @author rayvanderborght
 */
public class PngOptimizer {

	private final Logger log;

	private PngFilterHandler pngFilterHandler;
	private PngInterlaceHandler pngInterlaceHander;
	private PngCompressionHandler pngCompressionHandler;

	private boolean generateDataUriCss = false;
	public void setGenerateDataUriCss(boolean generateDataUriCss) { this.generateDataUriCss = generateDataUriCss; }

	private final List<Stats> stats = new ArrayList<Stats>();
	public List<Stats> getStats() { return stats; }

	/** */
	public PngOptimizer() {
		this(Logger.NONE);
	}

	/** */
	public PngOptimizer(String logLevel) {
		this.log = new Logger(logLevel);
		this.pngFilterHandler = new PngtasticFilterHandler(log);
		this.pngInterlaceHander = new PngtasticInterlaceHandler(log, pngFilterHandler);
		this.pngCompressionHandler = new PngtasticCompressionHandler(log);
	}

	/** */
	public void optimize(PngImage image, String outputFileName, boolean removeGamma, Integer compressionLevel)
			throws FileNotFoundException, IOException {

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
				? pngInterlaceHander.deInterlace((int) image.getWidth(), (int) image.getHeight(), image.getSampleBitCount(), inflatedImageData)
				: getScanlines(inflatedImageData, image.getSampleBitCount(), scanlineLength, image.getHeight());

		// TODO: use this for bit depth reduction
//		this.getColors(image, originalScanlines);

		// apply each type of filtering
		Map<PngFilterType, List<byte[]>> filteredScanlines = new HashMap<PngFilterType, List<byte[]>>();
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
	private List<byte[]> getScanlines(byte[] inflatedImageData, int sampleBitCount, int rowLength, long height) {
		log.debug("Getting scanlines");

		List<byte[]> rows = new ArrayList<byte[]>(Math.max((int) height, 0));
		byte[] previousRow = new byte[rowLength];

		for (int i = 0; i < height; i++) {
			int offset = i * rowLength;
			byte[] row = new byte[rowLength];
			System.arraycopy(inflatedImageData, offset, row, 0, rowLength);
			try {
				pngFilterHandler.deFilter(row, previousRow, sampleBitCount);
				rows.add(row);
				previousRow = row.clone();
			} catch (PngException e) {
				log.error("Error: %s", e.getMessage());
			}
		}
		return rows;
	}

	/* */
	private List<byte[]> copyScanlines(List<byte[]> original) {
		List<byte[]> copy = new ArrayList<byte[]>(original.size());
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

	/* */
	private PngChunk processHeadChunks(PngImage result, boolean removeGamma, Iterator<PngChunk> itChunks) throws IOException {
		PngChunk chunk = null;
		while (itChunks.hasNext()) {
			chunk = itChunks.next();
			if (PngChunk.IMAGE_DATA.equals(chunk.getTypeString())) {
				break;
			}

			if (chunk.isRequired()) {
				if (removeGamma && PngChunk.IMAGE_GAMA.equalsIgnoreCase(chunk.getTypeString())) {
					continue;
				}
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(chunk.getLength());
				DataOutputStream data = new DataOutputStream(bytes);

				data.write(chunk.getData());
				data.close();

				PngChunk newChunk = new PngChunk(chunk.getType(), bytes.toByteArray());
				if (PngChunk.IMAGE_HEADER.equals(chunk.getTypeString())) {
					newChunk.setInterlace((byte) 0);
				}
				result.addChunk(newChunk);
			}
		}
		return chunk;
	}

	/* */
	private byte[] getInflatedImageData(PngChunk chunk, Iterator<PngChunk> itChunks) throws IOException {
		ByteArrayOutputStream imageBytes = new ByteArrayOutputStream(chunk == null ? 0 : chunk.getLength());
		DataOutputStream imageData = new DataOutputStream(imageBytes);
		while (chunk != null) {
			if (PngChunk.IMAGE_DATA.equals(chunk.getTypeString())) {
				imageData.write(chunk.getData());
			} else {
				break;
			}
			chunk = itChunks.hasNext() ? itChunks.next() : null;
		}
		imageData.close();

		return pngCompressionHandler.inflate(imageBytes);
	}

	/* */
	@SuppressWarnings("unused")
	private Set<PngPixel> getColors(PngImage original, List<byte[]> rows) throws IOException {
		Set<PngPixel> colors = new HashSet<PngPixel>();
		PngImageType imageType = PngImageType.forColorType(original.getColorType());
		int sampleSize = original.getSampleBitCount();

		for (byte[] row : rows) {
			int sampleCount = ((row.length - 1) * 8) / sampleSize;
			ByteArrayInputStream ins = new ByteArrayInputStream(row);
			DataInputStream dis = new DataInputStream(ins);
			dis.readUnsignedByte();	// the filter byte

			for (int i = 0; i < sampleCount; i++) {
				switch (imageType) {
					case INDEXED_COLOR:
						// TODO: read pixels from palette
						break;

					case GREYSCALE:
					case GREYSCALE_ALPHA:
						// TODO: who knows
						break;

					case TRUECOLOR:
						if (original.getBitDepth() == 8) {
							int red = dis.readUnsignedByte();
							int green = dis.readUnsignedByte();
							int blue = dis.readUnsignedByte();
							colors.add(new PngPixel(red, green, blue));
						} else {
							int red = dis.readUnsignedShort();
							int green = dis.readUnsignedShort();
							int blue = dis.readUnsignedShort();
							colors.add(new PngPixel(red, green, blue));
						}
						break;

					case TRUECOLOR_ALPHA:
						if (original.getBitDepth() == 8) {
							int red = dis.readUnsignedByte();
							int green = dis.readUnsignedByte();
							int blue = dis.readUnsignedByte();
							int alpha = dis.readUnsignedByte();
							colors.add(new PngPixel(red, green, blue, alpha));
						} else {
							int red = dis.readUnsignedShort();
							int green = dis.readUnsignedShort();
							int blue = dis.readUnsignedShort();
							int alpha = dis.readUnsignedShort();
							colors.add(new PngPixel(red, green, blue, alpha));
						}
						break;

					default:
						throw new IllegalArgumentException();
				}
			}
		}
//		for (PngPixel c : colors)
//			this.log.debug("r=%d g=%d b=%d a=%d", red, green, blue, alpha);

		this.log.debug("color count=%d", colors.size());

		return colors;
	}

	/** */
	private static class PngPixel {
		private final int red;
		private final int green;
		private final int blue;
		private final int alpha;

		/** */
		public PngPixel(int red, int green, int blue) {
			this(red, green, blue, -1);
		}

		/** */
		public PngPixel(int red, int green, int blue, int alpha) {
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.alpha = alpha;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + this.alpha;
			result = prime * result + this.blue;
			result = prime * result + this.green;
			result = prime * result + this.red;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			if (obj == null || this.getClass() != obj.getClass()) {
				return false;
			}

			PngPixel other = (PngPixel) obj;
			if (this.alpha != other.alpha || this.blue != other.blue || this.green != other.green || this.red != other.red) {
				return false;
			}

			return true;
		}
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
}
