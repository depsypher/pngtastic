package com.googlecode.pngtastic.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a png image
 *
 * @author rayvanderborght
 */
public class PngImage {

	private final Logger log;

	public static final long SIGNATURE = 0x89504e470d0a1a0aL;

	private String fileName;
	public String getFileName() { return this.fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }

	private List<PngChunk> chunks = new ArrayList<>();
	public List<PngChunk> getChunks() { return this.chunks; }

	private long width;
	public long getWidth() { return this.width; }

	private long height;
	public long getHeight() { return this.height; }

	private short bitDepth;
	public short getBitDepth() { return this.bitDepth; }

	private short colorType;
	public short getColorType() { return this.colorType; }

	private short interlace;
	public short getInterlace() { return this.interlace; }
	public void setInterlace(short interlace) { this.interlace = interlace; }

	private PngChunk palette;
	public PngChunk getPalette() { return palette; }

	private PngImageType imageType;

	/** */
	public PngImage() {
		this.log = new Logger(Logger.NONE);
	}

	/** */
	public PngImage(Logger log) {
		this.log = log;
	}

	/** */
	public PngImage(String fileName, String logLevel) throws FileNotFoundException {
		this(new BufferedInputStream(new FileInputStream(fileName)), logLevel);
		this.fileName = fileName;
	}

	/** */
	public PngImage(InputStream ins) {
		this(ins, null);
	}

	/** */
	public PngImage(InputStream ins, String logLevel) {
		this(new Logger(logLevel));

		try {
			DataInputStream dis = new DataInputStream(ins);
			readSignature(dis);

			int length;
			PngChunk chunk;

			do {
				length = getChunkLength(dis);

				byte[] type = getChunkType(dis);
				byte[] data = getChunkData(dis, length);
				long crc = getChunkCrc(dis);

				chunk = new PngChunk(type, data);

//				log.debug("chunk: " + chunk.getTypeString());
//				if ("pHYs".equals(chunk.getTypeString())) {
//					for (byte x : chunk.getData())
//						log.debug("data: " + x + "," + String.format("%x", x));
//				}

				if (!chunk.verifyCRC(crc)) {
					throw new PngException("Corrupted file, crc check failed");
				}

				addChunk(chunk);
			} while (length > 0 && !PngChunk.IMAGE_TRAILER.equals(chunk.getTypeString()));

		} catch (IOException e) {
			throw new PngException("Error: " + e.getMessage(), e);
		}
	}

	/** */
	public File export(String fileName, byte[] bytes) throws IOException {
		File out = new File(fileName);
		writeFileOutputStream(out, bytes);

		return out;
	}

	/** */
	FileOutputStream writeFileOutputStream(File out, byte[] bytes) throws IOException {
		FileOutputStream outs = null;
		try {
			outs = new FileOutputStream(out);
			outs.write(bytes);
		} finally {
			if (outs != null) {
				outs.close();
			}
		}

		return outs;
	}

	/** */
	public DataOutputStream writeDataOutputStream(OutputStream output) throws IOException {
		DataOutputStream outs = new DataOutputStream(output);
		outs.writeLong(PngImage.SIGNATURE);

		for (PngChunk chunk : chunks) {
			log.debug("export: %s", chunk.toString());
			outs.writeInt(chunk.getLength());
			outs.write(chunk.getType());
			outs.write(chunk.getData());
			int i = (int)chunk.getCRC();
			outs.writeInt(i);
		}
		outs.close();

		return outs;
	}

	/** */
	public void addChunk(PngChunk chunk) {
		switch (chunk.getTypeString()) {
			case PngChunk.IMAGE_HEADER:
				this.width = chunk.getWidth();
				this.height = chunk.getHeight();
				this.bitDepth = chunk.getBitDepth();
				this.colorType = chunk.getColorType();
				this.interlace = chunk.getInterlace();
				break;

			case PngChunk.PALETTE:
				this.palette = chunk;
				break;
		}

		this.chunks.add(chunk);
	}

	/** */
	public byte[] getImageData() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();

			// Write all the IDAT data
			for (PngChunk chunk : chunks) {
				if (chunk.getTypeString().equals(PngChunk.IMAGE_DATA)) {
					out.write(chunk.getData());
				}
			}
			return out.toByteArray();
		} catch (IOException e) {
			System.out.println("Couldn't get image data: " + e);
		}
		return null;
	}

	/** */
	public int getSampleBitCount() {
		this.imageType = (this.imageType == null) ? PngImageType.forColorType(this.colorType) : this.imageType;
		return this.imageType.channelCount() * this.bitDepth;
	}

	/* */
	private int getChunkLength(DataInputStream ins) throws IOException {
		return ins.readInt();
	}

	/* */
	private byte[] getChunkType(InputStream ins) throws PngException {
		return getChunkData(ins, 4);
	}

	/* */
	private byte[] getChunkData(InputStream ins, int length) throws PngException {
		byte[] data = new byte[length];
		try {
			int actual = ins.read(data);
			if (actual < length) {
				throw new PngException(String.format("Expected %d bytes but got %d", length, actual));
			}
		} catch (IOException e) {
			throw new PngException("Error reading chunk data", e);
		}

		return data;
	}

	/* */
	private long getChunkCrc(DataInputStream ins) throws IOException {
		int i = ins.readInt();
		long crc = i & 0x00000000ffffffffL; // Make it unsigned.
		return crc;
	}

	/* */
	private static void readSignature(DataInputStream ins) throws PngException, IOException {
		long signature = ins.readLong();
		if (signature != PngImage.SIGNATURE) {
			throw new PngException("Bad png signature");
		}
	}
}
