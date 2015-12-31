package com.googlecode.pngtastic;

import com.googlecode.pngtastic.core.PngException;
import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngLayerer;
import com.googlecode.pngtastic.core.PngOptimizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Layers PNG images on top of one another to produce a merged image. Assumes
 * that there is an alpha channel.
 *
 * @author rayvanderborght
 */
public class PngtasticLayerer {

	/** */
	private static final String HELP = "java -cp pngtastic-x.x.jar com.googlecode.pngtastic.PngtasticLayerer [options] file1 [file2 ..]\n"
			+ "Options:\n"
			+ "  --toDir            the directory where the layered file goes (will be created if it doesn't exist)\n"
			+ "  --outFile          the filename of the layered file\n"
			+ "  --compressionLevel the compression level; 0-9 allowed (default is to try them all by brute force)\n"
			+ "  --logLevel         the level of logging output (none, debug, info, or error)\n";

	/** */
	public PngtasticLayerer(String toDir, String[] fileNames, String outFile, Integer compressionLevel, String logLevel) {
		final long start = System.currentTimeMillis();

		final PngLayerer layerer = new PngLayerer(logLevel);
		final PngOptimizer optimizer = new PngOptimizer(logLevel);

		try {
			PngImage baseImage = new PngImage(fileNames[0], logLevel);
			for (int i = 1; i < fileNames.length; i++) {
				final String file = fileNames[i];

				final PngImage image = new PngImage(file, logLevel);
				baseImage = layerer.layer(baseImage, image, compressionLevel, false);
			}
			final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
			baseImage.writeDataOutputStream(outputBytes);

			final String file = toDir + "/" + outFile;
			baseImage.setFileName(file);
			baseImage.export(file, outputBytes.toByteArray());

			optimizer.optimize(baseImage, file, false, compressionLevel);
		} catch (PngException | IOException e) {
			e.printStackTrace();
		}

		System.out.println(String.format("Processed %d files in %d milliseconds, saving %d bytes",
				optimizer.getResults().size(), System.currentTimeMillis() - start, optimizer.getTotalSavings()));
	}

	/** */
	public static void main(String[] args) {
		final Map<String, String> options = new HashMap<>();
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
		final String[] files = Arrays.copyOfRange(args, last, args.length);

		if (files.length == 0) {
			System.out.println("No files to process");
			System.out.println(HELP);
			return;
		}

		final String toDir = (options.get("--toDir") == null) ? "." : options.get("--toDir");
		final String outFile = (options.get("--outFile") == null) ? "" : options.get("--outFile");
		final Integer compressionLevel = safeInteger(options.get("--compressionLevel"));
		final String logLevel = options.get("--logLevel");

		new PngtasticLayerer(toDir, files, outFile, compressionLevel, logLevel);
	}

	/* */
	private static Integer safeInteger(String input) {
		try {
			return Integer.valueOf(input);
		} catch (Exception e) {
			return null;
		}
	}
}
