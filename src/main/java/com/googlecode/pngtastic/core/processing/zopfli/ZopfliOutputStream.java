/* Copyright 2017 Google Inc. All Rights Reserved.

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
 * Output stream that wraps/uses zopfli encoder.
 */
public class ZopfliOutputStream extends OutputStream {
  /** The default internal buffer size used by the encoder. */
  private static final int DEFAULT_MASTER_BLOCK_SIZE = 256 << 10;

  private static final int WINDOW_SIZE = Deflate.WINDOW_SIZE;

  private final OutputStream destination;
  private final Options options;
  private final Cookie cookie;

  private final BitWriter bitWriter;
  private final byte[] buffer;
  private final Zopfli.Checksum digest;

  /* Invariant: between calls offset - legacy < masterBlockSize, i.e. there is at least one byte
     left in buffer. */
  private int offset;
  /* Offset at which legacy data ends. Initially it is 0, but it grows,
     after some data is compressed, up to WINDOW_SIZE. */
  private int legacy;
  private boolean isClosed;

  /* Actually, exception is never thrown, but there is no way to prove it to compiler. */
  public ZopfliOutputStream(OutputStream destination, Options options, Cookie cookie)
      throws IOException {
    this.options = options;
    this.destination = destination;
    this.cookie = cookie;

    this.bitWriter = new BitWriter(destination);
    this.buffer = new byte[WINDOW_SIZE + cookie.masterBlockSize];
    this.digest = Zopfli.createDigest(options.outputType);

    Zopfli.writePrologue(options.outputType, this.bitWriter);
  }

  public ZopfliOutputStream(OutputStream destination, Options options) throws IOException {
    this(destination, options, new Cookie(DEFAULT_MASTER_BLOCK_SIZE));
  }

  public ZopfliOutputStream(OutputStream destination) throws IOException {
    this(destination, new Options());
  }

  @Override
  public void close() throws IOException {
    if (isClosed) {
      return;
    }
    isClosed = true;
    if (offset > legacy) {
      compressBlock(true);
    } else {
      Zopfli.writeEmptyBlock(this.bitWriter, true);
    }
    Zopfli.writeEpilogue(this.options.outputType, bitWriter, digest);
    destination.close();
  }

  @Override
  public void flush() throws IOException {
    if (isClosed) {
      throw new IllegalStateException("write after close");
    }
    if (offset > legacy) {
      compressBlock(false);
    }
    /* Empty block is at least 10 bits -> all compressed data will be available to decoder. */
    Zopfli.writeEmptyBlock(this.bitWriter, false);
    bitWriter.flush();
    destination.flush();
  }

  @Override
  public void write(int b) throws IOException {
    if (isClosed) {
      throw new IllegalStateException("write after close");
    }
    buffer[offset++] = (byte) b;
    int fence = cookie.masterBlockSize + legacy;
    if (offset == fence) {
      compressBlock(false);
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (isClosed) {
      throw new IllegalStateException("write after close");
    }
    int from = off;
    int toWrite = len;
    while (toWrite > 0) {
      int fence = cookie.masterBlockSize + legacy;
      int chunk = fence - offset;
      if (toWrite < chunk) {
        chunk = toWrite;
      }
      System.arraycopy(b, from, buffer, offset, chunk);
      from += chunk;
      toWrite -= chunk;
      offset += chunk;
      if (offset == fence) {
        compressBlock(false);
      }
    }
  }

  private void compressBlock(boolean isLast) throws IOException {
    try {
      Deflate.deflatePart(cookie, options, buffer, legacy, offset, isLast, bitWriter);
      digest.update(buffer, legacy, offset - legacy);
    } catch (ZopfliRuntimeException ex) {
      throw new IOException(ex);
    }
    if (offset > WINDOW_SIZE) {
      // Move legacy to the beginning of the buffer.
      System.arraycopy(buffer, offset - WINDOW_SIZE, buffer, 0, WINDOW_SIZE);
      legacy = WINDOW_SIZE;
    } else {
      // Otherwise we can just continue filling the buffer.
      legacy = offset;
    }
    offset = legacy;
  }
}
