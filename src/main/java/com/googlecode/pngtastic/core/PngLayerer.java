package com.googlecode.pngtastic.core;

import com.googlecode.pngtastic.core.processing.PngByteArrayOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Layers PNG images on top of one another. Currently expects two images of the same size.
 * Both images must be truecolor images, and the layer image (foreground) must have an alpha channel.
 *
 * @author rayvanderborght
 */
public class PngLayerer extends PngProcessor {

	/** */
	public PngLayerer() {
		this(Logger.NONE);
	}

	/** */
	public PngLayerer(String logLevel) {
		super(logLevel);
	}

	/** */
	public PngImage layer(PngImage baseImage, PngImage layerImage, Integer compressionLevel, boolean concurrent) throws IOException {
		log.debug("=== LAYERING: " + baseImage.getFileName() + ", " + layerImage.getFileName() + " ===");
		final long start = System.currentTimeMillis();

		// FIXME: support low bit depth interlaced images
		if (baseImage.getInterlace() == 1 && baseImage.getSampleBitCount() < 8) {
			return baseImage;
		}

		final PngImage result = new PngImage(log);
		result.setInterlace((short) 0);

		final Iterator<PngChunk> itBaseChunks = baseImage.getChunks().iterator();

		final PngChunk lastBaseChunk = processHeadChunks(new PngImage(), itBaseChunks);
		final PngByteArrayOutputStream inflatedBaseImageData = getInflatedImageData(lastBaseChunk, itBaseChunks);
		final List<byte[]> baseImageScanlines = getScanlines(baseImage, inflatedBaseImageData);

		final Iterator<PngChunk> itLayerChunks = layerImage.getChunks().iterator();

		final PngChunk lastLayerChunk = processHeadChunks(result, itLayerChunks);
		final PngByteArrayOutputStream inflatedLayerImageData = getInflatedImageData(lastLayerChunk, itLayerChunks);
		final List<byte[]> layerImageScanlines = getScanlines(layerImage, inflatedLayerImageData);

		final List<byte[]> newImageScanlines = doLayering(baseImage, layerImage, baseImageScanlines, layerImageScanlines);

		pngFilterHandler.applyFiltering(PngFilterType.NONE, newImageScanlines, layerImage.getSampleBitCount());

		final byte[] imageResult = pngCompressionHandler.deflate(serialize(newImageScanlines), compressionLevel, concurrent);

		final PngChunk imageChunk = new PngChunk(PngChunk.IMAGE_DATA.getBytes(), imageResult);
		result.addChunk(imageChunk);

		processTailChunks(result, itBaseChunks, lastBaseChunk);

		log.debug("Layered in %d milliseconds", (System.currentTimeMillis() - start));

		return result;
	}

	/* */
	private List<byte[]> getScanlines(PngImage image, PngByteArrayOutputStream inflatedImageData) {
		final int scanlineLength = Double.valueOf(Math.ceil(Long.valueOf(image.getWidth() * image.getSampleBitCount()) / 8F)).intValue() + 1;

		final List<byte[]> originalScanlines = (image.getInterlace() == 1)
				? pngInterlaceHandler.deInterlace((int)image.getWidth(), (int)image.getHeight(), image.getSampleBitCount(), inflatedImageData)
				: getScanlines(inflatedImageData, image.getSampleBitCount(), scanlineLength, image.getHeight());

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
				final ByteArrayOutputStream bytes = new ByteArrayOutputStream(chunk.getLength());
				final DataOutputStream data = new DataOutputStream(bytes);

				data.write(chunk.getData());
				data.close();

				final PngChunk newChunk = new PngChunk(chunk.getType(), bytes.toByteArray());
				if (PngChunk.IMAGE_HEADER.equals(chunk.getTypeString())) {
					newChunk.setInterlace((byte) 0);
				}
				result.addChunk(newChunk);
			}
		}
		return chunk;
	}

	/* */
	private PngChunk processTailChunks(PngImage result, Iterator<PngChunk> itBaseChunks, PngChunk lastBaseChunk) throws IOException {
		while (lastBaseChunk != null) {
			if (lastBaseChunk.isCritical()) {
				final ByteArrayOutputStream bytes = new ByteArrayOutputStream(lastBaseChunk.getLength());
				final DataOutputStream data = new DataOutputStream(bytes);

				data.write(lastBaseChunk.getData());
				data.close();

				final PngChunk newChunk = new PngChunk(lastBaseChunk.getType(), bytes.toByteArray());
				result.addChunk(newChunk);
			}
			lastBaseChunk = itBaseChunks.hasNext() ? itBaseChunks.next() : null;
		}
		return lastBaseChunk;
	}

	/* */
	private PngByteArrayOutputStream serialize(List<byte[]> scanlines) {
		final int scanlineLength = scanlines.get(0).length;
		final byte[] imageData = new byte[scanlineLength * scanlines.size()];

		for (int i = 0; i < scanlines.size(); i++) {
			final int offset = i * scanlineLength;
			final byte[] scanline = scanlines.get(i);
			System.arraycopy(scanline, 0, imageData, offset, scanlineLength);
		}

		return new PngByteArrayOutputStream(imageData);
	}

	/* */
	private List<byte[]> doLayering(PngImage baseImage, PngImage layerImage, List<byte[]> baseRows, List<byte[]> layerRows) throws IOException {
		final List<byte[]> result = new ArrayList<>(baseRows.size());

		final PngImageType baseImageType = PngImageType.forColorType(baseImage.getColorType());
		final PngImageType layerImageType = PngImageType.forColorType(layerImage.getColorType());
		final int sampleSize = baseImage.getSampleBitCount();

		for (int rowIndex = 0; rowIndex < baseRows.size(); rowIndex++) {
			final byte[] baseRow = baseRows.get(rowIndex);
			final byte[] layerRow = layerRows.get(rowIndex);
			final int sampleCount = ((baseRow.length - 1) * 8) / sampleSize;

			final ByteArrayInputStream baseIn = new ByteArrayInputStream(baseRow);
			final DataInputStream baseDin = new DataInputStream(baseIn);
			final int filterByte = baseDin.readUnsignedByte();

			final ByteArrayInputStream layerIn = new ByteArrayInputStream(layerRow);
			final DataInputStream layerDin = new DataInputStream(layerIn);
			layerDin.readUnsignedByte();	// skip filter byte

			final ByteArrayOutputStream outs = new ByteArrayOutputStream(baseRow.length);
			final DataOutputStream dos = new DataOutputStream(outs);
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
