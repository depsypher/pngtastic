package com.googlecode.pngtastic.core;

import com.googlecode.pngtastic.core.processing.PngByteArrayOutputStream;
import com.googlecode.pngtastic.core.processing.PngInterlaceHandler;
import com.googlecode.pngtastic.core.processing.PngtasticInterlaceHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * @author rayvanderborght
 */
public class PngReader extends PngProcessor {

	private final PngInterlaceHandler pngInterlaceHandler;

	public PngReader() {
		this(Logger.NONE);
	}

	public PngReader(String logLevel) {
		super(logLevel);
		this.pngInterlaceHandler = new PngtasticInterlaceHandler(log, pngFilterHandler);
	}

	public static byte[] readRGBA8(final byte[] image) {
		try {
			return new PngReader().readRGBA8(new PngImage(image));
		} catch (final IOException e) {
			throw new PngException(e);
		}
	}

	/** */
	public byte[] readRGBA8(final PngImage image) throws IOException {
		log.debug("=== READING ===");

		// FIXME: support low bit depth interlaced images
		if (image.getInterlace() == 1 && image.getSampleBitCount() < 8) {
			throw new PngException("not supported");
		}

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

		final byte[] rgba = getRGBA8(image, originalScanlines);

		return rgba;
	}

	private byte[] getRGBA8(final PngImage original, final List<byte[]> rows) throws IOException {
		final PngImageType imageType = PngImageType.forColorType(original.getColorType());
		final int sampleSize = original.getSampleBitCount();

		final int perRow = ((rows.get(0).length - 1) * 8) / sampleSize;
		byte[] result = new byte[perRow * 4 * rows.size()];
		int y = 0;
		for (byte[] row : rows) {
			final int sampleCount = ((row.length - 1) * 8) / sampleSize;

			final byte[] normalized = normalize(row, original.getBitDepth());
			final ByteArrayInputStream ins = new ByteArrayInputStream(normalized);
			final DataInputStream dis = new DataInputStream(ins);

			for (int x = 0; x < sampleCount; x++) {
				switch (imageType) {
					case INDEXED_COLOR: {
						final int offset = dis.readUnsignedByte() * 3;
						final int r = original.getPalette().getUnsignedByte(offset);
						final int g = original.getPalette().getUnsignedByte(offset + 1);
						final int b = original.getPalette().getUnsignedByte(offset + 2);

						final int index = (y * perRow + x) * 4;
						result[index] = (byte) r;
						result[index + 1] = (byte) g;
						result[index + 2] = (byte) b;
						result[index + 3] = (byte) 255;
						break;
					}

					case GREYSCALE: {
						if (original.getBitDepth() == 16) {
							final int p = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int v = Integer.divideUnsigned(p, 65535) + (Integer.remainderUnsigned(p, 65535) > 32767 ? 1 : 0);
							final int index = (y * perRow + x) * 4;
							result[index]     = (byte) v;
							result[index + 1] = (byte) v;
							result[index + 2] = (byte) v;
							result[index + 3] = (byte) 255;
						} else {
							final int p = dis.readUnsignedByte() * 255;
							final int max = (int) (Math.pow(2, original.getBitDepth()) - 1);
							final int v = Integer.divideUnsigned(p, max) + (Integer.remainderUnsigned(p, max) > (max / 2) ? 1 : 0);
							final int index = (y * perRow + x) * 4;
							result[index] = (byte) v;
							result[index + 1] = (byte) v;
							result[index + 2] = (byte) v;
							result[index + 3] = (byte) 255;
						}
						break;
					}

					case GREYSCALE_ALPHA: {
						if (original.getBitDepth() == 8) {
							final int p = dis.readUnsignedByte() * 255;
							final int a = dis.readUnsignedByte() * 255;
							final int max = (int) (Math.pow(2, original.getBitDepth()) - 1);
							final int v = Integer.divideUnsigned(p, max) + (Integer.remainderUnsigned(p, max) > max / 2 ? 1 : 0);
							final int index = (y * perRow + x) * 4;
							result[index] = (byte) v;
							result[index + 1] = (byte) v;
							result[index + 2] = (byte) v;
							result[index + 3] = (byte) (Integer.divideUnsigned(a, max) + (Integer.remainderUnsigned(a, max) > max / 2 ? 1 : 0));
						} else if (original.getBitDepth() == 16) {
							final int p = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int a = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int v = Integer.divideUnsigned(p, 65535) + (Integer.remainderUnsigned(p, 65535) > 32767 ? 1 : 0);
							final int index = (y * perRow + x) * 4;
							result[index]     = (byte) v;
							result[index + 1] = (byte) v;
							result[index + 2] = (byte) v;
							result[index + 3] = (byte) (Integer.divideUnsigned(a, 65535) + (Integer.remainderUnsigned(a, 65535) > 32767 ? 1 : 0));
						}
						break;
					}

					case TRUECOLOR: {
						if (original.getBitDepth() == 8) {
							final int r = dis.readUnsignedByte();
							final int g = dis.readUnsignedByte();
							final int b = dis.readUnsignedByte();
							final int index = (y * perRow + x) * 4;
							result[index] = (byte) r;
							result[index + 1] = (byte) g;
							result[index + 2] = (byte) b;
							result[index + 3] = (byte) 255;
						} else if (original.getBitDepth() == 16) {
							final int r = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int g = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int b = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int index = (y * perRow + x) * 4;
							result[index]     = (byte) (Integer.divideUnsigned(r, 65535) + (Integer.remainderUnsigned(r, 65535) > 32767 ? 1 : 0));
							result[index + 1] = (byte) (Integer.divideUnsigned(g, 65535) + (Integer.remainderUnsigned(g, 65535) > 32767 ? 1 : 0));
							result[index + 2] = (byte) (Integer.divideUnsigned(b, 65535) + (Integer.remainderUnsigned(b, 65535) > 32767 ? 1 : 0));
							result[index + 3] = (byte) 255;
						}
						break;
					}

					case TRUECOLOR_ALPHA: {
						if (original.getBitDepth() == 16) {
							final int r = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int g = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int b = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int a = ((dis.readUnsignedByte() << 8) + dis.readUnsignedByte()) * 255;
							final int index = (y * perRow + x) * 4;
							result[index]     = (byte) (Integer.divideUnsigned(r, 65535) + (Integer.remainderUnsigned(r, 65535) > 32767 ? 1 : 0));
							result[index + 1] = (byte) (Integer.divideUnsigned(g, 65535) + (Integer.remainderUnsigned(g, 65535) > 32767 ? 1 : 0));
							result[index + 2] = (byte) (Integer.divideUnsigned(b, 65535) + (Integer.remainderUnsigned(b, 65535) > 32767 ? 1 : 0));
							result[index + 3] = (byte) (Integer.divideUnsigned(a, 65535) + (Integer.remainderUnsigned(a, 65535) > 32767 ? 1 : 0));
						} else {
							final int r = dis.readUnsignedByte();
							final int g = dis.readUnsignedByte();
							final int b = dis.readUnsignedByte();
							final int a = dis.readUnsignedByte();
							final int index = (y * perRow + x) * 4;
							result[index] = (byte) r;
							result[index + 1] = (byte) g;
							result[index + 2] = (byte) b;
							result[index + 3] = (byte) a;
						}
						break;
					}

					default:
						throw new IllegalArgumentException();
				}
			}
			y++;
		}

		return result;
	}

	/**
	 * normalize bit depths less than 8 to all fit into one byte per sample
	 */
	private byte[] normalize(final byte[] imageData, final int bitDepth) {
		byte[] scaled;

		int length = imageData.length - 1; // remove filter byte
		switch (bitDepth) {
			default:
				throw new PngException("unrecognised depth");
			case 16:
			case 8:
				scaled = new byte[length];
				System.arraycopy(imageData, 1, scaled, 0, length);
				return scaled;
			case 4:
				scaled = new byte[length * 2];
				break;
			case 2:
				scaled = new byte[length * 4];
				break;
			case 1:
				scaled = new byte[length * 8];
				break;
		}

		int i = 0;
		while (i < length) {
			byte byte8, byte7, byte6, byte5, byte4, byte3, byte2, byte1;
			byte b = imageData[i + 1];	// skip filter byte
			switch (bitDepth) {
				case 4:
					byte2 = (byte) (b & 0x0f);
					byte1 = (byte) (b >> 4);
					scaled[i * 2] = byte1;
					scaled[i * 2 + 1] = byte2;
					break;
				case 2:
					byte4 = (byte) (b & 3);
					byte3 = (byte) (b >> 2 & 3);
					byte2 = (byte) (b >> 4 & 3);
					byte1 = (byte) (b >> 6 & 3);
					scaled[i * 4] = byte1;
					scaled[i * 4 + 1] = byte2;
					scaled[i * 4 + 2] = byte3;
					scaled[i * 4 + 3] = byte4;
					break;
				case 1:
					byte8 = (byte) (b & 1);
					byte7 = (byte) (b >> 1 & 1);
					byte6 = (byte) (b >> 2 & 1);
					byte5 = (byte) (b >> 3 & 1);
					byte4 = (byte) (b >> 4 & 1);
					byte3 = (byte) (b >> 5 & 1);
					byte2 = (byte) (b >> 6 & 1);
					byte1 = (byte) (b >> 7 & 1);
					scaled[i * 8] = byte1;
					scaled[i * 8 + 1] = byte2;
					scaled[i * 8 + 2] = byte3;
					scaled[i * 8 + 3] = byte4;
					scaled[i * 8 + 4] = byte5;
					scaled[i * 8 + 5] = byte6;
					scaled[i * 8 + 6] = byte7;
					scaled[i * 8 + 7] = byte8;
					break;
			}
			i += 1;
		}
		return scaled;
	}
}
