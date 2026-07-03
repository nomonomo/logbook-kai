# 開発・デバッグ向け設定

配布 ZIP には含まれない、開発・デバッグ用の設定サンプルです。

| パス | 内容 |
|------|------|
| [jmx_exporter/jmx-exporter-config.yaml](jmx_exporter/jmx-exporter-config.yaml) | Prometheus JMX Exporter 設定 |
| [logback/logback.xml](logback/logback.xml) | アクセスログ出力用 logback 設定サンプル |

---

## カスタム logback の適用

標準の `logbook/src/main/resources/logback.xml` の代わりに、このディレクトリの設定を使う場合は起動時に `-Dlogback.configurationFile` を指定します。

```
logbook\bin\java -Dlogback.configurationFile=D:\path\to\logbook-kai\dev\logback\logback.xml -m logbook
```

ログファイルは作業ディレクトリ（通常はアプリのインストールフォルダ）配下の `logs/` に出力されます。

本サンプルは本番 `logback.xml` をベースに、**プロキシアクセスログ**（テキスト／JSON）の appender のみを追加した最小構成です。Jetty や個別パッケージ向けのロガー設定は含みません。プロキシ本体の DEBUG ログが必要な場合は、例えば `<logger name="logbook.internal.proxy" level="DEBUG" />` を追記してください。

### LogstashEncoder（JSON 出力）について

`dev/logback/logback.xml` の `AccessLogJson` appender は [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder) を使用します。本番 JAR には同梱していないため、JSON 出力を使う場合は開発環境で encoder JAR を **class path**（`-cp` / `--class-path`）に追加してください。`-m logbook` で起動する場合も module path ではなく class path に載せます。

例（Maven ローカルリポジトリから取得した場合）:

```
logbook\bin\java ^
  -cp C:\path\to\logstash-logback-encoder-8.0.jar ^
  -Dlogback.configurationFile=D:\path\to\dev\logback\logback.xml ^
  -m logbook
```

---

## Prometheus JMX Exporter（実行状態の取得）

アプリ起動時に `LogbookBuildInfo` / `LogbookMetrics` MXBean を JMX に登録します。Prometheus 形式で取得するには、**別途取得した** [JMX Exporter](https://github.com/prometheus/jmx_exporter) エージェントを javaagent として指定してください（配布 ZIP には同梱しません）。

設定ファイルのサンプル: [jmx_exporter/jmx-exporter-config.yaml](jmx_exporter/jmx-exporter-config.yaml)

**必要バージョン: JMX Exporter 1.2.0 以降。** 本設定は `metricCustomizers`（MBean 属性をラベル化）を使用しており、この機能は [1.2.0（2025-03-10）](https://github.com/prometheus/jmx_exporter/releases/tag/1.2.0) で追加されました。1.2.0 未満では設定ファイルを読み込めません。

起動例（ポート `9404` で `/metrics` を公開）:

```
logbook\bin\java -Dlogbook.dev=true -javaagent:C:\path\to\jmx_prometheus_javaagent-1.6.0.jar=9404:C:\path\to\dev\jmx_exporter\jmx-exporter-config.yaml -m logbook
```

設定を更新したらアプリを再起動してください。

確認コマンド（PowerShell）:

```powershell
curl.exe -s http://localhost:9404/metrics | Select-String "^logbook_"
```

期待される出力例（JMX Exporter 1.6.0）:

```
logbook_build{buildtimestamp="2026-07-04T06:10:36Z",version="26.6.3"} 1.0
logbook_listen_port 8888.0
logbook_plugin_count 1.0
logbook_server_running 1.0
logbook_uptime_seconds 3218.0
```

JMX Exporter 1.6.x では OpenMetrics 命名規則により、設定ファイル上の `logbook_build_info` は **`logbook_build`** として出力されます（`_info` サフィックスが除去される）。1.6 未満では `logbook_build_info` のまま出力される場合があります。

### 登録される MXBean

| ObjectName | 説明 |
|------------|------|
| `logbook:type=BuildInfo` | ビルド識別（version / buildTimestamp / devMode 等） |
| `logbook:type=ApplicationMetrics` | 実行中状態 |

### 出力される主なメトリクス

| メトリクス | 説明 |
|-----------|------|
| `logbook_build`（1.6.x。設定上は `logbook_build_info`） | バージョン・ビルド日時（info 系、1 系列） |
| `logbook_uptime_seconds` | 起動からの経過秒数 |
| `logbook_listen_port` | リッスンポート（AppConfig） |
| `logbook_server_running` | プロキシサーバー稼働状態（1/0） |
| `logbook_plugin_count` | 読み込み済みプラグイン数 |
| `jvm_*` | エージェント組み込みの JVM メトリクス |

---

## プロキシ アクセスログ

HTTPS トンネル（CONNECT）内の実際の HTTP リクエスト／レスポンスを記録します。実装は `ProxyAccessLogger` が MDC に項目を設定し、専用ロガー `logbook.internal.proxy.AccessLog` へ DEBUG 出力します。

### 有効化

`dev/logback/logback.xml` では次のロガーで制御します。

```xml
<logger name="logbook.internal.proxy.AccessLog" level="DEBUG" additivity="false">
    <appender-ref ref="AccessLog" />
</logger>
```

- `level="DEBUG"` … 有効
- `level="OFF"` … 無効

### 出力形式の選び方

Java 側は MDC キーに値を入れ、メッセージは固定文字列 `"proxy access"` のみです。**テキスト／JSON の切り替えは logback.xml の appender 設定で行います。**

#### JSON 形式（LogstashEncoder 推奨）

MDC キーを JSON フィールドとして自動展開するには、`LogstashEncoder` で `includeMdc=true` を指定します。サンプルは `AccessLogJson` appender です。

```xml
<appender name="AccessLogJson" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/access-json.log</file>
    ...
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeCallerData>false</includeCallerData>
        <includeContext>false</includeContext>
        <includeMdc>true</includeMdc>
    </encoder>
</appender>
```

ロガー側で appender を差し替えます。

```xml
<logger name="logbook.internal.proxy.AccessLog" level="DEBUG" additivity="false">
    <appender-ref ref="AccessLogJson" />
</logger>
```

1 行 1 JSON となり、Grafana Loki / Alloy 等への取り込みに適しています。

#### テキスト形式（pattern で MDC を自前記載）

テキストで出力する場合、**pattern に `%X{キー名}` で項目を列挙する必要があります**。MDC は自動では行に展開されません。

サンプル（`AccessLog` appender）:

```xml
<encoder>
    <pattern>%date{ISO8601} clientAddr=%X{clientAddr} method=%X{method} uriPath=%X{uriPath} status=%X{status} elapsedMs=%X{elapsedMs} outcome=%X{outcome}%n</pattern>
</encoder>
```

必要な項目だけを選んで並べてください。

### MDC キー一覧

`ProxyAccessLogger` が設定するキーです。JSON 出力時は LogstashEncoder がそのままフィールド名になります。

| MDC キー | 内容 |
|----------|------|
| `clientAddr` | 接続元 IP |
| `clientPort` | 接続元ポート |
| `method` | HTTP メソッド |
| `uri` | リクエスト URI（クエリ含む） |
| `uriPath` | パスのみ（集計用、クエリ除外） |
| `host` | Host ヘッダー |
| `status` | HTTP ステータス（未受信時は 0） |
| `requestSize` | リクエストボディサイズ（バイト） |
| `responseSize` | レスポンスボディサイズ（圧縮後、バイト） |
| `contentLength` | Content-Length（未設定時 -1） |
| `contentEncoding` | Content-Encoding |
| `contentType` | Content-Type |
| `transferEncoding` | Transfer-Encoding |
| `httpVersion` | HTTP バージョン（例: HTTP/1.1） |
| `elapsedMs` | 処理時間（ミリ秒） |
| `upstreamLatencyMs` | アップストリーム TTFB 相当（ミリ秒、計測不可時 -1） |
| `upstreamBodyMs` | レスポンスボディ受信時間（ミリ秒、計測不可時 -1） |
| `proxyOverheadMs` | プロキシ側オーバーヘッド（ミリ秒、計測不可時 -1） |
| `outcome` | 結果（`COMPLETE` / `CLIENT_DISCONNECT` / `UPSTREAM_DISCONNECT` 等） |
| `errorDetail` | エラー詳細（正常時は空文字） |

`outcome` の取りうる値は `ProxyAccessLogger.Outcome` 列挙型を参照してください。

---

## 関連ドキュメント

- ビルド手順: [how-to-build.md](../how-to-build.md)
- 開発モード（`--dev` / `-Dlogbook.dev=true`）: [how-to-build.md](../how-to-build.md) の「実行時オプション」
