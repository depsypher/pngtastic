package com.googlecode.pngtastic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngLayerer;
import com.googlecode.pngtastic.core.PngOptimizer;

/**
 * Layers PNG images on top of one another to produce a merged image. Assumes
 * that there is an alpha channel.
 *
 * @author rayvanderborght
 */
public class PngtasticLayerer
{
    /** */
    private static final String HELP = "java -cp pngtastic-x.x.x.jar com.googlecode.pngtastic.PngtasticLayerer [options] file1 [file2 ..]\n"
            + "Options:\n"
            + "  --toDir            the directory where the layered file goes (will be created if it doesn't exist)\n"
            + "  --outFile          the filename of the layered file\n"
            + "  --compressionLevel the compression level; 0-9 allowed (default is to try them all by brute force)\n"
            + "  --logLevel         the level of logging output (none, debug, info, or error)\n";

    /** */
    public PngtasticLayerer(String toDir, String[] fileNames, String outFile, Integer compressionLevel, String logLevel) {
        long start = System.currentTimeMillis();

        PngLayerer layerer = new PngLayerer(logLevel);
        PngOptimizer optimizer = new PngOptimizer(logLevel);
        try {
        	PngImage baseImage = new PngImage(fileNames[0]);
	        for (int i = 1; i < fileNames.length; i++) {
	        	String file = fileNames[i];

                PngImage image = new PngImage(file);
                baseImage = layerer.layer(baseImage, image, compressionLevel, false);
	        }
			ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
			baseImage.writeDataOutputStream(outputBytes);

			String file = toDir + "/" + outFile;
			baseImage.setFileName(file);
			baseImage.export(file, outputBytes.toByteArray());

	        optimizer.optimize(baseImage, file, compressionLevel);
        } catch (IOException e) {
        	e.printStackTrace();
        }

        System.out.println(String.format("Processed %d files in %d milliseconds, saving %d bytes",
        		optimizer.getStats().size(), System.currentTimeMillis() - start, optimizer.getTotalSavings()));
    }

    /** */
    public static void main(String[] args) {
        Map<String, String> options = new HashMap<String, String>();
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

        String toDir = (options.get("--toDir") == null) ? "." : options.get("--toDir");
        String outFile = (options.get("--outFile") == null) ? "" : options.get("--outFile");
        Integer compressionLevel = safeInteger(options.get("--compressionLevel"));
        String logLevel = options.get("--logLevel");

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
