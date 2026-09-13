package logbook.internal;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jetty.util.StringUtil;

import com.fasterxml.jackson.annotation.JsonProperty;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import logbook.internal.gui.InternalFXMLLoader;
import logbook.internal.gui.Tools;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

/**
 * アップデートチェック
 *
 */
@Slf4j
public class CheckUpdate {

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

    /** HTTP User-Agent */
    private static final String USER_AGENT = "logbook-kai";

    /** 接続タイムアウト */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(60);

    /** API リクエストタイムアウト */
    private static final Duration API_TIMEOUT = Duration.ofSeconds(60);

    /** ダウンロードリクエストタイムアウト */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    /** ダウンロード用バッファサイズ */
    private static final int BUFFER_SIZE = 8192;

    /** 進捗更新の間隔（ミリ秒） */
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 100;

    /** ダウンロード最大試行回数 */
    private static final int DOWNLOAD_MAX_RETRIES = 3;

    /** HTTPクライアント（クラス内で使い回し） */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 現在のバージョンを取得するSupplier（テスト時にモック化可能） */
    private Supplier<Version> versionSupplier = Version::getCurrent;

    /**
     * GitHub tags API の要素
     */
    record GitHubTag(String name) {
    }

    /**
     * GitHub releases API のアセット
     */
    record GitHubAsset(
            String name,
            long size,
            @JsonProperty("browser_download_url") String browserDownloadUrl) {
    }

    /**
     * GitHub releases API のレスポンス（利用フィールドのみ）
     */
    record GitHubRelease(
            String message,
            boolean draft,
            boolean prerelease,
            String body,
            List<GitHubAsset> assets) {
    }

    /**
     * バージョン情報（アセット情報を含む）
     * アセット情報は、プラットフォームに応じたアセットが見つかった場合のみ設定される
     */
    record VersionInfo(String tagname, Version version, String downloadUrl, long fileSize, String body, String name) {
        /**
         * アセット情報が設定されているかどうか
         *
         * <p>アセット情報が完全に設定されている場合のみtrueを返します。
         * downloadUrl、fileSize、nameのすべてが有効な値である必要があります。</p>
         */
        boolean hasAsset() {
            return downloadUrl != null && !downloadUrl.isEmpty()
                    && fileSize > 0
                    && name != null && !name.isEmpty();
        }
    }

    /**
     * 更新チェック結果（UI 方針を含まない）
     */
    sealed interface UpdateCheckResult {
        /** 更新可能なバージョンが見つかった */
        record Available(VersionInfo versionInfo) implements UpdateCheckResult {
        }

        /** 利用可能な更新はない（最新、または対応アセットなし） */
        record UpToDate() implements UpdateCheckResult {
        }

        /** 通信・パース等でチェック自体に失敗した */
        record Failed(Exception cause) implements UpdateCheckResult {
        }
    }

    /**
     * バージョン取得用のSupplierを設定（テスト用）
     *
     * @param versionSupplier バージョンを取得するSupplier
     */
    void setVersionSupplier(Supplier<Version> versionSupplier) {
        this.versionSupplier = versionSupplier;
    }

    /**
     * 起動時の更新チェック。更新がある場合のみダイアログを表示する。
     *
     * @param stage 親ウィンドウ
     */
    public void runOnStartup(Stage stage) {
        ThreadManager.getExecutorService().execute(() -> {
            UpdateCheckResult result = findAvailableUpdate();
            if (result instanceof UpdateCheckResult.Available available) {
                Platform.runLater(() -> openInfo(available.versionInfo(), stage, true));
            } else if (result instanceof UpdateCheckResult.Failed failed) {
                if (failed.cause() != null) {
                    log.warn("起動時の更新チェックに失敗しました", failed.cause());
                } else {
                    log.warn("起動時の更新チェックに失敗しました");
                }
            }
        });
    }

    /**
     * メニューからの更新チェック。結果に応じて Alert または更新ダイアログを表示する。
     *
     * @param stage 親ウィンドウ
     */
    public void runFromMenu(Stage stage) {
        ThreadManager.getExecutorService().execute(() -> {
            UpdateCheckResult result = findAvailableUpdate();
            switch (result) {
            case UpdateCheckResult.Available available ->
                Platform.runLater(() -> openInfo(available.versionInfo(), stage, false));
            case UpdateCheckResult.UpToDate ignored ->
                Platform.runLater(() -> Tools.Controls.alert(
                        AlertType.INFORMATION, "更新の確認", "最新のバージョンです。", stage));
            case UpdateCheckResult.Failed failed -> {
                if (failed.cause() != null) {
                    log.warn("更新チェック中にエラーが発生しました", failed.cause());
                } else {
                    log.warn("更新チェック中にエラーが発生しました");
                }
                Platform.runLater(() -> Tools.Controls.alert(
                        AlertType.WARNING, "更新の確認", "更新情報の取得に失敗しました。", null));
            }
            }
        });
    }

    /**
     * 更新の有無を調べる（UI なし・同期）。
     *
     * @return チェック結果
     */
    UpdateCheckResult findAvailableUpdate() {
        try {
            HttpRequest request = newApiGet(TAGS);
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                return new UpdateCheckResult.Failed(null);
            }

            List<GitHubTag> tags = JsonMappers.LENIENT_READER
                    .forType(new TypeReference<List<GitHubTag>>() {
                    })
                    .readValue(response.body());
            List<VersionInfo> candidateVersions = processTags(tags);
            if (candidateVersions.isEmpty()) {
                return new UpdateCheckResult.UpToDate();
            }

            for (VersionInfo candidate : candidateVersions) {
                Optional<VersionInfo> found = findLatestVersion(candidate);
                if (found.isPresent()) {
                    return new UpdateCheckResult.Available(found.get());
                }
            }
            return new UpdateCheckResult.UpToDate();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new UpdateCheckResult.Failed(e);
        } catch (Exception e) {
            return new UpdateCheckResult.Failed(e);
        }
    }

    /**
     * tagsのJSONを処理し、新しいバージョンを抽出してソート
     *
     * @param tags tagsのリスト
     * @return 新しいバージョンのリスト（新しい順にソート済み、見つからなかった場合は空リスト）
     */
    List<VersionInfo> processTags(List<GitHubTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<VersionInfo> candidates = new ArrayList<>();

        // Githubのtagsから新しいバージョンを抽出
        for (GitHubTag tag : tags) {
            if (tag == null) {
                continue;
            }
            String tagname = tag.name();
            if (tagname == null || tagname.isEmpty()) {
                continue;
            }

            Matcher m = TAG_REGIX.matcher(tagname);
            if (!m.find()) {
                continue;
            }

            try {
                Version remote = new Version(m.group());
                if (Version.UNKNOWN.equals(remote) || versionSupplier.get().compareTo(remote) >= 0) {
                    continue;
                }
                // 候補に追加（bodyとnameは後で取得）
                candidates.add(new VersionInfo(tagname, remote, null, 0, null, null));
            } catch (IllegalArgumentException e) {
                // バージョン形式が不正な場合はスキップ
                log.debug("不正なバージョン形式: {}", tagname, e);
            }
        }

        // バージョン順にソート（新しい順）
        candidates.sort((a, b) -> b.version().compareTo(a.version()));

        return candidates;
    }

    /**
     * 指定されたtagのリリース情報を取得し、有効性を確認
     * アセット情報も同時に取得して、重複したAPI呼び出しを避ける
     * プラットフォームに応じたアセットが見つからない場合は、更新対象外として扱う
     *
     * @param versionInfo バージョン情報（tag名とバージョン、アセット情報は未設定）
     * @return 有効なバージョン情報。無効な場合は空
     */
    private Optional<VersionInfo> findLatestVersion(VersionInfo versionInfo) {
        try {
            HttpRequest request = newApiGet(RELEASES + versionInfo.tagname());
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }

            GitHubRelease release = JsonMappers.LENIENT_READER
                    .forType(GitHubRelease.class)
                    .readValue(response.body());
                    // リリース情報の有効性をチェック（無効な場合は早期リターン）
                    if (release == null
                    || release.message() != null
                    || release.draft()
                    || (!Boolean.getBoolean(USE_PRERELEASE) && release.prerelease())) {
                return Optional.empty();
            }

            List<GitHubAsset> assets = release.assets();
            if (assets == null || assets.isEmpty()) {
                return Optional.empty();
            }

            // プラットフォームに応じたアセットを取得
            String buildPlatform = SystemPlatform.getBuildPlatform();
            Optional<GitHubAsset> foundAssetOpt = findAssetForPlatform(assets, buildPlatform);

            // プラットフォームに応じたアセットが見つからない場合は更新対象外
            if (foundAssetOpt.isEmpty()) {
                log.debug("プラットフォーム {} に対応するアセットが見つかりません: {}", buildPlatform, versionInfo.tagname());
                return Optional.empty();
            }

            GitHubAsset foundAsset = foundAssetOpt.get();
            String downloadUrl = foundAsset.browserDownloadUrl();
            long fileSize = foundAsset.size();
            String assetName = foundAsset.name();
            if (downloadUrl == null || downloadUrl.isEmpty()
                    || assetName == null || assetName.isEmpty()
                    || fileSize <= 0) {
                return Optional.empty();
            }

            String body = release.body() != null ? release.body() : "";
            log.debug("リリースノートbody取得: サイズ={} bytes", body.length());

            // アセット情報とbodyを含むVersionInfoを作成
            VersionInfo versionInfoWithAsset = new VersionInfo(
                    versionInfo.tagname(),
                    versionInfo.version(),
                    downloadUrl,
                    fileSize,
                    body,
                    assetName);

            log.info("更新可能バージョンを検出：{},{}({} bytes)", versionInfo.version(), assetName, fileSize);
            return Optional.of(versionInfoWithAsset);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("リリース情報の取得が中断されました: {}", versionInfo.tagname(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.debug("リリース情報の取得に失敗: {}", versionInfo.tagname(), e);
            return Optional.empty();
        }
    }

    /**
     * GitHub API 向け GET リクエストを作成する
     *
     * @param url リクエストURL
     * @return HttpRequest
     */
    private static HttpRequest newApiGet(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(API_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
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
     * @param versionInfo バージョン情報
     * @param stage 親ウィンドウ
     * @param showSettingsHint 設定から自動チェックを無効化できる旨を表示するか
     */
    private void openInfo(VersionInfo versionInfo, Stage stage, boolean showSettingsHint) {
        Version o = versionSupplier.get();
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
        String autoUpdateInfo = showSettingsHint
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
            
            // アセット名を取得（VersionInfoから直接取得）
            // hasAsset()でnameが設定されていることを確認済みのため、nullチェックは不要
            String assetFileName = versionInfo.name();
            zipFile = updateDir.resolve(assetFileName);
            log.info("ダウンロード中: {} (ファイル名: {}, 期待されるサイズ: {} bytes)", 
                versionInfo.downloadUrl(), assetFileName, versionInfo.fileSize());
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
        ProgressCallback progressCallback = createProgressCallback(progressBar, statusLabel);

        ThreadManager.getExecutorService().execute(() -> {
            try {
                downloadWithProgressAndRetry(
                        versionInfo.downloadUrl(), zipFile, versionInfo.fileSize(), progressCallback);

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
                unzip(zipFile, tempDir);

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

                deleteDirectory(tempDir);
                Files.delete(zipFile);
                saveUpdateInfo(updateDir.resolve("update.json"), newVersion);

                log.info("更新準備完了");
                Platform.runLater(() -> {
                    progressDialog.close();
                    showUpdateReadyDialog(newVersion, rootDir, stage);
                });
            } catch (Exception e) {
                log.warn("自動更新の準備に失敗しました", e);
                Platform.runLater(() -> {
                    progressDialog.close();
                    showUpdateErrorDialog(e, stage);
                });
            }
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
     * 進捗表示付きダウンロード（リトライ機能付き）
     *
     * @param url ダウンロードURL
     * @param destination 保存先
     * @param expectedSize 期待されるファイルサイズ
     * @param progressCallback 進捗更新コールバック
     * @return ダウンロード完了時の保存先パス
     */
    private Path downloadWithProgressAndRetry(String url, Path destination,
            long expectedSize,
            ProgressCallback progressCallback) throws IOException, InterruptedException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= DOWNLOAD_MAX_RETRIES; attempt++) {
            if (attempt > 1) {
                Thread.sleep(1000);
            }
            log.info("ダウンロード試行 {} / {}", attempt, DOWNLOAD_MAX_RETRIES);
            try {
                Path path = downloadWithProgress(url, destination, expectedSize, progressCallback);
                long actualSize = Files.size(path);
                if (actualSize == expectedSize) {
                    log.info("ダウンロード完了: {} bytes (サイズ検証OK)", actualSize);
                    return path;
                }
                log.warn("ファイルサイズ不一致: 期待値={}, 実際={}", expectedSize, actualSize);
                lastError = new IOException("ファイルサイズ不一致: 期待値=" + expectedSize + ", 実際=" + actualSize);
            } catch (IOException e) {
                lastError = e;
                log.warn("ダウンロード失敗（試行 {} / {}）: {}", attempt, DOWNLOAD_MAX_RETRIES, e.getMessage());
            }
        }
        throw lastError != null
                ? lastError
                : new IOException("ダウンロードに失敗しました（最大試行回数: " + DOWNLOAD_MAX_RETRIES + "）");
    }

    /**
     * 進捗表示付きダウンロード（1回の試行）
     *
     * @param url ダウンロードURL
     * @param destination 保存先
     * @param expectedSize 期待されるファイルサイズ
     * @param progressCallback 進捗更新コールバック
     * @return ダウンロード完了時の保存先パス
     */
    private Path downloadWithProgress(String url, Path destination,
            long expectedSize,
            ProgressCallback progressCallback) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + response.statusCode() + ": " + url);
        }

        long fileSize = expectedSize > 0
                ? expectedSize
                : response.headers().firstValueAsLong("Content-Length").orElse(0L);
        if (progressCallback != null && fileSize > 0) {
            progressCallback.update(0, fileSize);
        }

        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        long downloaded = 0;
        long lastUpdateTime = 0;
        try (InputStream in = response.body();
                OutputStream out = Files.newOutputStream(destination)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                downloaded += n;
                if (progressCallback != null && fileSize > 0 && n > 0) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL_MS || downloaded >= fileSize) {
                        progressCallback.update(downloaded, fileSize);
                        lastUpdateTime = currentTime;
                    }
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(destination);
            throw e;
        }

        try {
            final long mbDivisor = 1024 * 1024;
            long downloadedSize = Files.size(destination);
            log.info("ダウンロード完了: {} MB", downloadedSize / mbDivisor);
        } catch (IOException e) {
            log.debug("ファイルサイズの取得に失敗しました（ログ出力のみ）", e);
        }
        return destination;
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
        ObjectNode root = JsonMappers.MAPPER.createObjectNode();
        root.put("version", version.toBaseString());
        root.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        JsonMappers.MAPPER.writeValue(file, root);
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
                        "現在のバージョン: " + versionSupplier.get() + "\n" +
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
     * @param assets アセットのリスト
     * @param buildPlatform ビルドプラットフォーム（win, mac, mac-aarch64 など）
     * @return 見つかったアセットのOptional、見つからない場合は空のOptional
     */
    Optional<GitHubAsset> findAssetForPlatform(List<GitHubAsset> assets, String buildPlatform) {
        if (assets == null || assets.isEmpty()) {
            return Optional.empty();
        }

        List<String> prefixes = getAssetPrefixes(buildPlatform);

        return prefixes.stream()
                .flatMap(prefix -> assets.stream()
                        .filter(asset -> {
                            String name = asset.name() != null ? asset.name() : "";
                            return name.startsWith(prefix) && name.endsWith(".zip");
                        })
                        .findFirst()
                        .stream())
                .findFirst();
    }
    
    /**
     * プラットフォームに応じたアセット名のプレフィックスリストを取得（優先順位順）
     * 
     * <p>ビルド時のプラットフォーム情報（win, mac, mac-aarch64, linux）に基づいて、
     * 対応するアセットファイル名のプレフィックスを返します。</p>
     * 
     * <p>ファイル名形式: logbook-{platform}.zip</p>
     * 
     * <p>不明なプラットフォームの場合は空のリストを返し、
     * これによりfindAssetForPlatformは空のOptionalを返します（更新対象外）。</p>
     * 
     * @param buildPlatform ビルドプラットフォーム（win, mac, mac-aarch64, linux）
     * @return プレフィックスリスト（優先順位順）。不明なプラットフォームの場合は空リスト
     */
    public List<String> getAssetPrefixes(String buildPlatform) {
        return switch (buildPlatform) {
        case "win" -> List.of("logbook-win.zip");
        case "mac" -> List.of("logbook-mac.zip");
        case "mac-aarch64" -> List.of("logbook-mac-aarch64.zip");
        case "linux" -> List.of("logbook-linux.zip", "logbook-kai-linux_", "logbook-kai-ubuntu_");
        default -> List.of(); // 不明なプラットフォームの場合は空リスト（更新対象外）
        };
    }
}
