package com.googlecode.pngtastic.core.processing;

import com.googlecode.pngtastic.core.Logger;
import com.googlecode.pngtastic.core.processing.zopfli.Options;
import com.googlecode.pngtastic.core.processing.zopfli.Zopfli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Implements PNG compression and decompression
 * Uses zopfli to compress: https://code.google.com/p/zopfli/, https://github.com/eustas/CafeUndZopfli
 *
 * @author rayvanderborght
 */
public class ZopfliCompressionHandler implements PngCompressionHandler {

    private static final int DEFAULT_ITERATIONS = 15;
    private final Options options;

    private final Logger log;
    private final Zopfli zopfli;

    public ZopfliCompressionHandler(Logger log) {
        this(log, DEFAULT_ITERATIONS);
    }

    public ZopfliCompressionHandler(Logger log, int iterations) {
        this.log = log;
        zopfli = new Zopfli(8 * 1024 * 1024);
        options = new Options(Options.OutputFormat.ZLIB, Options.BlockSplitting.FIRST, iterations);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] deflate(PngByteArrayOutputStream inflatedImageData, Integer compressionLevel, boolean concurrent) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        zopfli.compress(options, inflatedImageData.toByteArray(), byteArrayOutputStream);
        log.debug("Compression strategy: zopfli, bytes=%d", byteArrayOutputStream.size());

        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public String encodeBytes(byte[] bytes) {
        return Base64.encodeBytes(bytes);
    }
}
