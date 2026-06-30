package logbook.internal.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.Map;

import org.eclipse.jetty.compression.Compression;
import org.eclipse.jetty.compression.brotli.BrotliCompression;
import org.eclipse.jetty.compression.gzip.GzipCompression;
import org.eclipse.jetty.compression.zstandard.ZstandardCompression;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ResponseBodyDecompressor} のユニットテスト。
 * <ul>
 * <li>層1: Content-Encoding 解決（Jetty {@link Compression} レジストリ）</li>
 * <li>層2: gzip / brotli / zstd のラウンドトリップ解凍</li>
 * </ul>
 */
class ResponseBodyDecompressorTest
{
    private static ResponseBodyDecompressor decompressor;
    private static BrotliCompression brotliCompression;
    private static ZstandardCompression zstandardCompression;

    @BeforeAll
    static void setUpAll() throws Exception
    {
        brotliCompression = initBrotliCompression();
        zstandardCompression = initZstandardCompression();
        decompressor = ResponseBodyDecompressor.create(
            new GzipCompression(), brotliCompression, zstandardCompression);
    }

    private static BrotliCompression initBrotliCompression()
    {
        try
        {
            return CompressionTestFixtures.createBrotliCompression();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static ZstandardCompression initZstandardCompression()
    {
        try
        {
            return CompressionTestFixtures.createZstandardCompression();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    @Nested
    class EncodingResolution
    {
        @ParameterizedTest
        @NullAndEmptySource
        void nullOrEmpty_returnsNoCompression(String contentEncoding) throws Exception
        {
            assertTrue(decompressor.resolveCompressionForTest(contentEncoding, CompressionTestFixtures.PLAIN).isEmpty());
        }

        @ParameterizedTest
        @CsvSource({
            "gzip, gzip",
            "GZIP, gzip",
            "br,   brotli",
            "zstd, zstandard"
        })
        void knownEncodings(String header, String expectedJettyName) throws Exception
        {
            Compression compression = decompressor.resolveCompressionForTest(header, CompressionTestFixtures.PLAIN)
                .orElseThrow();
            assertEquals(expectedJettyName, compression.getName());
        }

        @ParameterizedTest
        @ValueSource(strings = { "deflate", "identity", "compress", "unknown" })
        void unsupported_throws(String contentEncoding)
        {
            assertThrows(IOException.class,
                () -> decompressor.resolveCompressionForTest(contentEncoding, CompressionTestFixtures.PLAIN));
        }

        @Test
        void gzipMagicWithoutHeader_resolvesGzip() throws Exception
        {
            byte[] compressed = CompressionTestFixtures.gzip(CompressionTestFixtures.PLAIN);
            Compression compression = decompressor.resolveCompressionForTest(null, compressed).orElseThrow();
            assertEquals("gzip", compression.getName());
        }
    }

    @Nested
    class DecompressRoundTrip
    {
        @Test
        void gzip() throws Exception
        {
            byte[] compressed = CompressionTestFixtures.gzip(CompressionTestFixtures.PLAIN);
            byte[] result = decompressor.decompress(compressed, Map.of("Content-Encoding", "gzip"));
            assertArrayEquals(CompressionTestFixtures.PLAIN, result);
        }

        @Test
        void brotli() throws Exception
        {
            assumeTrue(brotliCompression != null, "BrotliCompression が利用不可");
            byte[] compressed = CompressionTestFixtures.brotli(CompressionTestFixtures.PLAIN, brotliCompression);
            byte[] result = decompressor.decompress(compressed, Map.of("Content-Encoding", "br"));
            assertArrayEquals(CompressionTestFixtures.PLAIN, result);
        }

        @Test
        void zstd() throws Exception
        {
            assumeTrue(zstandardCompression != null, "ZstandardCompression が利用不可");
            byte[] compressed = CompressionTestFixtures.zstd(CompressionTestFixtures.PLAIN, zstandardCompression);
            byte[] result = decompressor.decompress(compressed, Map.of("Content-Encoding", "zstd"));
            assertArrayEquals(CompressionTestFixtures.PLAIN, result);
        }

        @Test
        void plainWithoutEncoding() throws Exception
        {
            byte[] result = decompressor.decompress(CompressionTestFixtures.PLAIN, Map.of());
            assertArrayEquals(CompressionTestFixtures.PLAIN, result);
        }

        @Test
        void emptyBody() throws Exception
        {
            byte[] result = decompressor.decompress(new byte[0], Map.of("Content-Encoding", "gzip"));
            assertArrayEquals(new byte[0], result);
        }

        @Test
        void gzip_byMagicBytesWithoutHeader() throws Exception
        {
            byte[] compressed = CompressionTestFixtures.gzip(CompressionTestFixtures.PLAIN);
            byte[] result = decompressor.decompress(compressed, Map.of());
            assertArrayEquals(CompressionTestFixtures.PLAIN, result);
        }

        @Test
        void contentEncodingHeader_caseInsensitive() throws Exception
        {
            byte[] compressed = CompressionTestFixtures.gzip(CompressionTestFixtures.PLAIN);
            byte[] result = decompressor.decompress(compressed, Map.of("content-encoding", "gzip"));
            assertArrayEquals(CompressionTestFixtures.PLAIN, result);
        }

        @Test
        void unsupportedEncoding_throws() throws Exception
        {
            byte[] compressed = CompressionTestFixtures.gzip(CompressionTestFixtures.PLAIN);
            IOException ex = assertThrows(IOException.class,
                () -> decompressor.decompress(compressed, Map.of("Content-Encoding", "deflate")));
            assertTrue(ex.getMessage().contains("deflate"));
        }
    }
}
