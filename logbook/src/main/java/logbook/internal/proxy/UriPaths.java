package logbook.internal.proxy;

import java.net.URI;

/**
 * ログ・集計用の URI パス正規化。
 * <p>
 * アクセスログ・API キャプチャの {@code uriPath} は本クラスの {@link #normalize(String)} に従う。
 * API ルーティング（{@code getRequestURI()}）には使わない。
 * </p>
 */
public final class UriPaths {

    private UriPaths() {
    }

    /**
     * URI からクエリ・フラグメントを除いたパス部分を返す。
     *
     * @param uri リクエスト URI（クエリ・絶対 URL を含む場合あり）
     * @return パス部分（null/空入力は {@code "/"}）
     */
    public static String normalize(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }

        if (uri.startsWith("/") || !uri.contains("://")) {
            return stripQueryAndFragment(uri);
        }

        try {
            String path = URI.create(uri).getPath();
            if (path == null || path.isEmpty()) {
                return "/";
            }
            return path;
        } catch (IllegalArgumentException e) {
            return stripQueryAndFragment(uri);
        }
    }

    private static String stripQueryAndFragment(String uri) {
        int end = uri.length();
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            end = queryIndex;
        }
        int fragmentIndex = uri.indexOf('#');
        if (fragmentIndex >= 0 && fragmentIndex < end) {
            end = fragmentIndex;
        }
        String path = uri.substring(0, end);
        return path.isEmpty() ? "/" : path;
    }
}
