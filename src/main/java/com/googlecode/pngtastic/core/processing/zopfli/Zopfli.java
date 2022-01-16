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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Zopfli compression and output framing facade.
 */
public final class Zopfli {

  /**
   * Abstract checksum calculator; tracks input length.
   */
  static class Checksum {
    private int totalLength;

    void update(byte[] input, int from, int length) {
      // TODO(eustas): check overflow.
      totalLength += length;
    }

    int checksum() {
      return 0;
    }

    int size() {
      return totalLength;
    }
  }

  /**
   * Calculates the CRC (with 0x04C11DB7 polynomial) checksum of the data.
   */
  static final class GzipChecksum extends Checksum {

    private static final int[] table = makeTable();

    private int value = ~0;

    private static int[] makeTable() {
      int[] result = new int[256];

      for (int n = 0; n < 256; ++n) {
        int c = n;
        for (int k = 0; k < 8; ++k) {
          if ((c & 1) == 1) {
            c = 0xEDB88320 ^ (c >>> 1);
          } else {
            c = c >>> 1;
          }
        }
        result[n] = c;
      }

      return result;
    }

    @Override
    void update(byte[] input, int from, int length) {
      super.update(input, from, length);
      int c = value;
      for (int i = 0; i < length; ++i) {
        c = table[(c ^ input[from + i]) & 0xFF] ^ (c >>> 8);
      }
      value = c;
    }

    @Override
    int checksum() {
      return ~value;
    }
  }

  /**
   * Calculates the adler32 checksum of the data.
   */
  static final class ZlibChecksum extends Checksum {
    private int lo = 1;
    private int hi = 0;

    @Override
    void update(byte[] input, int from, int length) {
      super.update(input, from, length);
      int s1 = lo;
      int s2 = hi;
      int i = 0;
      while (i < length) {
        int fence = Math.min(length, i + 3854);
        while (i < fence) {
          s1 += input[from + i++] & 0xFF;
          s2 += s1;
        }
        s1 %= 65521;
        s2 %= 65521;
      }
      lo = s1;
      hi = s2;
    }

    @Override
    int checksum() {
      return (hi << 16) | lo;
    }
  }

  private final Cookie cookie;

  public synchronized void compress(Options options, byte[] input, OutputStream output)
      throws IOException {
    try {
      BitWriter bitWriter = new BitWriter(output);
      Options.OutputFormat format = options.outputType;

      Checksum digest = createDigest(format);
      writePrologue(format, bitWriter);

      if (input.length == 0) {
        writeEmptyBlock(bitWriter, true);
      } else {
        int i = 0;
        while (i < input.length) {
          int j = Math.min(i + cookie.masterBlockSize, input.length);
          boolean isFinal = (j == input.length);
          Deflate.deflatePart(cookie, options, input, i, j, isFinal, bitWriter);
          i = j;
        }
      }

      digest.update(input, 0, input.length);
      writeEpilogue(format, bitWriter, digest);
    } catch (ZopfliRuntimeException ex) {
      throw new IOException(ex);
    }
  }

  static Checksum createDigest(Options.OutputFormat format) {
    switch (format) {
      case GZIP:
        return new GzipChecksum();

      case ZLIB:
        return new ZlibChecksum();

      case DEFLATE:
        return new Checksum();
    }
    throw new IllegalArgumentException();  // COV_NF_LINE
  }

  static void writePrologue(Options.OutputFormat format, BitWriter output) throws IOException {
    try {
      switch (format) {
        case GZIP:
          output.addBits(0x8B1F, 16);
          output.addBits(0x0008, 16);
          output.addBits(0x0000, 16);
          output.addBits(0x0000, 16);
          output.addBits(0x0302, 16);
          return;

        case ZLIB:
          output.addBits(0xDA78, 16);
          return;

        case DEFLATE:
          return;

        default:
          throw new IllegalArgumentException();  // COV_NF_LINE
      }
    } catch (ZopfliRuntimeException ex) {
      throw new IOException(ex);
    }
  }

  /**
   * Pads output to the byte boundary, writes format-specific epilogue and flushes the output.
   */
  static void writeEpilogue(Options.OutputFormat format, BitWriter output, Checksum digest)
      throws IOException {
    try {
      output.jumpToByteBoundary();
      int checksum = digest.checksum();
      int dataLength = digest.size();
      switch (format) {
        case GZIP:
          output.addBits(checksum & 0xFFFF, 16);
          output.addBits((checksum >> 16) & 0xFFFF, 16);
          output.addBits(dataLength & 0xFFFF, 16);
          output.addBits((dataLength >> 16) & 0xFFFF, 16);
          break;

        case ZLIB:
          output.addBits((checksum >> 24) & 0xFF, 8);
          output.addBits((checksum >> 16) & 0xFF, 8);
          output.addBits((checksum >> 8) & 0xFF, 8);
          output.addBits(checksum & 0xFF, 8);
          break;

        case DEFLATE:
          break;

        default:
          throw new IllegalArgumentException();  // COV_NF_LINE
      }
      output.flush();
    } catch (ZopfliRuntimeException ex) {
      throw new IOException(ex);
    }
  }

  static void writeEmptyBlock(BitWriter output, boolean isLast) throws IOException {
    try {
      output.addBits(isLast ? 1 : 0, 1);
      output.addBits(1, 2);  /* BTYPE = fixed */
      output.addBits(0, 7);  /* 256 == end-of-block */
    } catch (ZopfliRuntimeException ex) {
      throw new IOException(ex);
    }
  }

  public Zopfli(int masterBlockSize) {
    cookie = new Cookie(masterBlockSize);
  }
}
