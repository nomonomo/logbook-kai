package logbook.internal.proxy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTPリクエスト/レスポンスペアをより効率的に管理するCaptureHolderの改良版。
 * <p>
 * このクラスは、より優れたメモリ管理と構造でHTTPトランザクションデータを格納します:
 * <ul>
 * <li>メタデータ（ヘッダー）とボディデータを分離</li>
 * <li>生のバイト列とメタデータへの効率的なアクセスを提供</li>
 * <li>単一トンネル内での複数トランザクションをサポート（Keep-Alive）</li>
 * </ul>
 */
public class CaptureHolder2 {
    
    /**
     * メタデータとボディを持つ単一のHTTPリクエストを表します。
     */
    public static class HttpRequest {
        private String method;
        private String uri;
        private String version;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final List<byte[]> bodyChunks = new ArrayList<>();
        private int totalBodySize = 0;
        
        public void setRequestLine(String method, String uri, String version) {
            this.method = method;
            this.uri = uri;
            this.version = version;
        }
        
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }
        
        public void addBodyChunk(byte[] data) {
            if (data != null && data.length > 0) {
                bodyChunks.add(data);
                totalBodySize += data.length;
            }
        }
        
        public String getMethod() {
            return method;
        }
        
        public String getUri() {
            return uri;
        }
        
        public String getVersion() {
            return version;
        }
        
        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);
        }
        
        public String getHeader(String name) {
            return headers.get(name);
        }

        public String getContentType() {
            return headers.get("Content-Type");
        }
        
        public int getBodySize() {
            return totalBodySize;
        }
        
        public InputStream getBodyAsStream() {
            if (bodyChunks.isEmpty()) {
                return new ByteArrayInputStream(new byte[0]);
            }
            if (bodyChunks.size() == 1) {
                return new ByteArrayInputStream(bodyChunks.get(0));
            }
            // 複数チャンク - 効率のためSequenceInputStreamを使用
            List<InputStream> streams = new ArrayList<>(bodyChunks.size());
            for (byte[] chunk : bodyChunks) {
                streams.add(new ByteArrayInputStream(chunk));
            }
            return new SequenceInputStream(Collections.enumeration(streams));
        }
        
        public byte[] getBodyAsBytes() {
            if (totalBodySize == 0) {
                return new byte[0];
            }
            byte[] result = new byte[totalBodySize];
            int offset = 0;
            for (byte[] chunk : bodyChunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }
        
        public void clear() {
            method = null;
            uri = null;
            version = null;
            headers.clear();
            bodyChunks.clear();
            totalBodySize = 0;
        }
    }
    
    /**
     * メタデータとボディを持つ単一のHTTPレスポンスを表します。
     */
    public static class HttpResponse {
        private String version;
        private int status;
        private String reason;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final List<byte[]> bodyChunks = new ArrayList<>();
        private int totalBodySize = 0;
        private long contentLength = -1;  // Content-Lengthヘッダーの値（未設定時は-1）
        
        public void setStatusLine(String version, int status, String reason) {
            this.version = version;
            this.status = status;
            this.reason = reason;
        }
        
        public void addHeader(String name, String value) {
            headers.put(name, value);
            
            // Content-Lengthヘッダーを自動的にパースして保存
            if ("Content-Length".equalsIgnoreCase(name) && value != null) {
                try {
                    contentLength = Long.parseLong(value.trim());
                } catch (NumberFormatException e) {
                    // パース失敗時は-1のまま（無効なContent-Length）
                    contentLength = -1;
                }
            }
        }
        
        public void addBodyChunk(byte[] data) {
            if (data != null && data.length > 0) {
                bodyChunks.add(data);
                totalBodySize += data.length;
            }
        }
        
        public String getVersion() {
            return version;
        }
        
        public int getStatus() {
            return status;
        }
        
        public String getReason() {
            return reason;
        }
        
        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);
        }
        
        public String getHeader(String name) {
            return headers.get(name);
        }
        
        public String getContentType() {
            return headers.get("Content-Type");
        }
        
        /**
         * Content-Lengthヘッダーの値を取得する（ネットワーク送信されるボディのバイト数）。
         * 圧縮されている場合は圧縮後のサイズ。
         * 
         * @return Content-Lengthの値、未設定または無効な場合は-1
         */
        public long getContentLength() {
            return contentLength;
        }
        
        /**
         * 実際に受信したボディのバイト数を取得する（圧縮後のサイズ）。
         * 
         * @return 受信バイト数
         */
        public int getBodySize() {
            return totalBodySize;
        }
        
        public InputStream getBodyAsStream() {
            if (bodyChunks.isEmpty()) {
                return new ByteArrayInputStream(new byte[0]);
            }
            if (bodyChunks.size() == 1) {
                return new ByteArrayInputStream(bodyChunks.get(0));
            }
            // 複数チャンク - 効率のためSequenceInputStreamを使用
            List<InputStream> streams = new ArrayList<>(bodyChunks.size());
            for (byte[] chunk : bodyChunks) {
                streams.add(new ByteArrayInputStream(chunk));
            }
            return new SequenceInputStream(Collections.enumeration(streams));
        }
        
        public byte[] getBodyAsBytes() {
            if (totalBodySize == 0) {
                return new byte[0];
            }
            byte[] result = new byte[totalBodySize];
            int offset = 0;
            for (byte[] chunk : bodyChunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }
        
        public void clear() {
            version = null;
            status = 0;
            reason = null;
            headers.clear();
            bodyChunks.clear();
            totalBodySize = 0;
            contentLength = -1;
        }
    }
    
    /**
     * 単一のHTTPトランザクション（リクエスト+レスポンスのペア）を表します。
     */
    public static class HttpTransaction {
        private final HttpRequest request = new HttpRequest();
        private final HttpResponse response = new HttpResponse();
        
        public HttpRequest getRequest() {
            return request;
        }
        
        public HttpResponse getResponse() {
            return response;
        }
        
        public void clear() {
            request.clear();
            response.clear();
        }
    }
    
    // 構築中の現在のトランザクション
    private HttpTransaction currentTransaction = new HttpTransaction();
    
    // 完了したトランザクションのリスト（Keep-Alive接続用）
    private final List<HttpTransaction> completedTransactions = new ArrayList<>();
    
    /**
     * 構築中の現在のトランザクションを取得します。
     * @return 現在のHttpTransaction
     */
    public HttpTransaction getCurrentTransaction() {
        return currentTransaction;
    }
    
    /**
     * 構築中の現在のHTTPリクエストを取得します。
     * @return 現在のHttpRequest
     */
    public HttpRequest getCurrentRequest() {
        return currentTransaction.getRequest();
    }
    
    /**
     * 構築中の現在のHTTPレスポンスを取得します。
     * @return 現在のHttpResponse
     */
    public HttpResponse getCurrentResponse() {
        return currentTransaction.getResponse();
    }
    
    /**
     * 現在のトランザクションを完了とマークし、新しいトランザクションを開始します。
     * これは、リクエストとレスポンスの両方が完了した際に呼び出されます（Keep-Alive用）。
     */
    public void completeTransaction() {
        if (currentTransaction.getRequest().getMethod() != null || 
            currentTransaction.getResponse().getStatus() != 0) {
            completedTransactions.add(currentTransaction);
            currentTransaction = new HttpTransaction();
        }
    }
    
    /**
     * 完了したすべてのトランザクションを取得します。
     * @return 完了したHttpTransactionオブジェクトのリスト
     */
    public List<HttpTransaction> getCompletedTransactions() {
        return Collections.unmodifiableList(completedTransactions);
    }
    
    /**
     * 最後に完了したトランザクション、または完了したものがない場合は現在のトランザクションを取得します。
     * @return 最新のHttpTransaction
     */
    public HttpTransaction getLastTransaction() {
        if (!completedTransactions.isEmpty()) {
            return completedTransactions.get(completedTransactions.size() - 1);
        }
        return currentTransaction;
    }
    
    /**
     * すべてのデータをクリアし、初期状態にリセットします。
     */
    public void clear() {
        currentTransaction.clear();
        for (HttpTransaction transaction : completedTransactions) {
            transaction.clear();
        }
        completedTransactions.clear();
    }
    
    /**
     * 総メモリ使用量の推定値を取得します（モニタリング用）。
     * @return おおよそのメモリ使用量（バイト単位）
     */
    public long getMemoryUsage() {
        long total = 0;
        total += currentTransaction.getRequest().getBodySize();
        total += currentTransaction.getResponse().getBodySize();
        for (HttpTransaction transaction : completedTransactions) {
            total += transaction.getRequest().getBodySize();
            total += transaction.getResponse().getBodySize();
        }
        return total;
    }
}

