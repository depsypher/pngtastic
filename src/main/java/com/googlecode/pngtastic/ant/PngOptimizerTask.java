package com.googlecode.pngtastic.ant;

import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngOptimizer;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pngtastic optimizer ant task
 *
 * @author rayvanderborght
 */
public class PngOptimizerTask extends Task {

	private String toDir;
	public String getToDir() { return this.toDir; }
	public void setToDir(String toDir) { this.toDir = toDir; }

	private String fileSuffix = "";
	public void setFileSuffix(String fileSuffix) { this.fileSuffix = fileSuffix; }
	public String getFileSuffix() { return this.fileSuffix; }

	private Boolean generateDataUriCss = Boolean.FALSE;
	public Boolean getGenerateDataUriCss() { return generateDataUriCss; }
	public void setGenerateDataUriCss(Boolean generateDataUriCss) { this.generateDataUriCss = generateDataUriCss; }

	private Boolean removeGamma = Boolean.FALSE;
	public Boolean getRemoveGamma() { return removeGamma; }
	public void setRemoveGamma(Boolean removeGamma) { this.removeGamma = removeGamma; }

	private Integer compressionLevel;
	public Integer getCompressionLevel() { return this.compressionLevel; }
	public void setCompressionLevel(Integer compressionLevel) { this.compressionLevel = compressionLevel; }

	private String compressor;
	public String getCompressor() { return compressor; }
	public void setCompressor(String compressor) { this.compressor = compressor; }

	private Integer iterations;
	public Integer getIterations() { return iterations; }
	public void setIterations(Integer iterations) { this.iterations = iterations; }

	private String logLevel;
	public String getLogLevel() { return this.logLevel; }
	public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

	private List<FileSet> filesets = new ArrayList<>();
	public void addFileset(FileSet fileset) {
		if (!this.filesets.contains(fileset)) {
			this.filesets.add(fileset);
		}
	}

	@Override
	public void execute() throws BuildException {
		try {
			this.convert();
		} catch(Exception e) {
			throw new BuildException(e);
		}
	}

	/* */
	private void convert() {
		long start = System.currentTimeMillis();
		PngOptimizer optimizer = new PngOptimizer(logLevel);
		optimizer.setCompressor(compressor, iterations);
		optimizer.setGenerateDataUriCss(generateDataUriCss);

		for (FileSet fileset : filesets) {
			DirectoryScanner ds = fileset.getDirectoryScanner(getProject());
			for (String src : ds.getIncludedFiles()) {
				String inputPath = fileset.getDir() + "/" + src;
				String outputPath;
				try {
					String outputDir = (toDir == null) ? fileset.getDir().getCanonicalPath() : toDir;
					outputPath = outputDir + "/" + src;

					// make the directory this file is in (for nested dirs in a **/* fileset)
					makeDirs(outputPath.substring(0, outputPath.lastIndexOf('/')));

					PngImage image = new PngImage(inputPath, logLevel);
					optimizer.optimize(image, outputPath + fileSuffix, removeGamma, compressionLevel);
				} catch (Exception e) {
					log(String.format("Problem optimizing %s. Caught %s", inputPath, e.getMessage()));
				}
			}
		}

		log(String.format("Processed %d files in %d milliseconds, saving %d bytes",
				optimizer.getResults().size(), System.currentTimeMillis() - start, optimizer.getTotalSavings()));

		if (generateDataUriCss) {
			try {
				optimizer.generateDataUriCss(toDir);
			} catch (IOException e) {
			}
		}
	}

	/* */
	private String makeDirs(String path) {
		try {
			File out = new File(path);
			if (!out.exists()) {
				if (!out.mkdirs()) {
					throw new IOException("Couldn't create path: " + path);
				}
			}
			path = out.getCanonicalPath();
		} catch (IOException e) {
			throw new BuildException("Bad path: " + path);
		}
		return path;
	}
}
