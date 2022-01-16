/* Copyright 2014 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Author: eustas.ru@gmail.com (Eugene Klyuchnikov)
*/

package com.googlecode.pngtastic.core.processing.zopfli;

import com.googlecode.pngtastic.core.processing.zopfli.Options.BlockSplitting;
import com.googlecode.pngtastic.core.processing.zopfli.Options.OutputFormat;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Zopfli compression command line interface.
 */
class Main {

  public static void main(String[] args) {
    OutputFormat outputType = OutputFormat.GZIP;
    BlockSplitting blockSplitting = BlockSplitting.FIRST;
    int numIterations = 15;

    boolean outputToStdOut = false;

    for (int i = 0; i < args.length; ++i) {
      String arg = args[i];
      if ("-c".equals(arg)) {
        outputToStdOut = true;
      } else if ("--deflate".equals(arg)) {
        outputType = OutputFormat.DEFLATE;
      } else if ("--zlib".equals(arg)) {
        outputType = OutputFormat.ZLIB;
      } else if ("--gzip".equals(arg)) {
        outputType = OutputFormat.GZIP;
      } else if ("--splitlast".equals(arg)) {
        blockSplitting = BlockSplitting.LAST;
      } else if (arg.startsWith("--i")) {
        try {
          numIterations = Integer.parseInt(arg.substring(3));
        } catch (NumberFormatException e) {
          System.err.println("Can't parse number of iterations option.");
          return;
        }
      } else if ("-h".equals(arg)) {
        printHelp();
        return;
      }
    }

    if (numIterations < 1) {
      System.err.println("Error: must have 1 or more iterations");
    }

    Zopfli compressor = new Zopfli(8 << 20);
    byte[] buffer = new byte[65536];
    Options options = new Options(outputType, blockSplitting, numIterations);

    String fileName = null;
    for (int i = 0; i < args.length; ++i) {
      String arg = args[i];
      if (arg.charAt(0) == '-') {
        continue;
      }
      fileName = arg;
      String outFileName;
      if (outputToStdOut) {
        outFileName = null;
      } else if (outputType == OutputFormat.GZIP) {
        outFileName = fileName + ".gz";
      } else if (outputType == OutputFormat.ZLIB) {
        outFileName = fileName + ".zlib";
      } else {
        outFileName = fileName + ".deflate";
      }
      try {
        compressFile(compressor, options, fileName, outFileName, buffer);
      } catch (IOException ex) {
        ex.printStackTrace();
        return;
      }
    }

    if (fileName == null) {
      System.err.println(""
          + "Please provide filename\n"
          + "For help, try -h");
    }
  }

  private static void silentlyClose(Closeable stream) {
    try {
      stream.close();
    } catch (IOException ex) {
      // We don't care.
    }
  }

  private static byte[] readFile(String fileName, byte[] buffer) throws IOException {
    FileInputStream inputStream = new FileInputStream(fileName);
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(inputStream.available());
      baos.reset();
      int len;
      while ((len = inputStream.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
      return baos.toByteArray();
    } finally {
      silentlyClose(inputStream);
    }
  }

  /**
   * Compress single file.
   *
   * @param options Options.
   * @param fileName Input file name.
   * @param outFileName Output file name; {@code null} to output to stdout.
   */
  private static void compressFile(Zopfli compressor, Options options,
      String fileName, String outFileName, byte[] buffer) throws IOException {
    byte[] input = readFile(fileName, buffer);
    if (input == null) {
      return;
    }

    OutputStream output;
    if (outFileName != null) {
      output = new FileOutputStream(outFileName);
    } else {
      output = System.out;
    }

    try {
      compressor.compress(options, input, output);
    } finally {
      if (outFileName != null) {
        silentlyClose(output);
      }
    }
  }

  private static void printHelp() {
    System.err.println(""
        + "Usage: zopfli [OPTION]... FILE\n"
        + "  -h    gives this help\n"
        + "  -c    write the result on standard output, instead of disk"
        + " filename + '.gz'\n"
        + "  --i#  perform # iterations (default 15). More gives"
        + " more compression but is slower."
        + " Examples: --i10, --i50, --i1000\n"
        + "  --gzip        output to gzip format (default)\n"
        + "  --zlib        output to zlib format instead of gzip\n"
        + "  --deflate     output to deflate format instead of gzip\n"
        + "  --splitlast   do block splitting last instead of first");
  }
}
