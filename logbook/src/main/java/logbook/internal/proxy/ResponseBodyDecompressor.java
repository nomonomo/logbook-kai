package logbook.internal.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jetty.compression.Compression;
import org.eclipse.jetty.compression.brotli.BrotliCompression;
import org.eclipse.jetty.compression.gzip.GzipCompression;
import org.eclipse.jetty.compression.zstandard.ZstandardCompression;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTPレスポンスボディのContent-Encoding解凍を担当する。
 * <p>
 * Content-Encoding の解決と解凍は Jetty {@link Compression} に委譲する。
 * gzip マジックバイトによるフォールバックのみプロキシ固有ロジックとして残す。
 * <p>
 * Jetty の {@code Compression} は LifeCycle だが、本クラスは {@code newDecoderInputStream()} による
 * 解凍ファクトリとしてのみ利用する。ネイティブライブラリのロードはコンストラクタで行われ、
 * 解凍ストリームは呼び出しごとに try-with-resources で閉じる。{@code start()}/{@code stop()} や
 * Handler への addBean は Jetty サーバーコンポーネントとして統合する場合に必要であり、現状の用途では不要。
 */
@Slf4j
final class ResponseBodyDecompressor
{
    private static final List<Class<? extends Compression>> SUPPORTED_COMPRESSION_TYPES = List.of(
        GzipCompression.class,
        BrotliCompression.class,
        ZstandardCompression.class);

    private final CompressionRegistry registry;

    private ResponseBodyDecompressor(CompressionRegistry registry)
    {
        this.registry = registry;
    }

    /**
     * {@link #SUPPORTED_COMPRESSION_TYPES} を初期化して解凍器を構築する。
     *
     * @return 解凍器インスタンス
     */
    static ResponseBodyDecompressor createDefault()
    {
        return new ResponseBodyDecompressor(CompressionRegistry.createDefault(SUPPORTED_COMPRESSION_TYPES));
    }

    /**
     * テストやカスタム構成向けに、圧縮ハンドラを明示指定して構築する。
     *
     * @param compressions 解凍器（未初期化時は null を指定可）
     * @return 解凍器インスタンス
     */
    static ResponseBodyDecompressor create(Compression... compressions)
    {
        return new ResponseBodyDecompressor(CompressionRegistry.of(compressions));
    }

    /**
     * レスポンスボディを必要に応じて解凍する。
     *
     * @param bodyBytes 圧縮済みまたは非圧縮のボディバイト
     * @param headers HTTPレスポンスヘッダー
     * @return 解凍済みボディバイト
     * @throws IOException 未対応のContent-Encoding、または解凍失敗時
     */
    byte[] decompress(byte[] bodyBytes, Map<String, String> headers) throws IOException
    {
        if (bodyBytes == null || bodyBytes.length == 0)
        {
            return new byte[0];
        }

        Compression compression = registry.resolve(
            getHeaderCaseInsensitive(headers, "Content-Encoding"), bodyBytes);
        if (compression == null)
        {
            return bodyBytes;
        }

        byte[] decompressed = registry.decompress(compression, bodyBytes);
        log.debug("レスポンス解凍: {} {}B → {}B",
            compression.getName(), bodyBytes.length, decompressed.length);
        return decompressed;
    }

    /**
     * テスト向け: Content-Encoding とボディから {@link Compression} を解決する。
     *
     * @param contentEncoding Content-Encodingヘッダーの値
     * @param bodyBytes ボディバイト
     * @return 解凍に使用する Compression、非圧縮時は空
     * @throws IOException 未対応の Content-Encoding
     */
    Optional<Compression> resolveCompressionForTest(String contentEncoding, byte[] bodyBytes) throws IOException
    {
        return Optional.ofNullable(registry.resolve(contentEncoding, bodyBytes));
    }

    /**
     * HTTPヘッダー名は大文字小文字を区別しないが、{@link Map} のキー lookup は区別する。
     * まず canonical 名で O(1) 参照し、見つからなければ全エントリを走査する。
     */
    private static String getHeaderCaseInsensitive(Map<String, String> headers, String name)
    {
        if (headers == null || name == null)
        {
            return null;
        }

        // Jetty 経由では HttpField.getName() と一致することが多い
        String value = headers.get(name);
        if (value != null)
        {
            return value;
        }

        // テストや他経路でキー表記が異なる場合のフォールバック
        for (Map.Entry<String, String> entry : headers.entrySet())
        {
            if (name.equalsIgnoreCase(entry.getKey()))
            {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Content-Encoding から {@link Compression} を解決し、解凍を行う。
     */
    private static final class CompressionRegistry
    {
        private final Map<String, Compression> available;

        private CompressionRegistry(Map<String, Compression> available)
        {
            this.available = available;
        }

        static CompressionRegistry createDefault(List<Class<? extends Compression>> types)
        {
            Map<String, Compression> available = new LinkedHashMap<>();
            for (Class<? extends Compression> type : types)
            {
                register(available, initialize(type));
            }
            log.debug("解凍可能な Content-Encoding: {}",
                available.keySet().stream().sorted().collect(Collectors.joining(", ")));
            return new CompressionRegistry(Map.copyOf(available));
        }

        static CompressionRegistry of(Compression... compressions)
        {
            Map<String, Compression> available = new LinkedHashMap<>();
            for (Compression compression : compressions)
            {
                register(available, compression);
            }
            return new CompressionRegistry(Map.copyOf(available));
        }

        private static Compression initialize(Class<? extends Compression> type)
        {
            try
            {
                Compression compression = type.getDeclaredConstructor().newInstance();
                log.debug("{}: 初期化成功", type.getSimpleName());
                return compression;
            }
            catch (Throwable e)
            {
                log.error("{}: 初期化失敗", type.getSimpleName(), e);
                return null;
            }
        }

        private static void register(Map<String, Compression> available, Compression compression)
        {
            if (compression == null)
            {
                return;
            }
            available.put(compression.getEncodingName().toLowerCase(), compression);
        }

        Compression resolve(String contentEncoding, byte[] bodyBytes) throws IOException
        {
            if (isBlank(contentEncoding))
            {
                // レガシー保険: Content-Encoding 欠落時の gzip マジックバイト検出。warn で監視し、出現しなければ削除可。
                if (isGzipMagic(bodyBytes))
                {
                    log.warn("gzipをContent-Encodingなしでマジックバイト検出: bodySize={}B", bodyBytes.length);
                    return require("gzip", null);
                }
                return null;
            }

            return require(contentEncoding.toLowerCase(), contentEncoding);
        }

        Compression require(String encodingKey, String contentEncodingForError) throws IOException
        {
            Compression compression = available.get(encodingKey);
            if (compression != null)
            {
                return compression;
            }

            String header = contentEncodingForError != null ? contentEncodingForError : encodingKey;
            throw new IOException(String.format("未対応のContent-Encodingです: '%s'", header));
        }

        byte[] decompress(Compression compression, byte[] bodyBytes) throws IOException
        {
            try (InputStream compressedStream = new ByteArrayInputStream(bodyBytes);
                 InputStream decodedStream = compression.newDecoderInputStream(compressedStream);
                 ByteArrayOutputStream output = new ByteArrayOutputStream())
            {
                decodedStream.transferTo(output);
                return output.toByteArray();
            }
            catch (IOException e)
            {
                throw new IOException(String.format("%s解凍に失敗しました: encoding='%s', ボディサイズ=%d バイト",
                    compression.getName(), compression.getEncodingName(), bodyBytes.length), e);
            }
        }

        private static boolean isBlank(String value)
        {
            return value == null || value.isEmpty();
        }

        private static boolean isGzipMagic(byte[] bodyBytes)
        {
            return bodyBytes.length >= 2
                && (bodyBytes[0] & 0xFF) == 0x1f
                && (bodyBytes[1] & 0xFF) == 0x8b;
        }
    }
}
