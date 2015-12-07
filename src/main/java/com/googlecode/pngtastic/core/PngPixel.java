package com.googlecode.pngtastic.core;

/**
 * An individual pixel in an image
 */
public class PngPixel {

	private final int red;
	private final int green;
	private final int blue;
	private final int alpha;

	private final int x;
	private final int y;

	private int freq = 0;
	private boolean duplicate;

	/** */
	public PngPixel(int x, int y, int red, int green, int blue, boolean is8bit) {
		this(x, y, red, green, blue, is8bit ? 255 : 65535);
	}

	/** */
	public PngPixel(int x, int y, int red, int green, int blue, int alpha) {
		this.x = x;
		this.y = y;
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.alpha = alpha;
	}

	public double rgbaDistance(PngPixel other, int bits) {
		if (bits == 8) {
			return rgba8Distance(other);
		}
		return rgba16Distance(other);
	}

	public double rgba8Distance(PngPixel other) {
		int rdiff = this.red - other.red;
		int gdiff = this.green - other.green;
		int bdiff = this.blue - other.blue;

		long result = (rdiff * rdiff) + (gdiff * gdiff) + (bdiff * bdiff);

		// max is (255^2 * 3), e.g. FFFFFF.FF versus 000000.00
		return result / 195_075D;
	}

	public double rgba16Distance(PngPixel other) {
		int rdiff = this.red - other.red;
		int gdiff = this.green - other.green;
		int bdiff = this.blue - other.blue;

		long result = (rdiff * rdiff) + (gdiff * gdiff) + (bdiff * bdiff);

		// max is 65535^2 * 3
		return result / 12_884_508_675D;
	}

	// TODO: interesting idea for later...
//	public double rgba8Distance(PngPixel other, double alphaWeight) {
//		int rdiff = this.red - other.red;
//		int gdiff = this.green - other.green;
//		int bdiff = this.blue - other.blue;
//
//		long result;
//
//		if (alphaWeight > 0) {
//			int adiff = (int) ((Math.abs(this.alpha - other.alpha) / 256D) * 256D * alphaWeight % 256);
//			result =
//					Math.max(pow(rdiff), pow(rdiff + adiff)) +
//					Math.max(pow(gdiff), pow(gdiff + adiff)) +
//					Math.max(pow(bdiff), pow(bdiff + adiff));
//		} else {
//			result = pow(rdiff) + pow(gdiff) + pow(bdiff);
//		}
//
//		// max is FFFFFF FF versus 000000 00 ((255+255)^2 * 3)
//		return result / 780_300D;
//	}

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

	@Override
	public String toString() {
		return String.format("%02X%02X%02X.%02X@%d,%d:%d", red, green, blue, alpha, x, y, freq);
	}

	public int getAlpha() {
		return alpha;
	}

	public int getFreq() {
		return freq;
	}
	public void setFreq(int freq) {
		this.freq = freq;
	}

	public boolean isDuplicate() {
		return duplicate;
	}
	public void setDuplicate(boolean duplicate) {
		this.duplicate = duplicate;
	}
}
