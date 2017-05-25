package com.googlecode.pngtastic.core;

import com.googlecode.pngtastic.core.processing.PngByteArrayOutputStream;
import com.googlecode.pngtastic.core.processing.PngCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngFilterHandler;
import com.googlecode.pngtastic.core.processing.PngInterlaceHandler;
import com.googlecode.pngtastic.core.processing.PngtasticCompressionHandler;
import com.googlecode.pngtastic.core.processing.PngtasticFilterHandler;
import com.googlecode.pngtastic.core.processing.PngtasticInterlaceHandler;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.InflaterInputStream;

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

	protected PngByteArrayOutputStream getInflatedImageData(PngChunk chunk, Iterator<PngChunk> itChunks)
			throws IOException {

		final PngByteArrayOutputStream imageBytes = new PngByteArrayOutputStream(chunk == null ? 0 : chunk.getLength());
		try (final DataOutputStream imageData = new DataOutputStream(imageBytes)) {
			while (chunk != null) {
				if (PngChunk.IMAGE_DATA.equals(chunk.getTypeString())) {
					imageData.write(chunk.getData());
				} else {
					break;
				}
				chunk = itChunks.hasNext() ? itChunks.next() : null;
			}
			return inflate(imageBytes);
		}
	}

	/**
	 * Inflate (decompress) the compressed image data
	 *
	 * @param bytes A stream containing the compressed image data
	 * @return A byte array containing the uncompressed data
	 */
	public PngByteArrayOutputStream inflate(PngByteArrayOutputStream bytes) throws IOException {
		try (final PngByteArrayOutputStream inflatedOut = new PngByteArrayOutputStream();
		     final InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(bytes.get(), 0, bytes.len()))) {

			int readLength;
			final byte[] block = new byte[8192];

			while ((readLength = inflater.read(block)) != -1) {
				inflatedOut.write(block, 0, readLength);
			}
			return inflatedOut;
		}
	}

	protected List<byte[]> getScanlines(PngByteArrayOutputStream inflatedImageData, int sampleBitCount, int rowLength, long height) {
		final List<byte[]> rows = new ArrayList<>(Math.max((int) height, 0));
		byte[] previousRow = new byte[rowLength];

		for (int i = 0; i < height; i++) {
			final int offset = i * rowLength;
			final byte[] row = new byte[rowLength];
			System.arraycopy(inflatedImageData.get(), offset, row, 0, rowLength);
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

			if (result != null && chunk.isRequired()) {
				if (removeGamma && PngChunk.IMAGE_GAMA.equalsIgnoreCase(chunk.getTypeString())) {
					continue;
				}

				PngChunk newChunk = new PngChunk(chunk.getType(), chunk.getData().clone());
				if (PngChunk.IMAGE_HEADER.equals(chunk.getTypeString())) {
					newChunk.setInterlace((byte) 0);
				}
				result.addChunk(newChunk);
			}
		}
		return chunk;
	}
}
