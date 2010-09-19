/*
 * $Id: $
 * $URL: $
 */
package com.googlecode.pngtastic.core.processing;

import com.googlecode.pngtastic.core.PngException;
import com.googlecode.pngtastic.core.PngFilterType;

/**
 * Implement PNG filtering and defiltering
 *
 * @author rayvanderborght
 */
public class PngtasticFilterHandler implements PngFilterHandler
{
	/**
	 * @inheritDoc
	 *
	 * The bytes are named as follows (x = current, a = previous, b = above, c = previous and above)
	 * <pre>
	 * c b
	 * a x
	 * </pre>
	 */
	@Override
	public void filter(byte[] line, byte[] previousLine, int sampleBitCount) throws PngException
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

	/**
	 * @inheritDoc
	 */
	@Override
	public void deFilter(byte[] line, byte[] previousLine, int sampleBitCount) throws PngException
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
}
