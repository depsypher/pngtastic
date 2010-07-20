/*
 * $Id$
 * $URL$
 */
package com.googlecode.pngtastic;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngOptimizer;

/**
 * Optimizes PNG images to reduce filesize
 *
 * @see <a href="http://www.w3.org/TR/PNG">PNG spec</a>
 * @see <a href="http://optipng.sourceforge.net/pngtech/">PNG related articles</a>
 * @see <a href="http://www.schaik.com/pngsuite/">PNG reference images</a>
 *
 * @author rayvanderborght
 */
public class Pngtastic
{
	/** */
	public Pngtastic(String toDir, String[] fileNames, String fileSuffix, String logLevel)
	{
		long start = System.currentTimeMillis();

		PngOptimizer optimizer = new PngOptimizer(logLevel);
		for (String file : fileNames)
		{
			try
			{
				String outputPath = toDir + "/" + file;
				this.makeDirs(outputPath.substring(0, outputPath.lastIndexOf('/')));

				PngImage image = new PngImage(file);
				optimizer.optimize(image, outputPath + fileSuffix);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		System.out.println(String.format("Processed %d files in %d milliseconds, saving %d bytes", optimizer.getStats().size(), System.currentTimeMillis() - start, optimizer.getTotalSavings()));
	}

	/* */
	private String makeDirs(String path) throws IOException
	{
		File out = new File(path);
		if (!out.exists())
		{
			if (!out.mkdirs())
				throw new IOException("Couldn't create path: " + path);
		}
		return out.getCanonicalPath();
	}

	/** */
	public static void main(String[] args)
	{
		Map<String, String> options = new HashMap<String, String>();
		int last = 0;
		for (int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if (arg.startsWith("--"))
			{
				int next = i + 1;
				if (next < args.length)
				{
					options.put(arg, args[next]);
					last = next + 1;
				}
				else
				{
					options.put(arg, null);
					last = next;
				}
			}
		}
		String[] files = Arrays.copyOfRange(args, last, args.length);

		if (files.length == 0)
		{
			System.out.println("No files to process");
			return;
		}

		String toDir = (options.get("--toDir") == null) ? "." : options.get("--toDir");
		String fileSuffix = (options.get("--fileSuffix") == null) ? "" : options.get("--fileSuffix");
		String logLevel = options.get("--logLevel");

		new Pngtastic(toDir, files, fileSuffix, logLevel);
	}
}
