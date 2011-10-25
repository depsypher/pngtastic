package com.googlecode.pngtastic.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.googlecode.pngtastic.core.PngOptimizer.Stats;
import com.googlecode.pngtastic.core.processing.PngCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngFilterHandler;
import com.googlecode.pngtastic.core.processing.PngInterlaceHandler;
import com.googlecode.pngtastic.core.processing.PngtasticCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngtasticFilterHandler;
import com.googlecode.pngtastic.core.processing.PngtasticInterlaceHandler;


/**
 * Layers PNG images on top of one another. Currently expects two images of the
 * same size. Both images must be truecolor images, and the layer image
 * (foreground) must have an alpha channel.
 *
 * @author rayvanderborght
 */
public class PngLayerer {

	/** */
	private final Logger log;

	/** */
	private PngFilterHandler pngFilterHandler;
	private PngInterlaceHandler pngInterlaceHander;
	private PngCompressionHandler pngCompressionHandler;

	/** */
	private final List<Stats> stats = new ArrayList<Stats>();
	public List<Stats> getStats() { return this.stats; }

	/** */
	public PngLayerer() {
		this(Logger.NONE);
	}

	/** */
	public PngLayerer(String logLevel) {
		this.log = new Logger(logLevel);
		this.pngFilterHandler = new PngtasticFilterHandler(this.log);
		this.pngInterlaceHander = new PngtasticInterlaceHandler(this.log, this.pngFilterHandler);
		this.pngCompressionHandler = new PngtasticCompressionHandler(this.log);
	}

	/** */
	public PngImage layer(PngImage baseImage, PngImage layerImage, String outputFileName, Integer compressionLevel) throws FileNotFoundException, IOException {
		this.log.debug("=== LAYERING: " + baseImage.getFileName() + ", " + layerImage.getFileName() + " ===");

		long start = System.currentTimeMillis();
		PngImage outputImage = this.layer(baseImage, layerImage, compressionLevel, true);

		ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
		outputImage.writeDataOutputStream(outputBytes);

		PngImage results = new PngImage(new ByteArrayInputStream(outputBytes.toByteArray()));
		results.setFileName(outputFileName);

		long time = System.currentTimeMillis() - start;
		this.log.debug("Layered in %d milliseconds", time);

		return results;
	}

	/** */
	public PngImage layer(PngImage baseImage, PngImage layerImage, Integer compressionLevel, boolean concurrent) throws IOException {
		// FIXME: support low bit depth interlaced images
		if (baseImage.getInterlace() == 1 && baseImage.getSampleBitCount() < 8) {
            return baseImage;
        }

		PngImage result = new PngImage(this.log);
		result.setInterlace((short) 0);

		Iterator<PngChunk> itBaseChunks = baseImage.getChunks().iterator();

		PngChunk lastBaseChunk = this.processHeadChunks(new PngImage(), itBaseChunks);
		byte[] inflatedBaseImageData = this.getInflatedImageData(lastBaseChunk, itBaseChunks);
		List<byte[]> baseImageScanlines = this.getScanlines(baseImage, inflatedBaseImageData);

		Iterator<PngChunk> itLayerChunks = layerImage.getChunks().iterator();

		PngChunk lastLayerChunk = this.processHeadChunks(result, itLayerChunks);
		byte[] inflatedLayerImageData = this.getInflatedImageData(lastLayerChunk, itLayerChunks);
		List<byte[]> layerImageScanlines = this.getScanlines(layerImage, inflatedLayerImageData);

		List<byte[]> newImageScanlines = this.doLayering(baseImage, layerImage, baseImageScanlines, layerImageScanlines);

		PngFilterType filterType = PngFilterType.NONE;
		this.log.debug("Applying filter: %s", filterType);
		List<byte[]> scanlines = this.copyScanlines(newImageScanlines);
		this.pngFilterHandler.applyFiltering(filterType, scanlines, layerImage.getSampleBitCount());

		byte[] deflatedImageData = null;
		byte[] imageResult = this.pngCompressionHandler.deflate(this.serialize(scanlines), compressionLevel, concurrent);
		if (deflatedImageData == null || imageResult.length < deflatedImageData.length) {
			deflatedImageData = imageResult;
		}

		PngChunk imageChunk = new PngChunk(PngChunk.IMAGE_DATA.getBytes(), deflatedImageData);
		result.addChunk(imageChunk);

		// finish it
		while (lastBaseChunk != null) {
			if (lastBaseChunk.isCritical()) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(lastBaseChunk.getLength());
				DataOutputStream data = new DataOutputStream(bytes);

				data.write(lastBaseChunk.getData());
				data.close();

				PngChunk newChunk = new PngChunk(lastBaseChunk.getType(), bytes.toByteArray());
				result.addChunk(newChunk);
			}
			lastBaseChunk = itBaseChunks.hasNext() ? itBaseChunks.next() : null;
		}

		return result;
	}

	/* */
	private List<byte[]> getScanlines(PngImage image, byte[] inflatedImageData) {
		int scanlineLength = Double.valueOf(Math.ceil(Long.valueOf(image.getWidth() * image.getSampleBitCount()) / 8F)).intValue() + 1;

		List<byte[]> originalScanlines = (image.getInterlace() == 1)
				? this.pngInterlaceHander.deInterlace((int)image.getWidth(), (int)image.getHeight(), image.getSampleBitCount(), inflatedImageData)
				: this.getScanlines(inflatedImageData, image.getSampleBitCount(), scanlineLength, image.getHeight());

		return originalScanlines;
	}

	/* */
	private PngChunk processHeadChunks(PngImage result, Iterator<PngChunk> itChunks) throws IOException {
		PngChunk chunk = null;
		while (itChunks.hasNext()) {
			chunk = itChunks.next();
			if (PngChunk.IMAGE_DATA.equals(chunk.getTypeString())) {
                break;
            }

			if (chunk.isRequired()) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(chunk.getLength());
				DataOutputStream data = new DataOutputStream(bytes);

				data.write(chunk.getData());
				data.close();

				PngChunk newChunk = new PngChunk(chunk.getType(), bytes.toByteArray());
				if (PngChunk.IMAGE_HEADER.equals(chunk.getTypeString())) {
					newChunk.setInterlace((byte)0);
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

		return this.pngCompressionHandler.inflate(imageBytes);
	}

	/* */
	private List<byte[]> getScanlines(byte[] inflatedImageData, int sampleBitCount, int rowLength, long height) {
		this.log.debug("Getting scanlines");

		List<byte[]> rows = new ArrayList<byte[]>(Math.max((int)height, 0));
		byte[] previousRow = new byte[rowLength];

		for (int i = 0; i < height; i++) {
			int offset = i * rowLength;
			byte[] row = new byte[rowLength];
			System.arraycopy(inflatedImageData, offset, row, 0, rowLength);
			try {
				this.pngFilterHandler.deFilter(row, previousRow, sampleBitCount);
				rows.add(row);

				previousRow = row.clone();
			} catch (PngException e) {
				this.log.error("Error: %s", e.getMessage());
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
	private List<byte[]> doLayering(PngImage baseImage, PngImage layerImage, List<byte[]> baseRows, List<byte[]> layerRows) throws IOException {
		List<byte[]> result = new ArrayList<byte[]>(baseRows.size());

		PngImageType baseImageType = PngImageType.forColorType(baseImage.getColorType());
		PngImageType layerImageType = PngImageType.forColorType(layerImage.getColorType());
		int sampleSize = baseImage.getSampleBitCount();

		for (int rowIndex = 0; rowIndex < baseRows.size(); rowIndex++) {
			byte[] baseRow = baseRows.get(rowIndex);
			byte[] layerRow = layerRows.get(rowIndex);
			int sampleCount = ((baseRow.length - 1) * 8) / sampleSize;

			ByteArrayInputStream baseIn = new ByteArrayInputStream(baseRow);
			DataInputStream baseDin = new DataInputStream(baseIn);
			int filterByte = baseDin.readUnsignedByte();

			ByteArrayInputStream layerIn = new ByteArrayInputStream(layerRow);
			DataInputStream layerDin = new DataInputStream(layerIn);
			layerDin.readUnsignedByte();	// skip filter byte

			ByteArrayOutputStream outs = new ByteArrayOutputStream(baseRow.length);
			DataOutputStream dos = new DataOutputStream(outs);
			dos.writeByte(filterByte);

			for (int i = 0; i < sampleCount; i++) {

				// Zero alpha represents a completely transparent pixel,
				// maximum alpha represents a completely opaque pixel.
				int baseRed, baseGreen, baseBlue, baseAlpha = 0;
				int layerRed, layerGreen, layerBlue, layerAlpha = 0;

				if (baseImage.getBitDepth() == 8) {
					baseRed = baseDin.readUnsignedByte();
					baseGreen = baseDin.readUnsignedByte();
					baseBlue = baseDin.readUnsignedByte();
				} else {
					baseRed = baseDin.readUnsignedShort();
					baseGreen = baseDin.readUnsignedShort();
					baseBlue = baseDin.readUnsignedShort();
				}

				if (baseImageType == PngImageType.TRUECOLOR_ALPHA) {
					baseAlpha = (baseImage.getBitDepth() == 8)
						? baseDin.readUnsignedByte()
						: baseDin.readUnsignedShort();
				} else {
					baseAlpha = 255;
				}

				if (layerImage.getBitDepth() == 8) {
					layerRed = layerDin.readUnsignedByte();
					layerGreen = layerDin.readUnsignedByte();
					layerBlue = layerDin.readUnsignedByte();
				} else {
					layerRed = layerDin.readUnsignedShort();
					layerGreen = layerDin.readUnsignedShort();
					layerBlue = layerDin.readUnsignedShort();
				}

				if (layerImageType == PngImageType.TRUECOLOR_ALPHA) {
					layerAlpha = (layerImage.getBitDepth() == 8)
						? layerDin.readUnsignedByte()
						: layerDin.readUnsignedShort();
				}

				if (layerAlpha == 0) {
                    dos.writeByte(baseRed);
                    dos.writeByte(baseGreen);
                    dos.writeByte(baseBlue);
                    dos.writeByte(baseAlpha);
				} else {
				    dos.writeByte((baseRed * (255 - layerAlpha) + layerRed * layerAlpha) / 255);
				    dos.writeByte((baseGreen * (255 - layerAlpha) + layerGreen * layerAlpha) / 255);
				    dos.writeByte((baseBlue * (255 - layerAlpha) + layerBlue * layerAlpha) / 255);
				    dos.writeByte(255);
				}
			}
			dos.flush();
			result.add(outs.toByteArray());
		}
		return result;
	}
}
