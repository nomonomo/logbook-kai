package logbook.internal;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.RetainingResponseListener;
import org.eclipse.jetty.client.PathResponseListener;
import org.eclipse.jetty.util.StringUtil;
import java.util.concurrent.CompletableFuture;
import org.eclipse.jetty.http.HttpMethod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.web.WebView;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import logbook.internal.gui.InternalFXMLLoader;
import logbook.internal.gui.Tools;
import lombok.extern.slf4j.Slf4j;

/**
 * アップデートチェック
 *
 */
@Slf4j
public class CheckUpdate {

    /** シングルトンインスタンス */
    private static CheckUpdate INSTANCE;

    /** GitHub リポジトリのパス */
    public static final String REPOSITORY_PATH = "nomonomo/logbook-kai";

    /** 更新確認先 Github tags API */
    private static final String TAGS = "https://api.github.com/repos/" + REPOSITORY_PATH + "/tags";

    /** 更新確認先 Github releases API */
    private static final String RELEASES = "https://api.github.com/repos/" + REPOSITORY_PATH + "/releases/tags/";

    /** ダウンロードサイトを開くを選択したときに開くURL */
    private static final String OPEN_URL = "https://github.com/" + REPOSITORY_PATH + "/releases";

    /** 検索するtagの名前 */
    /* 例えばv20.1.1 の 20.1.1にマッチ */
    static final Pattern TAG_REGIX = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?$");

    /** Prerelease を使う System Property */
    private static final String USE_PRERELEASE = "logbook.use.prerelease";

    /** HTTPクライアント */
    private HttpClient httpClient;

    /**
     * プライベートコンストラクタ（シングルトンパターン）
     */
    private CheckUpdate() {
        // コンストラクタでは初期化しない（遅延初期化）
    }

    /**
     * CheckUpdateのシングルトンインスタンスを取得
     * 
     * @return CheckUpdateインスタンス
     */
    public static CheckUpdate getInstance() {
        if (INSTANCE == null) {
            synchronized (CheckUpdate.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CheckUpdate();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * HTTPクライアントを明示的に初期化（オプション）
     * 通常はgetHttpClient()で遅延初期化されるが、意図的に初期化処理を先に実行したい場合に使用する
     * Main.javaのstart()メソッドから呼び出され、アプリケーション起動時に一度だけ実行されることを想定
     * デフォルト設定を使用（connectTimeout: 5秒、followRedirects: true）
     * 
     * @throws RuntimeException HTTPクライアントの初期化に失敗した場合
     */
    public void initializeHttpClient() {
        synchronized (this) {
            if (httpClient != null) {
                // 既に初期化されている場合は何もしない
                log.debug("HTTPクライアントは既に初期化されています");
                return;
            }
            httpClient = new HttpClient();
            try {
                httpClient.start();
                log.debug("HTTPクライアントを明示的に初期化しました");
            } catch (Exception e) {
                log.error("HTTPクライアントの初期化に失敗しました", e);
                httpClient = null; // 初期化失敗時はnullに戻す
                throw new RuntimeException("HTTPクライアントの初期化に失敗しました", e);
            }
        }
    }

    /**
     * HTTPクライアントを取得（遅延初期化）
     * 初回呼び出し時に一度だけ初期化され、以降は同じインスタンスを返す
     * 明示的に初期化したい場合は、事前にinitializeHttpClient()を呼び出すことを推奨
     * デフォルト設定を使用（connectTimeout: 5秒、followRedirects: true）
     * 
     * @return HTTPクライアントインスタンス
     * @throws RuntimeException HTTPクライアントの初期化に失敗した場合
     */
    private HttpClient getHttpClient() {
        // ダブルチェックロッキングパターンでパフォーマンスを最適化
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = new HttpClient();
                    try {
                        httpClient.start();
                        log.debug("HTTPクライアントを遅延初期化しました");
                    } catch (Exception e) {
                        log.error("HTTPクライアントの初期化に失敗しました", e);
                        httpClient = null; // 初期化失敗時はnullに戻す
                        throw new RuntimeException("HTTPクライアントの初期化に失敗しました", e);
                    }
                }
            }
        }
        return httpClient;
    }

    /**
     * HTTPクライアントを停止（アプリケーション終了時のクリーンアップ用）
     * Main.javaのstop()メソッドから呼び出され、アプリケーション終了時に実行される
     * HttpClient.stop()が呼ばれると、実行中のリクエストも自動的にキャンセルされる
     * 既に停止済みの場合は何もしない
     */
    public void shutdown() {
        synchronized (this) {
            // HTTPクライアントを停止（実行中のリクエストもキャンセルされる）
            if (httpClient != null) {
                try {
                    // stop()は実行中のリクエストをキャンセルし、完了を待つ
                    httpClient.stop();
                    log.debug("HTTPクライアントを停止しました");
                } catch (Exception e) {
                    log.warn("HTTPクライアントの停止中にエラーが発生しました", e);
                } finally {
                    httpClient = null;
                }
            }
        }
    }

    public void run(Stage stage) {
        run(false, stage);
    }

    public void run(boolean isStartUp) {
        run(isStartUp, null);
    }

    public void run(boolean isStartUp, Stage stage) {
        // 非同期でバージョン情報を取得（UIスレッドをブロックしない）
        // TAGS APIのレスポンスは約12KB程度のため、RetainingResponseListenerを使用
        HttpClient client = getHttpClient();
        Request request = client.newRequest(URI.create(TAGS))
                .method(HttpMethod.GET);

        // RetainingResponseListenerを使用（レスポンスが少量のため）
        RetainingResponseListener listener = new RetainingResponseListener() {
            @Override
            public void onSuccess(Response response) {
                super.onSuccess(response); // 必須: チャンクの解放を保証

                if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                    showUpdateCheckError(isStartUp);
                    return;
                }

                try {
                    // レスポンスボディを直接JSONとして取得
                    String content = getContentAsString(StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode tags = mapper.readTree(content);
                    List<VersionInfo> candidateVersions = processTags(tags);
                    if (candidateVersions.isEmpty()) {
                        // 最新バージョンが見つからなかった場合
                        if (!isStartUp) {
                            Platform.runLater(() -> {
            Tools.Controls.alert(AlertType.INFORMATION, "更新の確認", "最新のバージョンです。", stage);
                            });
                        }
                    } else {
                        // 候補バージョンを順番にチェック（新しい順）
                        CheckUpdate.this.checkVersionsSequentially(candidateVersions, 0, isStartUp, stage);
                    }
                } catch (Exception e) {
                    log.warn("tagsの処理に失敗しました", e);
                    CheckUpdate.this.showUpdateCheckError(isStartUp);
                }
            }

            @Override
            public void onFailure(Response response, Throwable failure) {
                super.onFailure(response, failure); // 必須: チャンクの解放を保証
                log.warn("更新チェック中にエラーが発生しました", failure);
                CheckUpdate.this.showUpdateCheckError(isStartUp);
            }
        };

        // リクエストを非同期で送信
        request.send(listener);
    }

    /**
     * バージョン情報（アセット情報を含む）
     * アセット情報は、プラットフォームに応じたアセットが見つかった場合のみ設定される
     */
    record VersionInfo(String tagname, Version version, String downloadUrl, long fileSize, String body) {
        /**
         * アセット情報が設定されているかどうか
         */
        boolean hasAsset() {
            return downloadUrl != null && !downloadUrl.isEmpty() && fileSize > 0;
        }
    }

    /**
     * 更新チェックエラー時のアラートを表示
     * 
     * @param isStartUp 起動時チェックかどうか
     */
    private void showUpdateCheckError(boolean isStartUp) {
        if (!isStartUp) {
            Platform.runLater(() -> {
                Tools.Controls.alert(AlertType.WARNING, "更新の確認",
                        "更新情報の取得に失敗しました。", null);
            });
        }
    }

    /**
     * tagsのJSONを処理し、新しいバージョンを抽出してソート
     * 
     * @param tags tagsのJsonNode（配列形式）
     * @return 新しいバージョンのリスト（新しい順にソート済み、見つからなかった場合は空リスト）
     */
    List<VersionInfo> processTags(JsonNode tags) {
        if (tags == null || !tags.isArray()) {
            return Collections.emptyList();
        }

        List<VersionInfo> candidates = new ArrayList<>();

        // Githubのtagsから新しいバージョンを抽出
        for (int index = 0; index < tags.size(); index++) {
            JsonNode tagNode = tags.get(index);
            if (tagNode == null || !tagNode.has("name")) {
                continue;
            }

            String tagname = tagNode.get("name").asText();
            if (tagname == null || tagname.isEmpty()) {
                continue;
            }

            // tagの名前にバージョンを含む?実行中のバージョンより新しい?
            Matcher m = TAG_REGIX.matcher(tagname);
            if (!m.find()) {
                continue;
            }

            try {
                Version remote = new Version(m.group());
                if (Version.UNKNOWN.equals(remote) || Version.getCurrent().compareTo(remote) >= 0) {
                    continue;
                }

                // 候補に追加（bodyは後で取得）
                candidates.add(new VersionInfo(tagname, remote, null, 0, null));
            } catch (IllegalArgumentException e) {
                // バージョン形式が不正な場合はスキップ
                log.debug("不正なバージョン形式: {}", tagname, e);
                continue;
            }
        }

        // バージョン順にソート（新しい順）
        candidates.sort((a, b) -> b.version().compareTo(a.version()));

        return candidates;
    }

    /**
     * 候補バージョンを順番にチェックし、最初に有効なバージョンを見つける
     * 
     * @param candidates 候補バージョンのリスト（新しい順）
     * @param index 現在チェック中のインデックス
     * @param isStartUp 起動時チェックかどうか
     * @param stage 親ウィンドウ
     */
    private void checkVersionsSequentially(List<VersionInfo> candidates, int index,
            boolean isStartUp, Stage stage) {
        if (index >= candidates.size()) {
            // 全ての候補をチェックしたが、有効なバージョンが見つからなかった
            if (!isStartUp) {
                Platform.runLater(() -> {
                    Tools.Controls.alert(AlertType.INFORMATION, "更新の確認", "最新のバージョンです。", stage);
                });
            }
            return;
        }

        VersionInfo candidate = candidates.get(index);
        CheckUpdate.this.findLatestVersion(candidate, isStartUp, stage,
                (versionInfo) -> {
                    // 有効なバージョンが見つかった場合、UIを表示
                    Platform.runLater(() -> {
                        CheckUpdate.this.openInfo(versionInfo, isStartUp, stage);
                    });
                },
                () -> {
                    // このバージョンが無効な場合、次の候補をチェック
                    CheckUpdate.this.checkVersionsSequentially(candidates, index + 1, isStartUp, stage);
                });
    }

    /**
     * 指定されたtagのリリース情報を取得し、有効性を確認
     * アセット情報も同時に取得して、重複したAPI呼び出しを避ける
     * プラットフォームに応じたアセットが見つからない場合は、更新対象外として扱う
     * 
     * @param versionInfo バージョン情報（tag名とバージョン、アセット情報は未設定）
     * @param isStartUp 起動時チェックかどうか
     * @param stage 親ウィンドウ
     * @param onValid 有効なバージョンが見つかった場合のコールバック（アセット情報を含むVersionInfoを渡す）
     * @param onInvalid 無効なバージョンまたはプラットフォーム対応アセットが見つからない場合のコールバック
     */
    private void findLatestVersion(VersionInfo versionInfo, boolean isStartUp, Stage stage,
            Consumer<VersionInfo> onValid,
            Runnable onInvalid) {
        // リリース情報のレスポンスは約3-4KB程度のため、RetainingResponseListenerを使用
        HttpClient client = getHttpClient();
        Request releaseRequest = client.newRequest(URI.create(RELEASES + versionInfo.tagname()))
                .method(HttpMethod.GET);

        // RetainingResponseListenerを使用（レスポンスが少量のため）
        RetainingResponseListener listener = new RetainingResponseListener() {
            @Override
            public void onSuccess(Response response) {
                super.onSuccess(response); // 必須: チャンクの解放を保証

                if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                    // リリース情報が取得できない場合は無効として扱う
                    onInvalid.run();
                    return;
                }

                try {
                    // レスポンスボディを直接JSONとして取得
                    String content = getContentAsString(StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode releases = mapper.readTree(content);

                    // リリース情報の有効性をチェック（無効な場合は早期リターン）
                    if (releases == null || releases.isNull() ||
                            releases.has("message") ||
                            releases.get("draft").asBoolean(false) ||
                            (!Boolean.getBoolean(USE_PRERELEASE) && releases.get("prerelease").asBoolean(false))) {
                        onInvalid.run();
                        return;
                    }

                    // assetsが1つ以上あることを確認
                    JsonNode assets = releases.get("assets");
                    if (assets == null || !assets.isArray() || assets.size() == 0) {
                        onInvalid.run();
                        return;
                    }

                    // プラットフォームに応じたアセットを取得
                    String buildPlatform = SystemPlatform.getBuildPlatform();
                    Optional<JsonNode> foundAssetOpt = findAssetForPlatform(assets, buildPlatform);

                    // プラットフォームに応じたアセットが見つからない場合は更新対象外
                    if (foundAssetOpt.isEmpty()) {
                        log.debug("プラットフォーム {} に対応するアセットが見つかりません: {}", buildPlatform, versionInfo.tagname());
                        onInvalid.run();
                        return;
                    }

                    JsonNode foundAsset = foundAssetOpt.get();
                    String downloadUrl = foundAsset.get("browser_download_url").asText();
                    long fileSize = foundAsset.get("size").asLong();

                    // リリースノートのbody（Markdownテキスト）を取得
                    String body = releases.has("body") && !releases.get("body").isNull() 
                            ? releases.get("body").asText("") 
                            : "";
                    log.debug("リリースノートbody取得: サイズ={} bytes", body.length());

                    // アセット情報とbodyを含むVersionInfoを作成
                    VersionInfo versionInfoWithAsset = new VersionInfo(
                            versionInfo.tagname(),
                            versionInfo.version(),
                            downloadUrl,
                            fileSize,
                            body);

                    log.info("更新可能バージョンを検出：{},{}({} bytes)", versionInfo.version(), foundAsset.get("name").asText(), fileSize);
                            // 有効なバージョンとアセット情報が見つかった
                    onValid.accept(versionInfoWithAsset);
                } catch (Exception e) {
                    log.debug("リリース情報のパースに失敗: {}", versionInfo.tagname(), e);
                    onInvalid.run();
                }
            }

            @Override
            public void onFailure(Response response, Throwable failure) {
                super.onFailure(response, failure); // 必須: チャンクの解放を保証
                log.debug("リリース情報の取得に失敗: {}", versionInfo.tagname(), failure);
                onInvalid.run();
            }
        };

        // リクエストを非同期で送信
        releaseRequest.send(listener);
    }

    /**
     * エラーメッセージのHTMLを作成
     * 
     * @param errorMessage エラーメッセージ
     * @return エラーメッセージのHTML
     */
    private String createErrorMessageHtml(String errorMessage) {
        // エラーメッセージをHTMLエスケープ
        String escapedMessage = StringUtil.sanitizeXmlString(errorMessage);
        return """
                <div style='color: red; padding: 10px; border: 1px solid #ccc; border-radius: 4px;'>
                    <strong>エラー:</strong><br>
                    <pre style='white-space: pre-wrap; font-family: monospace; margin: 0;'>%s</pre>
                </div>
                """.formatted(escapedMessage);
    }

    /**
     * Markdown用のHTMLテンプレートを作成
     * 
     * @param errorMessage エラーメッセージ（nullの場合は通常のローディング表示）
     * @return HTMLテンプレート文字列
     */
    private String createMarkdownHtmlTemplate(String errorMessage) {
        String contentHtml;
        if (errorMessage != null) {
            contentHtml = createErrorMessageHtml(errorMessage);
        } else {
            contentHtml = "<p style='color: gray;'>更新内容を読み込んでいます...</p>";
        }
        
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <link href='https://github.githubassets.com/assets/github-markdown.css' rel='stylesheet'>
                    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                    <style>
                        html, body {
                            margin: 0 !important;
                            padding: 0 !important;
                        }
                        body {
                            font-family: 'Meiryo UI', Meiryo, 'Segoe UI', 'Lucida Grande', Verdana, Arial, Helvetica, sans-serif;
                            font-size: 14px !important;
                            display: flex !important;
                            flex-direction: column !important;
                            align-items: flex-start !important;
                        }
                        .markdown-body {
                            margin: 0 !important;
                            padding: 10px 15px !important;
                            font-size: 14px !important;
                        }
                        .markdown-body > *:first-child {
                            margin-top: 0 !important;
                        }
                        .markdown-body > *:last-child {
                            margin-bottom: 0 !important;
                        }
                    </style>
                </head>
                <body>
                    <div class='markdown-body' id='markdown-content'>
                        %s
                    </div>
                </body>
                </html>
                """.formatted(contentHtml);
    }

    /**
     * marked.jsを使用してMarkdownテキストをHTMLに変換して表示
     * 
     * <p>クライアント側で`marked.js`を使用して、リリースノートのMarkdownテキストをHTMLに変換します。
     * エラー時は、エラーメッセージを含むHTMLテンプレートを静的に作成して表示します。
     * 
     * @param markdownText リリースノートのMarkdownテキスト（nullまたは空文字列でないことが保証されている）
     * @param htmlTemplate HTMLテンプレート
     * @param finalHtml 最終的なHTMLを格納する配列（ラムダ式内で使用するため配列を使用）
     * @param webView WebView（UI更新用）
     */
    private void renderMarkdown(String markdownText, String htmlTemplate,
            String[] finalHtml, WebView webView) {
        log.debug("renderMarkdown開始: markdownTextサイズ={} bytes", markdownText.length());

        // HTMLテンプレートをロード
        finalHtml[0] = htmlTemplate;
        
        // openInfoメソッドは既にJavaFX Application Threadで実行されているため、
        // Platform.runLater()は不要
        webView.getEngine().loadContent(htmlTemplate);
        
        // HTMLのロード完了を待ってからJavaScriptを実行
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                try {
                    // JavaScriptの文字列リテラルとしてエスケープ
                    String escapedMarkdown = escapeJavaScriptString(markdownText);
                    // エラーメッセージのHTMLを事前に作成（JavaScript内で使用）
                    String markedJsErrorHtml = escapeJavaScriptString(createErrorMessageHtml("marked.jsの読み込みに失敗しました。"));
                    String renderErrorHtml = escapeJavaScriptString(createErrorMessageHtml("Markdownのレンダリングに失敗しました。"));
                    // marked.jsでMarkdownをHTMLに変換
                    String script = String.format("""
                        (function() {
                            try {
                                if (typeof marked === 'undefined') {
                                    // marked.jsの読み込み失敗時は、エラーメッセージのHTMLを直接設定
                                    document.getElementById('markdown-content').innerHTML = %s;
                                    return;
                                }
                                var html = marked.parse(%s, {
                                    breaks: true,
                                    gfm: true
                                });
                                document.getElementById('markdown-content').innerHTML = html;
                            } catch (error) {
                                // Markdownレンダリング失敗時は、エラーメッセージのHTMLを直接設定
                                document.getElementById('markdown-content').innerHTML = %s;
                            }
                        })();
                        """, 
                        markedJsErrorHtml,
                        escapedMarkdown,
                        renderErrorHtml);
                    webView.getEngine().executeScript(script);
                    log.debug("Markdownレンダリング完了: サイズ={} bytes", markdownText.length());
                } catch (Exception e) {
                    log.warn("JavaScript実行中にエラーが発生しました", e);
                    // JavaScript実行エラー時は、エラーメッセージのHTMLを直接設定
                    String errorHtml = createMarkdownHtmlTemplate("Markdownのレンダリングに失敗しました: " + e.getMessage());
                    finalHtml[0] = errorHtml;
                    webView.getEngine().loadContent(errorHtml);
                }
            } else if (newState == Worker.State.FAILED) {
                log.warn("HTMLの読み込みに失敗しました");
                // HTML読み込み失敗時は、エラーメッセージを含むHTMLテンプレートを静的に作成
                String errorHtml = createMarkdownHtmlTemplate("HTMLの読み込みに失敗しました");
                finalHtml[0] = errorHtml;
                webView.getEngine().loadContent(errorHtml);
            }
        });
    }

    /**
     * Javaの文字列をJavaScriptの文字列リテラルとしてエスケープ
     * 
     * <p>バッククォートを使用したテンプレートリテラル形式でエスケープすることで、
     * 改行や特殊文字を安全に扱える。
     * 
     * @param str エスケープする文字列
     * @return JavaScriptの文字列リテラル（バッククォート形式）
     */
    private String escapeJavaScriptString(String str) {
        if (str == null) {
            return "null";
        }
        // バッククォートを使用したテンプレートリテラル形式でエスケープ
        // これにより、改行や特殊文字を安全に扱える
        return "`" + str.replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("${", "\\${") + "`";
    }


    /**
     * 新しいバージョン情報ダイアログを表示
     * 
     * @param versionInfo バージョン情報（tag名、バージョン、アセット情報を含む）
     * @param isStartUp 起動時チェックかどうか
     * @param stage 親ウィンドウ
     */
    private void openInfo(VersionInfo versionInfo, boolean isStartUp, Stage stage) {
        Version o = Version.getCurrent();
        Version n = versionInfo.version();
        ButtonType update = new ButtonType("自動更新");
        ButtonType visible = new ButtonType("ダウンロードサイトを開く");
        ButtonType no = new ButtonType("後で");

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
        InternalFXMLLoader.setGlobal(alert.getDialogPane());
        alert.setTitle("新しいバージョン");
        alert.setHeaderText("新しいバージョン");
        alert.initOwner(stage);

        // メインコンテンツを作成
        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));

        // バージョン情報
        String versionInfoText = """
                現在のバージョン: %s
                新しいバージョン: %s
                """.formatted(o, n);
        Label versionLabel = new Label(versionInfoText);
        contentBox.getChildren().add(versionLabel);

        // リリースノートを非同期で取得して表示
        WebView webView = new WebView();
        webView.setPrefHeight(300);
        webView.setPrefWidth(600);

        // リリースノートのMarkdownテキストを取得
        String markdownText = versionInfo.body() != null && !versionInfo.body().trim().isEmpty() 
                ? versionInfo.body() 
                : null;

        // リンククリック時にブラウザで開く
        final boolean[] isInitialLoad = { true };
        final String[] finalHtml = { null }; // ラムダ式内で使用するため配列に

        webView.getEngine().locationProperty().addListener((ChangeListener<String>) (obs, oldLocation, newLocation) -> {
            // 初期ロード（data:スキーム）はスキップ
            if (isInitialLoad[0]) {
                isInitialLoad[0] = false;
                return;
            }

            // 外部リンクの場合、ブラウザで開く
            if (newLocation != null && !newLocation.startsWith("data:")) {
                // まず、WebViewのナビゲーションを即座にキャンセル（元のコンテンツに戻す）
                Platform.runLater(() -> {
                    if (finalHtml[0] != null) {
                        webView.getEngine().loadContent(finalHtml[0]);
                    }
                });

                // その後、ブラウザで開く
                try {
                    Desktop.getDesktop().browse(URI.create(newLocation));
                } catch (Exception e) {
                    log.warn("ブラウザを開くのに失敗しました: {}", newLocation, e);
                }
            }
        });

        ScrollPane scrollPane = new ScrollPane(webView);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        contentBox.getChildren().add(scrollPane);

        // リリースノートが空の場合は、エラーメッセージを含むHTMLテンプレートを直接表示
        if (markdownText == null || markdownText.trim().isEmpty()) {
            log.debug("リリースノートが空のため、エラー表示を表示");
            String errorHtml = createMarkdownHtmlTemplate("リリースノートがありません。");
            finalHtml[0] = errorHtml;
            webView.getEngine().loadContent(errorHtml);
        } else {
            // Markdownをレンダリングして表示
            String htmlTemplate = createMarkdownHtmlTemplate(null);
            finalHtml[0] = htmlTemplate;
            renderMarkdown(markdownText, htmlTemplate, finalHtml, webView);
        }

        // 自動更新の説明
        String autoUpdateInfo = isStartUp
            ? """
                自動更新を利用すると、次回起動時に自動的に更新されます。
                ※自動アップデートチェックは[その他]-[設定]から無効に出来ます
                """
            : """
                自動更新を利用すると、次回起動時に自動的に更新されます。
                """;
        Label infoLabel = new Label(autoUpdateInfo);
        contentBox.getChildren().add(infoLabel);

        alert.getDialogPane().setContent(contentBox);
        // 既存のボタンを削除してからカスタムボタンを追加
        // clear()を使うと×ボタンも削除されるため、setAll()を使用
        // ButtonType.CANCELを追加しないことで、×ボタンのみが動作し、キャンセルボタンは表示されない
        alert.getButtonTypes().setAll(update, visible, no);

        Optional<ButtonType> result = alert.showAndWait();
        // ×ボタンが押された場合、result.isEmpty()がtrueになる
        // noボタンが押された場合、noが返される
        // どちらも何も実行しない（noと同じ動作）
        if (result.isPresent()) {
            ButtonType selected = result.get();
            if (selected == update) {
                // ダイアログを閉じてから、非同期で自動更新を開始
                // これにより、進捗ダイアログが正しく表示される
                alert.close();
                // 非同期で実行することで、openInfoメソッドが終了しても進捗ダイアログが表示され続ける
                Platform.runLater(() -> launchUpdate(n, versionInfo, stage));
            } else if (selected == visible) {
                openBrowser();
        }
            // noボタンが押された場合、何も実行しない
        }
        // result.isEmpty()の場合（×ボタンが押された場合）、何も実行しない
    }

    /**
     * ブラウザでダウンロードサイトを開く
     * 
     * <p>このメソッドはJavaFX Application Threadから呼び出されることを前提としています。
     * {@code Desktop.getDesktop().browse()}は非ブロッキング操作のため、UIスレッドをブロックしません。
     */
    private void openBrowser() {
        try {
            Desktop.getDesktop().browse(URI.create(OPEN_URL));
        } catch (Exception e) {
            log.warn("ブラウザを開くのに失敗しました: {}", OPEN_URL, e);
        }
    }

    /**
     * 自動更新を実行
     * Jettyの非同期処理を直接使用し、完了時にJavaFXスレッドでUIを更新
     * 
     * @param newVersion 新しいバージョン
     * @param versionInfo バージョン情報（アセット情報を含む）
     * @param stage 親ウィンドウ
     */
    private void launchUpdate(Version newVersion, VersionInfo versionInfo, Stage stage) {
        log.info("自動更新を開始: バージョン {}", newVersion);

        // アセット情報の確認
        if (!versionInfo.hasAsset()) {
            log.warn("アセット情報が設定されていません: {}", versionInfo.tagname());
            showUpdateErrorDialog(new IllegalStateException("アセット情報が取得できませんでした"), stage);
            return;
        }

        // 初期化: ルートディレクトリの取得
        Path rootDir;
        try {
            rootDir = getRootDirectory();
        } catch (Exception e) {
            log.warn("更新情報の取得に失敗しました", e);
            showUpdateErrorDialog(e, stage);
            return;
        }

        // セットアップ: ディレクトリ作成
        Path updateDir;
        Path zipFile;
        try {
            updateDir = rootDir.resolve("update");
            Files.createDirectories(updateDir);
            zipFile = updateDir.resolve("logbook-win.zip");
            log.info("ダウンロード中: {} (期待されるサイズ: {} bytes)", versionInfo.downloadUrl(), versionInfo.fileSize());
        } catch (Exception e) {
            log.warn("自動更新の開始に失敗しました", e);
            showUpdateErrorDialog(e, stage);
            return;
        }

        // 進捗ダイアログを表示
        Alert progressDialog = showDownloadingDialog(stage, versionInfo.fileSize());
        
        // 進捗バーとステータスラベルの参照を取得（lookupのタイミング問題を回避）
        ProgressBar progressBar = (ProgressBar) progressDialog.getDialogPane().lookup("#progressBar");
        Label statusLabel = (Label) progressDialog.getDialogPane().lookup("#statusLabel");

        // ダウンロード（リトライ機能付き、非同期処理）
        CompletableFuture<Path> downloadFuture = downloadWithProgressAndRetry(
                versionInfo.downloadUrl(), zipFile, versionInfo.fileSize(),
                createProgressCallback(progressBar, statusLabel));

        downloadFuture
                .thenCompose((Path downloadedPath) -> {
                    // 解凍（非同期で実行）
                    Platform.runLater(() -> {
                        if (progressBar != null) {
                            progressBar.setProgress(0.8);
                        }
                        if (statusLabel != null) {
                            statusLabel.setText("解凍中...");
                        }
                    });
                    Path tempDir = updateDir.resolve("temp");
                    log.info("解凍中...");
                    try {
                        unzip(zipFile, tempDir);

                        // ファイルを配置
                        Platform.runLater(() -> {
                            if (progressBar != null) {
                                progressBar.setProgress(0.9);
                            }
                            if (statusLabel != null) {
                                statusLabel.setText("ファイルを配置中...");
                            }
                        });
                        Path extractedLogbook = findLogbookDirectory(tempDir);
                        Path targetLogbook = updateDir.resolve("logbook");

                        if (Files.exists(targetLogbook)) {
                            deleteDirectory(targetLogbook);
                        }
                        Files.move(extractedLogbook, targetLogbook);
                        log.info("新バージョンを配置: {}", targetLogbook);

                        // 一時ファイルを削除
                        deleteDirectory(tempDir);
                        Files.delete(zipFile);

                        // 更新情報を保存
                        saveUpdateInfo(updateDir.resolve("update.json"), newVersion);

                        log.info("更新準備完了");

                        // 完了時にダイアログを閉じて、確認ダイアログを表示
                        Platform.runLater(() -> {
                            progressDialog.close();
                            showUpdateReadyDialog(newVersion, rootDir, stage);
                        });

                        return CompletableFuture.<Void> completedFuture(null);
                    } catch (IOException e) {
                        return CompletableFuture.<Void> failedFuture(e);
                    }
                })
                .exceptionally((Throwable throwable) -> {
                    Exception e = throwable instanceof Exception ? (Exception) throwable
                            : new Exception("自動更新の準備に失敗しました", throwable);
                    log.warn("自動更新の準備に失敗しました", e);
                    Platform.runLater(() -> {
                        progressDialog.close();
                        showUpdateErrorDialog(e, stage);
                    });
                    return null;
                });
    }

    /**
     * ルートディレクトリを取得（logbook-win/）
     */
    private Path getRootDirectory() {
        String javaHome = System.getProperty("java.home");
        // java.home = C:\Apps\logbook-win\logbook
        // logbook → logbook-win
        return Paths.get(javaHome).getParent();
    }

    /**
     * logbook/ ディレクトリを探す
     */
    private Path findLogbookDirectory(Path extractedDir) throws IOException {
        try (Stream<Path> walk = Files.walk(extractedDir, 3)) {
            return walk
                    .filter(Files::isDirectory)
                    .filter(p -> "logbook".equals(p.getFileName().toString()))
                    .filter(p -> Files.exists(p.resolve("bin/javaw.exe")) ||
                            Files.exists(p.resolve("bin/java.exe")) ||
                            Files.exists(p.resolve("bin/java")))
                    .findFirst()
                    .orElseThrow(() -> new IOException("logbook ディレクトリが見つかりません"));
        }
    }

    /**
     * 進捗表示付きダウンロード（リトライ機能付き、非同期処理）
     * 
     * @param url ダウンロードURL
     * @param destination 保存先
     * @param expectedSize 期待されるファイルサイズ
     * @param progressCallback 進捗更新コールバック
     * @return CompletableFuture<Path> ダウンロード完了時の保存先パス
     */
    private CompletableFuture<Path> downloadWithProgressAndRetry(String url, Path destination,
            long expectedSize,
            ProgressCallback progressCallback) {
        return downloadWithProgressAndRetryInternal(url, destination, expectedSize, progressCallback, 1, 3);
    }

    /**
     * 進捗表示付きダウンロード（リトライ機能付き、内部実装）
     * 再帰的にリトライを実装
     */
    private CompletableFuture<Path> downloadWithProgressAndRetryInternal(String url, Path destination,
            long expectedSize,
            ProgressCallback progressCallback,
            int attempt, int maxRetries) {
        // 最大試行回数を超えている場合は即座に失敗を返す
        if (attempt > maxRetries) {
            return CompletableFuture.<Path> failedFuture(
                    new IOException("ダウンロードに失敗しました（最大試行回数: " + maxRetries + "）"));
        }

        log.info("ダウンロード試行 {} / {}", attempt, maxRetries);

        // リトライ前の処理（2回目以降）
        CompletableFuture<Void> preRetryFuture;
        if (attempt > 1) {
            // リトライ前に少し待機してから再試行（非同期で待機）
            // 既存ファイルの削除は不要（downloadWithProgressAsyncでoverwrite=trueを指定しているため、
            // PathResponseListenerのコンストラクタで自動的に上書きされる）
            preRetryFuture = CompletableFuture
                    .<Void> supplyAsync(() -> {
                        try {
                            Thread.sleep(1000);
                            return null;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }, ThreadManager.getExecutorService());
        } else {
            // 初回は待機不要（即座に完了するCompletableFuture）
            preRetryFuture = CompletableFuture.completedFuture(null);
        }

        // ダウンロードを非同期で実行（リトライ前の待機後に実行）
        return preRetryFuture
                .thenCompose((Void v) -> downloadWithProgressAsync(url, destination, expectedSize, progressCallback))
                .thenCompose((Path path) -> {
                    try {
                        // ファイルサイズをチェック
                        long actualSize = Files.size(path);
                        if (actualSize == expectedSize) {
                            log.info("ダウンロード完了: {} bytes (サイズ検証OK)", actualSize);
                            return CompletableFuture.<Path> completedFuture(path);
                        } else {
                            log.warn("ファイルサイズ不一致: 期待値={}, 実際={}", expectedSize, actualSize);
                            // 再帰呼び出しでリトライ（待機処理は再帰呼び出し先の先頭で実施される）
                            return downloadWithProgressAndRetryInternal(
                                    url, destination, expectedSize, progressCallback, attempt + 1, maxRetries);
                        }
                    } catch (IOException e) {
                        return CompletableFuture.<Path> failedFuture(e);
                    }
                })
                .exceptionallyCompose((Throwable throwable) -> {
                    log.warn("ダウンロード失敗（試行 {} / {}）: {}", attempt, maxRetries, throwable.getMessage());
                    // 再帰呼び出しでリトライ（待機処理は再帰呼び出し先の先頭で実施される）
                    return downloadWithProgressAndRetryInternal(
                            url, destination, expectedSize, progressCallback, attempt + 1, maxRetries);
                });
    }

    /**
     * 進捗表示付きダウンロード（1回の試行、非同期処理）
     * PathResponseListenerを使用してファイルへの書き込みを自動化
     * 進捗管理はPathResponseListenerを継承したクラスで実装
     * 
     * @param url ダウンロードURL
     * @param destination 保存先
     * @param expectedSize 期待されるファイルサイズ
     * @param progressCallback 進捗更新コールバック
     * @return CompletableFuture<Path> ダウンロード完了時の保存先パス
     */
    private CompletableFuture<Path> downloadWithProgressAsync(String url, Path destination,
            long expectedSize,
            ProgressCallback progressCallback) {
        URI uri = URI.create(url);
        HttpClient client = getHttpClient();
        Request request = client.newRequest(uri)
                .method(HttpMethod.GET);

        // PathResponseListenerを継承して進捗管理を追加
        // PathResponseListenerがファイルへの書き込みを自動的に処理するため、コードが簡潔になる
        // PathResponseListenerのコンストラクタでFileChannel.open()が呼ばれ、
        // HTTPリクエストを送信する前にファイルが作成されるため、その時点でIOExceptionが発生する可能性がある
        // （例：ディスクフル、権限不足、ファイルがロックされているなど）
        ProgressTrackingPathResponseListener listener;
        try {
            listener = new ProgressTrackingPathResponseListener(
                    destination, true, expectedSize, progressCallback);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        // リクエストを非同期で送信
        request.send(listener);

        // CompletableFutureを返す（非同期で完了を待機）
        return listener
                .thenApply((PathResponseListener.PathResponse pathResponse) -> {
                    // ログ出力（ファイルサイズ取得に失敗しても処理は続行）
                    try {
                        final long mbDivisor = 1024 * 1024;
                        long downloadedSize = Files.size(pathResponse.path());
                        log.info("ダウンロード完了: {} MB", downloadedSize / mbDivisor);
                    } catch (IOException e) {
                        log.debug("ファイルサイズの取得に失敗しました（ログ出力のみ）", e);
                    }
                    return pathResponse.path();
                })
                .exceptionallyCompose((Throwable throwable) -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof IOException) {
                        return CompletableFuture.<Path> failedFuture(cause);
                    }
                    return CompletableFuture.<Path> failedFuture(
                            new IOException("ダウンロードに失敗しました", cause));
                });
    }

    /**
     * PathResponseListenerを継承して進捗管理を追加
     * PathResponseListenerがファイルへの書き込みを自動的に処理するため、コードが簡潔になる
     */
    private class ProgressTrackingPathResponseListener extends PathResponseListener {
        private final long expectedSize;
        private final ProgressCallback progressCallback;
        private long downloaded = 0;
        private long lastUpdateTime = 0;
        private static final long UPDATE_INTERVAL_MS = 100; // 進捗更新の間隔（100ミリ秒）

        public ProgressTrackingPathResponseListener(Path path, boolean overwrite,
                long expectedSize, ProgressCallback progressCallback)
                throws IOException {
            super(path, overwrite);
            this.expectedSize = expectedSize > 0 ? expectedSize : 0;
            this.progressCallback = progressCallback;
        }

        @Override
        public void onHeaders(Response response) {
            super.onHeaders(response);

            // Content-Lengthヘッダーからファイルサイズを取得（expectedSizeが0の場合）
            if (expectedSize == 0 && progressCallback != null) {
                long contentLength = response.getHeaders().getLongField("content-length");
                if (contentLength > 0) {
                    // ファイルサイズが確定した時点で進捗を更新
                    progressCallback.update(0, contentLength);
                }
            }
        }

        @Override
        public void onContent(Response response, ByteBuffer content) {
            // super.onContent()を呼ぶ前に、バッファのサイズを取得
            // super.onContent()が呼ばれた後は、バッファが消費されている可能性がある
            int chunkSize = content.remaining();
            
            super.onContent(response, content);

            // 進捗を更新（更新頻度を制限してUIスレッドへの負荷を軽減）
            if (progressCallback != null && chunkSize > 0) {
                downloaded += chunkSize;
                long fileSize = expectedSize > 0 ? expectedSize : response.getHeaders().getLongField("content-length");
                if (fileSize > 0) {
                    // 一定間隔（100ms）ごとに進捗を更新
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || downloaded >= fileSize) {
                        progressCallback.update(downloaded, fileSize);
                        lastUpdateTime = currentTime;
                    }
                }
            }
        }
    }

    /**
     * ZIPファイルを解凍
     */
    private void unzip(Path zipFile, Path destDir) throws IOException {
        Files.createDirectories(destDir);

        // 正規化されたdestDirを保持（Zip Slip対策）
        Path normalizedDestDir = destDir.normalize();

        try (ZipInputStream zis = new ZipInputStream(
                Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // エントリ名の正規化（Zip Slip対策の最初のチェック）
                String entryName = entry.getName();
                if (entryName.contains("..")) {
                    throw new IOException("不正なZIPエントリ: " + entryName);
                }

                Path entryPath = normalizedDestDir.resolve(entryName).normalize();

                // Zip Slip脆弱性対策（二重チェック）
                if (!entryPath.startsWith(normalizedDestDir)) {
                    throw new IOException("不正なZIPエントリ: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Path parent = entryPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    // transferTo()を使用して効率的にコピー（大きなファイルに有効）
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        zis.transferTo(out);
                    }

                    // 実行権限を復元（Unix系）
                    if (SystemPlatform.getOs() != SystemPlatform.OsType.WINDOWS && entryPath.toString().contains("/bin/")) {
                        entryPath.toFile().setExecutable(true, false);
                    }
                }

                zis.closeEntry();
            }
        }
    }

    /**
     * 更新情報を保存
     */
    private void saveUpdateInfo(Path file, Version version) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("version", version.toString());
        root.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        mapper.writeValue(Files.newOutputStream(file), root);
    }

    /**
     * ディレクトリを削除
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir))
            return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("削除失敗: {}", path);
                        }
                    });
        }
    }

    /**
     * ダウンロード進捗ダイアログを表示
     * 
     * @param stage 親ウィンドウ
     * @param totalSize 総ファイルサイズ（バイト）
     * @return 進捗表示用のAlert
     */
    private Alert showDownloadingDialog(Stage stage, long totalSize) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
        InternalFXMLLoader.setGlobal(alert.getDialogPane());
        alert.setTitle("自動更新");
        alert.setHeaderText("ダウンロード中");

        // 進捗バーを作成
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setId("progressBar"); // IDを設定して後で更新できるようにする

        // ステータスラベルを作成
        Label statusLabel = new Label("新しいバージョンをダウンロードしています...");
        statusLabel.setWrapText(true);
        statusLabel.setId("statusLabel"); // IDを設定して後で更新できるようにする

        // サイズ情報ラベルを作成
        String sizeInfo = String.format("%.2f MB", totalSize / (1024.0 * 1024.0));
        Label sizeLabel = new Label("総サイズ: " + sizeInfo);

        // VBoxに配置
        VBox contentBox = new VBox(10);
        contentBox.getChildren().addAll(statusLabel, progressBar, sizeLabel);

        alert.getDialogPane().setContent(contentBox);
        alert.initOwner(stage);

        alert.show();

        return alert;
    }

    /**
     * 進捗コールバック
     */
    @FunctionalInterface
    private interface ProgressCallback {
        void update(long downloaded, long total);
    }

    /**
     * 進捗コールバックを作成
     * 
     * @param progressBar 進捗バー
     * @param statusLabel ステータスラベル
     * @return 進捗コールバック
     */
    private ProgressCallback createProgressCallback(ProgressBar progressBar, Label statusLabel) {
        return (downloaded, total) -> {
            // 進捗更新をJavaFXスレッドで実行
            double progress = total > 0 ? (downloaded * 100.0 / total) : 0.0;
            double downloadedMB = downloaded / (1024.0 * 1024.0);
            double totalMB = total / (1024.0 * 1024.0);
            int percent = total > 0 ? (int) ((downloaded * 100) / total) : 0;
            String message = String.format("ダウンロード中: %d%% (%.2f / %.2f MB)",
                    percent, downloadedMB, totalMB);
            // 進捗バーとステータスラベルの参照を直接使用
            Platform.runLater(() -> {
                if (progressBar != null) {
                    progressBar.setProgress(progress / 100.0);
                }
                if (statusLabel != null) {
                    statusLabel.setText(message);
                }
            });
        };
    }

    /**
     * 更新準備完了ダイアログ
     */
    private void showUpdateReadyDialog(Version newVersion, Path rootDir, Stage stage) {
        ButtonType exitNow = new ButtonType("今すぐ終了");
        ButtonType applyLater = new ButtonType("次回起動時に適用");

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
        InternalFXMLLoader.setGlobal(alert.getDialogPane());
        alert.setTitle("更新準備完了");
        alert.setHeaderText("更新の準備が完了しました");
        alert.setContentText(
                "新しいバージョンのダウンロードと展開が完了しました。\n\n" +
                        "現在のバージョン: " + Version.getCurrent() + "\n" +
                        "新しいバージョン: " + newVersion + "\n\n" +
                        "「今すぐ終了」を選択すると、アプリケーションを終了します。\n" +
                        "次回起動時に更新が自動的に適用されます。");
        alert.initOwner(stage);
        alert.getButtonTypes().setAll(exitNow, applyLater);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == exitNow) {
            // アプリケーションを終了（updateフォルダは残す → 次回起動時に適用）
            log.info("ユーザー要求によりアプリケーションを終了します");
            Platform.exit();
        }
        // applyLater の場合は何もしない（次回起動時に適用）
    }

    /**
     * 更新エラーダイアログ
     */
    private void showUpdateErrorDialog(Exception e, Stage stage) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
        InternalFXMLLoader.setGlobal(alert.getDialogPane());
        alert.setTitle("更新エラー");
        alert.setHeaderText("自動更新に失敗しました");
        alert.setContentText("エラー: " + e.getMessage() + "\n\n" +
                "ダウンロードサイトから手動で更新してください。");
        alert.initOwner(stage);

        ButtonType openSite = new ButtonType("ダウンロードサイトを開く");
        ButtonType close = new ButtonType("閉じる");
        alert.getButtonTypes().setAll(openSite, close);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == openSite) {
            openBrowser();
        }
    }

    /**
     * プラットフォームに応じたアセットを検索します。
     * 
     * <p>プラットフォーム固有のアセットを優先的に検索し、
     * 見つからない場合は汎用アセットを検索します。</p>
     * 
     * @param assets アセットのJSONノード配列
     * @param buildPlatform ビルドプラットフォーム（win, mac, mac-aarch64 など）
     * @return 見つかったアセットのOptional、見つからない場合は空のOptional
     */
    public Optional<JsonNode> findAssetForPlatform(JsonNode assets, String buildPlatform) {
        if (assets == null || !assets.isArray() || assets.size() == 0) {
            return Optional.empty();
        }
        
        List<String> prefixes = getAssetPrefixes(buildPlatform);

        // Stream APIを使用してアセットを検索（優先順位順）
        Optional<JsonNode> foundAssetOpt = prefixes.stream()
                .flatMap(prefix -> StreamSupport.stream(assets.spliterator(), false)
                        .filter(assetNode -> {
                            String name = assetNode.get("name").asText("");
                            return name.startsWith(prefix) && name.endsWith(".zip");
                        })
                        .findFirst()
                        .stream())
                .findFirst();

        // プラットフォーム固有のアセットが見つからない場合、汎用アセットを検索
        if (foundAssetOpt.isEmpty()) {
            foundAssetOpt = StreamSupport.stream(assets.spliterator(), false)
                    .filter(assetNode -> {
                        String name = assetNode.get("name").asText("");
                        return name.startsWith("logbook") && name.endsWith(".zip");
                    })
                    .findFirst();
        }
        
        return foundAssetOpt;
    }
    
    /**
     * プラットフォームに応じたアセット名のプレフィックスリストを取得（優先順位順）
     * 
     * <p>ビルド時のプラットフォーム情報（win, mac, mac-aarch64）に基づいて、
     * 対応するアセットファイル名のプレフィックスを返します。</p>
     * 
     * <p>ファイル名形式: logbook-{platform}.zip</p>
     * 
     * @param buildPlatform ビルドプラットフォーム（win, mac, mac-aarch64 など）
     * @return プレフィックスリスト（優先順位順）
     */
    public List<String> getAssetPrefixes(String buildPlatform) {
        return switch (buildPlatform) {
        case "win" -> List.of("logbook-win.zip");
        case "mac" -> List.of("logbook-mac.zip");
        case "mac-aarch64" -> List.of("logbook-mac-aarch64.zip");
        case "linux" -> List.of("logbook-linux.zip", "logbook-kai-linux_", "logbook-kai-ubuntu_");
        default -> List.of(""); // フォールバック
        };
    }

}
