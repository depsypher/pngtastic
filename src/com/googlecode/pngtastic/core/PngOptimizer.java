/*
 * $Id$
 * $URL$
 */
package com.googlecode.pngtastic.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Optimizes PNG images for smallest possible filesize.
 *
 * @author rayvanderborght
 */
public class PngOptimizer
{
	/** */
	private final Logger log;

	/** */
	private static final List<Integer> compressionStrategies = Arrays.asList(Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY);

	/** */
	private final List<Stats> stats = new ArrayList<Stats>();
	public List<Stats> getStats() { return this.stats; }

	/** */
	public PngOptimizer()
	{
		this(Logger.NONE);
	}

	/** */
	public PngOptimizer(String logLevel)
	{
		this.log = new Logger(logLevel);
	}

	/** */
	public void optimize(PngImage image, String outputFileName) throws FileNotFoundException, IOException
	{
		this.log.debug("=== OPTIMIZING ===");

		long start = System.currentTimeMillis();
		PngImage optimized = this.optimize(image);

		ByteArrayOutputStream optimizedBytes = new ByteArrayOutputStream();
		DataOutputStream output = optimized.writeDataOutputStream(optimizedBytes);

		File originalFile = new File(image.getFileName());
		long originalFileSize = originalFile.length();

		File exported = null;
		if (output.size() < originalFileSize)
		{
			exported = optimized.export(outputFileName, optimizedBytes.toByteArray());
		}
		else
		{
			ByteBuffer buffer = ByteBuffer.allocate((int)originalFileSize);
			FileInputStream ins = null;
			try
			{
				ins = new FileInputStream(originalFile);
				ins.getChannel().read(buffer);
			}
			finally
			{
				if (ins != null)
					ins.close();
			}
			exported = new File(outputFileName);
			optimized.writeFileOutputStream(exported, buffer.array());
		}
		long optimizedFileSize = exported.length();

		long time = System.currentTimeMillis() - start;

		this.log.debug("Optimized in %d milliseconds", time);
		this.log.debug("Original length in bytes: %d (%s)", originalFileSize, image.getFileName());
		this.log.debug("Final length in bytes: %d (%s)", optimizedFileSize, outputFileName);

		if (optimizedFileSize <= originalFileSize)
			this.log.info("%5.2f%% :%6dB ->%6dB (%5dB saved) - %s", (originalFileSize - optimizedFileSize) / Float.valueOf(originalFileSize) * 100, originalFileSize, optimizedFileSize, originalFileSize - optimizedFileSize, outputFileName);
		else
			this.log.info("%5.2f%% :%6dB ->%6dB (%5dB saved) - %s", -(optimizedFileSize - originalFileSize) / Float.valueOf(originalFileSize) * 100, originalFileSize, optimizedFileSize, -(optimizedFileSize - originalFileSize), outputFileName);

		this.stats.add(new Stats(originalFileSize, optimizedFileSize));
	}

	/** */
	public PngImage optimize(PngImage image) throws IOException
	{
		// FIXME: support interlaced images
		if (image.getInterlace() == 1)
			return image;

		// FIXME: support greyscale alpha
		if (image.getColorType() == 4)
			return image;

		PngImage result = new PngImage(this.log);

		Iterator<PngChunk> itChunks = image.getChunks().iterator();
		PngChunk chunk = null;
		while (itChunks.hasNext())
		{
			chunk = itChunks.next();
			if (PngChunk.IMAGE_DATA.equals(chunk.getTypeString()))
				break;

			if (chunk.isRequired())
			{
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(chunk.getLength());
				DataOutputStream data = new DataOutputStream(bytes);

				data.write(chunk.getData());
				data.close();

				PngChunk newChunk = new PngChunk(chunk.getType(), bytes.toByteArray());
				result.addChunk(newChunk);
			}
		}

		// collect image data chunks
		ByteArrayOutputStream imageBytes = new ByteArrayOutputStream(chunk.getLength());
		DataOutputStream imageData = new DataOutputStream(imageBytes);
		while (chunk != null)
		{
			if (PngChunk.IMAGE_DATA.equals(chunk.getTypeString()))
			{
				imageData.write(chunk.getData());
			}
			else
			{
				break;
			}
			chunk = itChunks.hasNext() ? itChunks.next() : null;
		}
		imageData.close();

		byte[] inflatedImageData = this.inflateImageData(imageBytes);

		int scanlineLength = Double.valueOf(Math.ceil(Long.valueOf(image.getWidth() * image.getSampleBitCount()) / 8F)).intValue() + 1;
		List<byte[]> originalScanlines = this.getScanlines(inflatedImageData, image.getSampleBitCount(), scanlineLength, image.getHeight());

		// TODO: use this for bit depth reduction
//		this.getColors(image, originalScanlines);

		// apply each type of filtering
		Map<PngFilterType, List<byte[]>> filteredScanlines = new HashMap<PngFilterType, List<byte[]>>();
		for (PngFilterType filterType : PngFilterType.standardValues())
		{
			this.log.debug("Applying filter: %s", filterType);
			List<byte[]> scanlines = this.copyScanlines(originalScanlines);
			this.applyFiltering(filterType, scanlines, image.getSampleBitCount());

			filteredScanlines.put(filterType, scanlines);
		}

		// pick the filter that compresses best
		PngFilterType bestFilterType = null;
		byte[] deflatedImageData = null;
		for (Entry<PngFilterType, List<byte[]>> entry : filteredScanlines.entrySet())
		{
			byte[] imageResult = this.deflateImageData(this.serialize(entry.getValue()));
			if (deflatedImageData == null || imageResult.length < deflatedImageData.length)
			{
				deflatedImageData = imageResult;
				bestFilterType = entry.getKey();
			}
		}

		// see if adaptive filtering results in even better compression
		List<byte[]> scanlines = this.copyScanlines(originalScanlines);
		this.applyAdaptiveFiltering(inflatedImageData, scanlines, filteredScanlines, image.getSampleBitCount());

		byte[] adaptiveImageData = this.deflateImageData(inflatedImageData);
		if (deflatedImageData == null || adaptiveImageData.length < deflatedImageData.length)
		{
			deflatedImageData = adaptiveImageData;
			bestFilterType = PngFilterType.ADAPTIVE;
			this.log.debug("Adaptive=%d, Other=%d", adaptiveImageData.length, deflatedImageData.length);
		}

		this.log.debug("Best filter type: %s", bestFilterType);

		PngChunk imageChunk = new PngChunk(PngChunk.IMAGE_DATA.getBytes(), deflatedImageData);
		result.addChunk(imageChunk);

		// finish it
		while (chunk != null)
		{
			if (chunk.isCritical())
			{
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(chunk.getLength());
				DataOutputStream data = new DataOutputStream(bytes);

				data.write(chunk.getData());
				data.close();

				PngChunk newChunk = new PngChunk(chunk.getType(), bytes.toByteArray());
				result.addChunk(newChunk);
			}
			chunk = itChunks.hasNext() ? itChunks.next() : null;
		}

		return result;
	}

	/* */
	private List<byte[]> getScanlines(byte[] inflatedImageData, int sampleBitCount, int rowLength, long height)
	{
		List<byte[]> rows = new ArrayList<byte[]>(Math.max((int)height, 0));
		byte[] previousRow = new byte[rowLength];

		for (int i = 0; i < height; i++)
		{
			int offset = i * rowLength;
			byte[] row = new byte[rowLength];
			System.arraycopy(inflatedImageData, offset, row, 0, rowLength);
			try
			{
				this.deFilter(row, previousRow, sampleBitCount);
				rows.add(row);

				previousRow = row.clone();
			}
			catch(PngException e)
			{
				e.printStackTrace();
			}
		}
		return rows;
	}

	/* */
	private List<byte[]> copyScanlines(List<byte[]> original)
	{
		List<byte[]> copy = new ArrayList<byte[]>(original.size());
		for (byte[] scanline : original)
			copy.add(scanline.clone());

		return copy;
	}

	/* */
	private void applyFiltering(PngFilterType filterType, List<byte[]> scanlines, int sampleBitCount)
	{
		int i = 0;
		int scanlineLength = scanlines.get(0).length;
		byte[] previousRow = new byte[scanlineLength];
		for (byte[] scanline : scanlines)
		{
			if (filterType != null)
				scanline[0] = filterType.getValue();

			byte[] previous = scanline.clone();

			try
			{
				this.filter(scanline, previousRow, sampleBitCount);
			}
			catch (PngException e)
			{
				this.log.info("Error during filtering: %s", e.getMessage());
			}
			previousRow = previous;
			i++;
		}
	}

	/* */
	private void applyAdaptiveFiltering(byte[] inflatedImageData, List<byte[]> scanlines, Map<PngFilterType, List<byte[]>> filteredScanLines, int sampleSize) throws IOException
	{
		for (int s = 0; s < scanlines.size(); s++)
		{
			long bestSum = Long.MAX_VALUE;
			PngFilterType bestFilterType = null;
			for (Map.Entry<PngFilterType, List<byte[]>> entry : filteredScanLines.entrySet())
			{
				long sum = 0;
				byte[] scanline = entry.getValue().get(s);
				for (int i = 1; i < scanline.length; i++)
					sum += Math.abs(scanline[i]);

				if (sum < bestSum)
				{
					bestFilterType = entry.getKey();
					bestSum = sum;
				}
			}
			scanlines.get(s)[0] = bestFilterType.getValue();
		}

		this.applyFiltering(null, scanlines, sampleSize);
	}

	/* */
	private byte[] serialize(List<byte[]> scanlines)
	{
		int scanlineLength = scanlines.get(0).length;
		byte[] imageData = new byte[scanlineLength * scanlines.size()];
		for (int i = 0; i < scanlines.size(); i++)
		{
			int offset = i * scanlineLength;
			byte[] scanline = scanlines.get(i);
			System.arraycopy(scanline, 0, imageData, offset, scanlineLength);
		}

		return imageData;
	}

	/*
	 * Do filtering as described in the png spec:
	 * The scanline starts with a filter type byte, then continues with the image data.
	 * The bytes are named as follows (x = current, a = previous, b = above, c = previous and above)
	 *
	 * c b
	 * a x
	 */
	private void filter(byte[] line, byte[] previousLine, int sampleBitCount) throws PngException
	{
		PngFilterType filterType = PngFilterType.forValue(line[0]);
		line[0] = 0;

		PngFilterType previousFilterType = PngFilterType.forValue(previousLine[0]);
		previousLine[0] = 0;

		switch (filterType)
		{
			case NONE:
				break;

			case SUB:
			{
				byte[] original = line.clone();
				int previous = -(Math.max(1, sampleBitCount / 8) - 1);
				for (int x = 1, a = previous; x < line.length; x++, a++)
					line[x] = (byte)(original[x] - ((a < 0) ? 0 : original[a]));
				break;
			}
			case UP:
			{
				for (int x = 1; x < line.length; x++)
					line[x] = (byte)(line[x] - previousLine[x]);
				break;
			}
			case AVERAGE:
			{
				byte[] original = line.clone();
				int previous = -(Math.max(1, sampleBitCount / 8) - 1);
				for (int x = 1, a = previous; x < line.length; x++, a++)
					line[x] = (byte)(original[x] - ((0xFF & original[(a < 0) ? 0 : a]) + (0xFF & previousLine[x])) / 2);
				break;
			}
			case PAETH:
			{
				byte[] original = line.clone();
				int previous = -(Math.max(1, sampleBitCount / 8) - 1);
				for (int x = 1, a = previous; x < line.length; x++, a++)
				{
					int result = this.paethPredictor(original, previousLine, x, a);
					line[x] = (byte)(original[x] - result);
				}
				break;
			}
			default:
				throw new PngException("Unrecognized filter type " + filterType);
		}
		line[0] = filterType.getValue();
		previousLine[0] = previousFilterType.getValue();
	}

	/*
	 * Do the opposite of PNG filtering:
	 * @see #filter(byte[], byte[], int)
	 */
	private void deFilter(byte[] line, byte[] previousLine, int sampleBitCount) throws PngException
	{
		PngFilterType filterType = PngFilterType.forValue(line[0]);
		line[0] = 0;

		PngFilterType previousFilterType = PngFilterType.forValue(previousLine[0]);
		previousLine[0] = 0;

		switch (filterType)
		{
			case SUB:
			{
				int previous = -(Math.max(1, sampleBitCount / 8) - 1);
				for (int x = 1, a = previous; x < line.length; x++, a++)
					line[x] = (byte)(line[x] + ((a < 0) ? 0 : line[a]));
				break;
			}
			case UP:
			{
				for (int x = 1; x < line.length; x++)
					line[x] = (byte) (line[x] + previousLine[x]);
				break;
			}
			case AVERAGE:
			{
				int previous = -(Math.max(1, sampleBitCount / 8) - 1);
				for (int x = 1, a = previous; x < line.length; x++, a++)
					line[x] = (byte)(line[x] + ((0xFF & ((a < 0) ? 0 : line[a])) + (0xFF & previousLine[x])) / 2);
				break;
			}
			case PAETH:
			{
				int previous = -(Math.max(1, sampleBitCount / 8) - 1);
				for (int x = 1, xp = previous; x < line.length; x++, xp++)
				{
					int result = this.paethPredictor(line, previousLine, x, xp);
					line[x] = (byte)(line[x] + result);
				}
				break;
			}
		}
		line[0] = filterType.getValue();
		previousLine[0] = previousFilterType.getValue();
	}

	/* */
	private int paethPredictor(byte[] line, byte[] previousLine, int x, int xp)
	{
		int a = 0xFF & ((xp < 0) ? 0 : line[xp]);
		int b = 0xFF & previousLine[x];
		int c = 0xFF & ((xp < 0) ? 0 : previousLine[xp]);
		int p = a + b - c;

		int pa = (p >= a) ? (p - a) : -(p - a);
		int pb = (p >= b) ? (p - b) : -(p - b);
		int pc = (p >= c) ? (p - c) : -(p - c);

		if (pa <= pb && pa <= pc)
			return a;

		return (pb <= pc) ? b : c;
	}


	/*
	 * Deflate (compress) the inflated data using the best compression level
	 */
	private byte[] deflateImageData(byte[] inflatedImageData) throws IOException
	{
		List<byte[]> results = this.deflateImageDataConcurrently(inflatedImageData, true);

		byte[] result = null;
		for (int i = 0; i < results.size(); i++)
		{
			byte[] data = results.get(i);
			if (result == null || (data.length < result.length))
				result = data;
		}
		this.log.debug("Image bytes=%d", result.length);

		return result;
	}

	/*
	 * Do the work of deflating (compressing) the image data with the
	 * different compression strategies in different threads to take
	 * advantage of multiple core architectures.
	 */
	private List<byte[]> deflateImageDataConcurrently(final byte[] inflatedImageData, final boolean bruteForceCompression)
	{
		final Collection<byte[]> results = new ConcurrentLinkedQueue<byte[]>();

		final Collection<Callable<Object>> tasks = new ArrayList<Callable<Object>>();
		for (final int strategy : compressionStrategies)
		{
			tasks.add(Executors.callable(new Runnable()
			{
				public void run()
				{
					try
					{
						results.add(PngOptimizer.this.deflateImageData(inflatedImageData, strategy, bruteForceCompression));
					}
					catch (Throwable e)
					{
						PngOptimizer.this.log.debug("Uncaught Exception", e);
					}
				}
			}));
		}

		ExecutorService compressionThreadPool = Executors.newFixedThreadPool(compressionStrategies.size());
		try
		{
			compressionThreadPool.invokeAll(tasks);
		}
		catch (InterruptedException ex) {  }
		finally
		{
			compressionThreadPool.shutdown();
		}

		return new ArrayList<byte[]>(results);
	}

	/* */
	private byte[] deflateImageData(byte[] inflatedImageData, int strategy, boolean bruteForceCompression) throws IOException
	{
		byte[] result = null;

		int compression = Deflater.BEST_COMPRESSION;
		int bestCompression = compression;
		while (compression > Deflater.NO_COMPRESSION)
		{
			ByteArrayOutputStream deflatedOut = new ByteArrayOutputStream();
			Deflater deflater = new Deflater(compression);
			deflater.setStrategy(strategy);

			DeflaterOutputStream stream = new DeflaterOutputStream(deflatedOut, deflater);
			stream.write(inflatedImageData);
			stream.close();

			if (result == null || (result.length > deflatedOut.size()))
			{
				result = deflatedOut.toByteArray();
				bestCompression = compression;
			}

			if (!bruteForceCompression)
				break;

			compression--;
		}

		this.log.debug("Compression strategy: %s, compression level=%d, bytes=%d", strategy, bestCompression, result.length);

		return result;
	}

	/*
	 * Inflate (decompress) the compressed image data
	 */
	private byte[] inflateImageData(ByteArrayOutputStream imageBytes) throws IOException
	{
		InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(imageBytes.toByteArray()));
		ByteArrayOutputStream inflatedOut = new ByteArrayOutputStream();

		int readLength;
		byte[] block = new byte[8192];
		while ((readLength = inflater.read(block)) != -1)
			inflatedOut.write(block, 0, readLength);

		byte[] inflatedImageData = inflatedOut.toByteArray();
		return inflatedImageData;
	}

	/* */
	@SuppressWarnings("unused")
	private Set<PngPixel> getColors(PngImage original, List<byte[]> rows) throws IOException
	{
		Set<PngPixel> colors = new HashSet<PngPixel>();
		PngImageType imageType = PngImageType.forColorType(original.getColorType());
		int sampleSize = original.getSampleBitCount();

		for (byte[] row : rows)
		{
			int sampleCount = ((row.length - 1) * 8) / sampleSize;
			ByteArrayInputStream ins = new ByteArrayInputStream(row);
			DataInputStream dis = new DataInputStream(ins);
			dis.readUnsignedByte();	// the filter byte

			for (int i = 0; i < sampleCount; i++)
			{
				switch (imageType)
				{
					case INDEXED_COLOR:
						// TODO: read pixels from palette
						break;

					case GREYSCALE:
					case GREYSCALE_ALPHA:
						// TODO: who knows
						break;

					case TRUECOLOR:
						if (original.getBitDepth() == 8)
						{
							int red = dis.readUnsignedByte();
							int green = dis.readUnsignedByte();
							int blue = dis.readUnsignedByte();
							colors.add(new PngPixel(red, green, blue));
						}
						else
						{
							int red = dis.readUnsignedShort();
							int green = dis.readUnsignedShort();
							int blue = dis.readUnsignedShort();
							colors.add(new PngPixel(red, green, blue));
						}
						break;

					case TRUECOLOR_ALPHA:
						if (original.getBitDepth() == 8)
						{
							int red = dis.readUnsignedByte();
							int green = dis.readUnsignedByte();
							int blue = dis.readUnsignedByte();
							int alpha = dis.readUnsignedByte();
							colors.add(new PngPixel(red, green, blue, alpha));
						}
						else
						{
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
	private static class PngPixel
	{
		private final int red;
		private final int green;
		private final int blue;
		private final int alpha;

		/** */
		public PngPixel(int red, int green, int blue)
		{
			this(red, green, blue, -1);
		}

		/** */
		public PngPixel(int red, int green, int blue, int alpha)
		{
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.alpha = alpha;
		}

		/** */
		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + this.alpha;
			result = prime * result + this.blue;
			result = prime * result + this.green;
			result = prime * result + this.red;
			return result;
		}

		/** */
		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;

			if (obj == null || this.getClass() != obj.getClass())
				return false;

			PngPixel other = (PngPixel) obj;
			if (this.alpha != other.alpha || this.blue != other.blue || this.green != other.green || this.red != other.red)
				return false;

			return true;
		}
	}

	/**
	 * Holds info about an image file optimization
	 *
	 * @author ray
	 */
	public static class Stats
	{
		/** */
		private long originalFileSize;
		public long getOriginalFileSize() { return this.originalFileSize; }

		/** */
		private long optimizedFileSize;
		public long getOptimizedFileSize() { return this.optimizedFileSize; }

		/** */
		public Stats(long originalFileSize, long optimizedFileSize)
		{
			this.originalFileSize = originalFileSize;
			this.optimizedFileSize = optimizedFileSize;
		}
	}

	/**
	 * Get the number of bytes saved in all images processed so far
	 *
	 * @return The number of bytes saved
	 */
	public long getTotalSavings()
	{
		long totalSavings = 0;
		for (PngOptimizer.Stats stat : this.getStats())
			totalSavings += (stat.getOriginalFileSize() - stat.getOptimizedFileSize());

		return totalSavings;
	}

	/* */
	@SuppressWarnings("unused")
	private void printData(byte[] inflatedImageData)
	{
		StringBuilder result = new StringBuilder();
		for (byte b : inflatedImageData)
			result.append(String.format("%2x|", b));
		this.log.debug(result.toString());
	}
}
