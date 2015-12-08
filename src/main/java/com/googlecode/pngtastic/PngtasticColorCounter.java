package com.googlecode.pngtastic;

import com.googlecode.pngtastic.core.PngColorCounter;
import com.googlecode.pngtastic.core.PngException;
import com.googlecode.pngtastic.core.PngImage;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Gives a count of colors in a given image
 *
 * @author rayvanderborght
 */
public class PngtasticColorCounter {
	/** */
	private static final String HELP = "java -jar pngtastic-x.x.jar com.googlecode.pngtastic.PngtasticColorCounter [options] file1 [file2 ..]\n"
			+ "Options:\n"
			+ "  --distThreshold    the distance below which two colors are considered similar (0.0 to 1.0)\n"
			+ "  --freqThreshold    the percentage a color must be represented in the overall image (0.0 to 1.0)\n"
			+ "  --minAlpha         the minimum alpha channel value a pixel must have\n"
			+ "  --logLevel         the level of logging output (none, debug, info, or error)\n";

	/** */
	public PngtasticColorCounter(String[] fileNames, String logLevel, double distThreshold, double freqThreshold,
			int minAlpha) {

		long start = System.currentTimeMillis();
		PngColorCounter counter = new PngColorCounter(logLevel, distThreshold, freqThreshold, minAlpha);

		for (String file : fileNames) {
			try {
				PngImage image = new PngImage(file);
				counter.count(image);

				System.out.println(counter.getResult());

			} catch (PngException | IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println(String.format("Processed in %d milliseconds", System.currentTimeMillis() - start));
	}

	/** */
	public static void main(String[] args) {
		Map<String, String> options = new HashMap<>();
		int last = 0;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("--")) {
				int next = i + 1;
				if (next < args.length) {
					options.put(arg, args[next]);
					last = next + 1;
				} else {
					options.put(arg, null);
					last = next;
				}
			}
		}
		String[] files = Arrays.copyOfRange(args, last, args.length);

		if (files.length == 0) {
			System.out.println("No files to process");
			System.out.println(HELP);
			return;
		}

		Double distThreshold = safeDouble(options.get("--distThreshold"), 0.005D);  // min @8bit: 0.000005
		Double freqThreshold = safeDouble(options.get("--freqThreshold"), 0.0005D);
		Integer minAlpha = safeInteger(options.get("--minAlpha"), 30);
		String logLevel = options.get("--logLevel");

		new PngtasticColorCounter(files, logLevel, distThreshold, freqThreshold, minAlpha);
	}

	private static Integer safeInteger(String input, Integer dflt) {
		try {
			return Integer.valueOf(input);
		} catch (Exception e) {
			return dflt;
		}
	}

	private static Double safeDouble(String input, Double dflt) {
		try {
			return Double.valueOf(input);
		} catch (Exception e) {
			return dflt;
		}
	}
}
