package com.justsyncit.storage.metadata;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionTest {

    @Test
    public void testCompressDecompress() throws IOException {
        String original = "This is a test string to be compressed. It should be long enough to actually benefit from compression or at least test the mechanism properly. "
                .repeat(10);

        String compressed = compress(original);
        assertNotNull(compressed);
        assertNotEquals(original, compressed);

        String decompressed = decompress(compressed);
        assertEquals(original, decompressed);
    }

    @Test
    public void testEmptyString() throws IOException {
        String original = "";
        String compressed = compress(original);
        assertEquals("", compressed);

        String decompressed = decompress(compressed);
        assertEquals(original, decompressed);
    }

    @Test
    public void testNullString() throws IOException {
        String compressed = compress(null);
        assertNull(compressed);

        String decompressed = decompress(null);
        assertNull(decompressed);
    }

    // Helper methods mirrored from SqliteMetadataService for standalone testing
    private String compress(String str) throws IOException {
        if (str == null || str.isEmpty()) {
            return str;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private String decompress(String str) throws IOException {
        if (str == null || str.isEmpty()) {
            return str;
        }
        byte[] bytes = Base64.getDecoder().decode(str);
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
