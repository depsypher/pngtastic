package com.googlecode.pngtastic.ant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngOptimizer;

/**
 * Pngtastic ant task
 *
 * @author rayvanderborght
 */
public class PngtasticTask extends Task
{
	/** */
	private String toDir;
	public String getToDir() { return this.toDir; }
	public void setToDir(String toDir) { this.toDir = toDir; }

	/** */
	private String fileSuffix = "";
	public void setFileSuffix(String fileSuffix) { this.fileSuffix = fileSuffix; }
	public String getFileSuffix() { return this.fileSuffix; }

	/** */
	private Integer compressionLevel;
	public Integer getCompressionLevel() { return this.compressionLevel; }
	public void setCompressionLevel(Integer compressionLevel) { this.compressionLevel = compressionLevel; }

	/** */
	private String logLevel;
	public String getLogLevel() { return this.logLevel; }
	public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

	/** */
	private List<FileSet> filesets = new ArrayList<FileSet>();
    public void addFileset(FileSet fileset)
    {
    	if (!this.filesets.contains(fileset))
    		this.filesets.add(fileset);
    }

	/** */
	@Override
	public void execute() throws BuildException
	{
		try
		{
			this.convert();
		}
		catch(Exception e)
		{
			throw new BuildException(e);
		}
	}

	/* */
	private void convert()
	{
		long start = System.currentTimeMillis();
		PngOptimizer optimizer = new PngOptimizer(this.logLevel);

		for (FileSet fileset : this.filesets)
		{
			DirectoryScanner ds = fileset.getDirectoryScanner(this.getProject());
			for (String src : ds.getIncludedFiles())
			{
				String inputPath = fileset.getDir() + "/" + src;
				String outputPath = null;
				try
				{
					String outputDir = (this.toDir == null) ? fileset.getDir().getCanonicalPath() : this.toDir;
					outputPath = outputDir + "/" + src;

					// make the directory this file is in (for nested dirs in a **/* fileset)
					this.makeDirs(outputPath.substring(0, outputPath.lastIndexOf('/')));

					PngImage image = new PngImage(inputPath);
					optimizer.optimize(image, outputPath + this.fileSuffix, this.compressionLevel);
				}
				catch (Exception e)
				{
					this.log(String.format("Problem optimizing %s. Caught %s", inputPath, e.getMessage()));
				}
			}
		}

		this.log(String.format("Processed %d files in %d milliseconds, saving %d bytes", optimizer.getStats().size(), System.currentTimeMillis() - start, optimizer.getTotalSavings()));
	}

	/* */
	private String makeDirs(String path)
	{
		try
		{
			File out = new File(path);
			if (!out.exists())
			{
				if (!out.mkdirs())
					throw new IOException("Couldn't create path: " + path);
			}
			path = out.getCanonicalPath();
		}
		catch (IOException e)
		{
			throw new BuildException("Bad path: " + path);
		}
		return path;
	}
}
