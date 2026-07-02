package logbook.internal.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.eclipse.jetty.compression.brotli.BrotliCompression;
import org.eclipse.jetty.compression.zstandard.ZstandardCompression;

/**
 * 解凍テスト用の圧縮データ生成ヘルパー。
 */
final class CompressionTestFixtures
{
    static final byte[] PLAIN = "{\"api_result\":1}".getBytes(StandardCharsets.UTF_8);

    private CompressionTestFixtures()
    {
    }

    static BrotliCompression createBrotliCompression() throws Exception
    {
        BrotliCompression brotli = new BrotliCompression();
        brotli.start();
        return brotli;
    }

    static ZstandardCompression createZstandardCompression() throws Exception
    {
        ZstandardCompression zstandard = new ZstandardCompression();
        zstandard.start();
        return zstandard;
    }

    static byte[] gzip(byte[] plain) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(baos))
        {
            out.write(plain);
        }
        return baos.toByteArray();
    }

    static byte[] brotli(byte[] plain, BrotliCompression brotli) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream out = brotli.newEncoderOutputStream(baos, brotli.getDefaultEncoderConfig()))
        {
            out.write(plain);
        }
        return baos.toByteArray();
    }

    static byte[] zstd(byte[] plain, ZstandardCompression zstandard) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream out = zstandard.newEncoderOutputStream(baos, zstandard.getDefaultEncoderConfig()))
        {
            out.write(plain);
        }
        return baos.toByteArray();
    }
}
