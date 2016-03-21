package com.googlecode.pngtastic.core.processing;

import com.googlecode.pngtastic.core.Logger;
import com.googlecode.pngtastic.core.processing.zopfli.Buffer;
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

    private static final Options OPTIONS = new Options(Options.BlockSplitting.FIRST, 15);

    private final Logger log;
    private final Zopfli zopfli;

    public ZopfliCompressionHandler(Logger log) {
        this.log = log;
        this.zopfli = new Zopfli(8 * 1024 * 1024);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] deflate(PngByteArrayOutputStream inflatedImageData, Integer compressionLevel, boolean concurrent) throws IOException {
        Buffer result = zopfli.compress(OPTIONS, inflatedImageData.toByteArray());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(result.getSize());
        byteArrayOutputStream.write(result.getData(), 0, result.getSize());
        log.debug("Compression strategy: zopfli, bytes=%d", byteArrayOutputStream.size());

        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public String encodeBytes(byte[] bytes) {
        return Base64.encodeBytes(bytes);
    }
}
