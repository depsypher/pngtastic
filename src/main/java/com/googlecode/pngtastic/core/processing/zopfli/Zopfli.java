/*
Copyright 2014 Google Inc. All Rights Reserved.

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

import java.util.zip.Adler32;

public final class Zopfli {

    private final Cookie cookie;

    public Zopfli(int masterBlockSize) {
        cookie = new Cookie(masterBlockSize);
    }

    /**
     * Calculates the adler32 checksum of the data
     */
    private static long adler32(byte[] data) {
        final Adler32 checksum = new Adler32();
        checksum.update(data);
        return checksum.getValue();
    }

    public synchronized Buffer compress(Options options, byte[] input) {
        Buffer output = new Buffer();
        compressZlib(options, input, output);
        return output;
    }

    private void compressZlib(Options options, byte[] input, Buffer output) {
        output.append((byte) 0x78);
        output.append((byte) 0xDA);

        Deflate.compress(cookie, options, input, output);

        long checksum = adler32(input);
        output.append((byte) ((checksum >> 24) % 256));
        output.append((byte) ((checksum >> 16) % 256));
        output.append((byte) ((checksum >> 8) % 256));
        output.append((byte) (checksum % 256));
    }
}
