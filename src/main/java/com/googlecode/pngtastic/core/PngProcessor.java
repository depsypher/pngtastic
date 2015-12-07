package com.googlecode.pngtastic.core;

import com.googlecode.pngtastic.core.processing.PngCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngFilterHandler;
import com.googlecode.pngtastic.core.processing.PngInterlaceHandler;
import com.googlecode.pngtastic.core.processing.PngtasticCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngtasticFilterHandler;
import com.googlecode.pngtastic.core.processing.PngtasticInterlaceHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base class for png image processing
 *
 * @author ray
 */
public abstract class PngProcessor {

	protected final Logger log;
	protected final PngFilterHandler pngFilterHandler;
	protected final PngInterlaceHandler pngInterlaceHandler;

	protected PngCompressionHandler pngCompressionHandler;

	protected PngProcessor(String logLevel) {
		this.log = new Logger(logLevel);
		this.pngFilterHandler = new PngtasticFilterHandler(log);
		this.pngInterlaceHandler = new PngtasticInterlaceHandler(log, pngFilterHandler);
		this.pngCompressionHandler = new PngtasticCompressionHandler(log);

	}

	protected byte[] getInflatedImageData(PngChunk chunk, Iterator<PngChunk> itChunks) throws IOException {
		final ByteArrayOutputStream imageBytes = new ByteArrayOutputStream(chunk == null ? 0 : chunk.getLength());
		final DataOutputStream imageData = new DataOutputStream(imageBytes);
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

	protected List<byte[]> getScanlines(byte[] inflatedImageData, int sampleBitCount, int rowLength, long height) {
		final List<byte[]> rows = new ArrayList<>(Math.max((int) height, 0));
		byte[] previousRow = new byte[rowLength];

		for (int i = 0; i < height; i++) {
			final int offset = i * rowLength;
			final byte[] row = new byte[rowLength];
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

	protected PngChunk processHeadChunks(PngImage result, boolean removeGamma, Iterator<PngChunk> itChunks) throws IOException {
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
}
