package com.googlecode.pngtastic.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Usage:
 * <code>
 *     byte[] bytes = new PngChunkInserter().insert(image, PngChunkInserter.dpi300Chunk);
 *     final File exported = image.export(toDir + "/name.png", bytes);
 * </code>
 *
 * @author ray
 */
public class PngChunkInserter {

	/**
	 * Conversion note: one inch is equal to exactly 0.0254 meters.
	 * 300dpi = 300 / 0.0254 = 11,811.023622 = 11811 = 0x2E23 = new byte[] { 0, 0, 46, 35 }
	 * http://comments.gmane.org/gmane.comp.graphics.png.general/2425
	 */
	private static final byte[] dpi300 = new byte[] { 0, 0, 46, 35, 0, 0, 46, 35, 1 };

	public static final PngChunk dpi300Chunk = new PngChunk(PngChunk.PHYSICAL_PIXEL_DIMENSIONS.getBytes(), dpi300);

	public byte[] insert(PngImage image, PngChunk chunk) throws IOException {
		// add it after the header chunk
		image.getChunks().add(1, chunk);

		final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
		image.writeDataOutputStream(outputBytes);

		return outputBytes.toByteArray();
	}
}
