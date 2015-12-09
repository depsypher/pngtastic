package com.googlecode.pngtastic.core;

import com.googlecode.pngtastic.core.processing.PngByteArrayOutputStream;
import com.googlecode.pngtastic.core.processing.PngInterlaceHandler;
import com.googlecode.pngtastic.core.processing.PngtasticInterlaceHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Counts the dominant colors in a png image
 *
 * @author rayvanderborght
 */
public class PngColorCounter extends PngProcessor {

	private final PngInterlaceHandler pngInterlaceHandler;

	private final double distThreshold;
	private final double freqThreshold;
	private final int minAlpha;
	private final long timeout;

	private ColorCounterResult colorCounterResult;
	public ColorCounterResult getResult() { return colorCounterResult; }

	public PngColorCounter() {
		this(Logger.NONE, 0.01D, 0.01D, 30, 0L);
	}

	public PngColorCounter(double distThreshold, double freqThreshold, int minAlpha) {
		this(Logger.NONE, distThreshold, freqThreshold, minAlpha, 0L);
	}

	public PngColorCounter(double distThreshold, double freqThreshold, int minAlpha, long timeout) {
		this(Logger.NONE, distThreshold, freqThreshold, minAlpha, timeout);
	}

	public PngColorCounter(String logLevel, double distThreshold, double freqThreshold, int minAlpha, long timeout) {
		super(logLevel);

		this.distThreshold = distThreshold;
		this.freqThreshold = freqThreshold;
		this.minAlpha = minAlpha;
		this.timeout = timeout;

		this.pngInterlaceHandler = new PngtasticInterlaceHandler(log, pngFilterHandler);
	}

	/** */
	public void count(PngImage image) throws IOException {
		log.debug("=== COUNTING ===");

		// FIXME: support low bit depth interlaced images
		if (image.getInterlace() == 1 && image.getSampleBitCount() < 8) {
			log.debug("not supported");
			return;
		}

		final long start = System.currentTimeMillis();

		final Iterator<PngChunk> itChunks = image.getChunks().iterator();
		final PngChunk chunk = processHeadChunks(null, false, itChunks);

		// collect image data chunks
		final PngByteArrayOutputStream inflatedImageData = getInflatedImageData(chunk, itChunks);

		final long width = image.getWidth();
		final long height = image.getHeight();
		final int scanlineLength = (int) (Math.ceil(width * image.getSampleBitCount() / 8F)) + 1;

		final List<byte[]> originalScanlines = (image.getInterlace() == 1)
				? pngInterlaceHandler.deInterlace((int) width, (int) height, image.getSampleBitCount(), inflatedImageData)
				: getScanlines(inflatedImageData, image.getSampleBitCount(), scanlineLength, height);

		final List<PngPixel> colors = getColors(image, originalScanlines, start);
		final List<PngPixel> results = getMergedColors(image, colors, start);

		final long elapsed = System.currentTimeMillis() - start;
		colorCounterResult = new ColorCounterResult(image.getFileName(), width, height, colors.size(), results, elapsed);
	}

	private List<PngPixel> getColors(PngImage original, List<byte[]> rows, long start) throws IOException {
		final Map<PngPixel, Integer> colors = new LinkedHashMap<>();
		final PngImageType imageType = PngImageType.forColorType(original.getColorType());
		final int sampleSize = original.getSampleBitCount();

		int y = 0;
		for (byte[] row : rows) {
			if (timeout > 0 && (System.currentTimeMillis() - start > timeout)) {
				throw new PngException("Reached " + timeout + "ms timeout");
			}
			final int sampleCount = ((row.length - 1) * 8) / sampleSize;
			final ByteArrayInputStream ins = new ByteArrayInputStream(row);
			final DataInputStream dis = new DataInputStream(ins);
			dis.readUnsignedByte();	// the filter byte

			for (int x = 0; x < sampleCount; x++) {
				switch (imageType) {
					case INDEXED_COLOR: {
						// TODO: consider transparency
						final int offset = dis.readUnsignedByte() * 3;
						final int r = original.getPalette().getUnsignedByte(offset);
						final int g = original.getPalette().getUnsignedByte(offset + 1);
						final int b = original.getPalette().getUnsignedByte(offset + 2);

						final PngPixel pixel = new PngPixel(x, y, r, g, b, true);
						final Integer count = colors.get(pixel);
						colors.put(pixel, (count == null) ? 1 : count + 1);
						break;
					}

					case GREYSCALE:
					case GREYSCALE_ALPHA:
						// TODO: who knows
						throw new PngException("Greyscale images not supported");

					case TRUECOLOR: {
						final PngPixel pixel;
						if (original.getBitDepth() == 8) {
							final int r = dis.readUnsignedByte();
							final int g = dis.readUnsignedByte();
							final int b = dis.readUnsignedByte();
							pixel = new PngPixel(x, y, r, g, b, true);
						} else {
							final int r = dis.readUnsignedShort();
							final int g = dis.readUnsignedShort();
							final int b = dis.readUnsignedShort();
							pixel = new PngPixel(x, y, r, g, b, false);
						}
						final Integer count = colors.get(pixel);
						colors.put(pixel, (count == null) ? 1 : count + 1);
						break;
					}

					case TRUECOLOR_ALPHA: {
						final PngPixel pixel;
						if (original.getBitDepth() == 8) {
							final int r = dis.readUnsignedByte();
							final int g = dis.readUnsignedByte();
							final int b = dis.readUnsignedByte();
							final int a = dis.readUnsignedByte();
							pixel = new PngPixel(x, y, r, g, b, a);
						} else {
							final int r = dis.readUnsignedShort();
							final int g = dis.readUnsignedShort();
							final int b = dis.readUnsignedShort();
							final int a = dis.readUnsignedShort();
							pixel = new PngPixel(x, y, r, g, b, a);
						}
						if (pixel.getAlpha() > minAlpha) {
							final Integer count = colors.get(pixel);
							colors.put(pixel, (count == null) ? 1 : (count + 1));
						}
						break;
					}

					default:
						throw new IllegalArgumentException();
				}
			}
			y++;
		}
		log.debug("Full color count=%d", colors.size());

		if (freqThreshold > 0) {
			final int minFreq = (int) (original.getWidth() * original.getHeight() * freqThreshold);
			for (Iterator<Map.Entry<PngPixel, Integer>> it = colors.entrySet().iterator(); it.hasNext();) {
				final Entry<PngPixel, Integer> entry = it.next();
				if (entry.getValue() < minFreq) {
					it.remove();
				}
			}
		}
		log.debug("Filtered color count=%d", colors.size());

		final List<PngPixel> results = new ArrayList<>(colors.keySet());
		for (PngPixel pixel : results) {
			pixel.setFreq(colors.get(pixel));
		}

		return results;
	}

	private List<PngPixel> getMergedColors(PngImage image, List<PngPixel> colors, long start) {
		final int bits = image.getBitDepth();
		final List<PngPixel> copy = new ArrayList<>(colors);

		for (PngPixel pa : colors) {
			if (timeout > 0 && (System.currentTimeMillis() - start > timeout)) {
				throw new PngException("Reached " + timeout + "ms timeout");
			}

			if (!pa.isDuplicate()) {
				for (Iterator<PngPixel> it = copy.iterator(); it.hasNext();) {
					final PngPixel pb = it.next();

					if (pb.isDuplicate()) {
						it.remove();

					} else if (pa != pb && pa.rgbaDistance(pb, bits) < distThreshold) {
						if (pa.getFreq() > pb.getFreq()) {
							pb.setDuplicate(true);
							it.remove();
						} else {
							pa.setDuplicate(true);
						}
					}
				}
			}
		}

		final List<PngPixel> results = new ArrayList<>();
		for (PngPixel p : colors) {
			if (!p.isDuplicate()) {
				results.add(p);
			}
		}

		return results;
	}

	/**
	 * Holds image processing info
	 */
	public static class ColorCounterResult {
		private final String fileName;
		private final long width;
		private final long height;
		private final int totalColors;
		private final List<PngPixel> dominantColors;
		private final long elapsed;

		public ColorCounterResult(String fileName, long width, long height, int totalColors, List<PngPixel> dominantColors, long elapsed) {
			this.fileName = fileName;
			this.width = width;
			this.height = height;
			this.totalColors = totalColors;
			this.dominantColors = dominantColors;
			this.elapsed = elapsed;
		}

		@Override
		public String toString() {
			return "Filename: " + fileName + " " + width + "x" + height
					+ "\nCandidates: " + totalColors
					+ "\nDominant Colors: " + dominantColors.size()
					+ "\nColors: " + dominantColors.toString()
					+ "\nElapsed: " + elapsed + "ms\n";
		}

		public String getFileName() {
			return fileName;
		}
		public long getWidth() {
			return width;
		}
		public long getHeight() {
			return height;
		}
		public int getTotalColors() {
			return totalColors;
		}
		public List<PngPixel> getDominantColors() {
			return dominantColors;
		}
		public long getElapsed() {
			return elapsed;
		}
	}
}
