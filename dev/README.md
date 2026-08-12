# 開発・デバッグ向け設定

配布 ZIP には含まれない、開発・デバッグ用の設定サンプルです。

| パス | 内容 |
|------|------|
| [jmx_exporter/jmx-exporter-config.yaml](jmx_exporter/jmx-exporter-config.yaml) | Prometheus JMX Exporter 設定 |
| [logback/logback.xml](logback/logback.xml) | アクセスログ・コンテンツリスナー・API スキーマ検知用 logback 設定サンプル |
| [api-capture-rules.properties](api-capture-rules.properties) | API キャプチャ対象（`mvn -Pdev` で同梱。配布は空） |
| [image-listener.properties](image-listener.properties) | ImageListener の img category（`mvn -Pdev` で同梱） |

---

## カスタム logback の適用

標準の `logbook/src/main/resources/logback.xml` の代わりに、このディレクトリの設定を使う場合は起動時に `-Dlogback.configurationFile` を指定します。

```
logbook\bin\java -Dlogback.configurationFile=D:\path\to\logbook-kai\dev\logback\logback.xml -m logbook
```

ログファイルは作業ディレクトリ（通常はアプリのインストールフォルダ）配下の `logs/` に出力されます。

本サンプルは本番 `logback.xml` をベースに、**プロキシアクセスログ**・**コンテンツリスナー処理ログ**・**API スキーマ検知ログ**（テキスト／JSON）の appender を追加した構成です。Jetty や個別パッケージ向けのロガー設定は含みません。プロキシ本体の DEBUG ログが必要な場合は、例えば `<logger name="logbook.internal.proxy" level="DEBUG" />` を追記してください。

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
logbook_startup_jvm_to_launcher_millis 2500.0
logbook_startup_jvm_to_ui_ready_millis 18000.0
logbook_startup_jvm_to_window_shown_millis 9000.0
logbook_startup_to_ui_ready_millis 15500.0
logbook_startup_to_window_shown_millis 6500.0
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
| `logbook_startup_jvm_to_launcher_millis` | JVM 起動〜 `Launcher.main`（javaagent 込み） |
| `logbook_startup_to_window_shown_millis` | Launcher 先頭〜メインウィンドウ表示 |
| `logbook_startup_to_ui_ready_millis` | Launcher 先頭〜初回 UI 更新完了 |
| `logbook_startup_jvm_to_window_shown_millis` | JVM 起動〜メインウィンドウ表示 |
| `logbook_startup_jvm_to_ui_ready_millis` | JVM 起動〜初回 UI 更新完了 |
| `jvm_*` | エージェント組み込みの JVM メトリクス |

未到達の起動マイルストーンは `0`。javaagent が無い配布 ZIP ではこれらの系列は見えません。

### 起動フェーズログ（開発者・問題調査用）

配布同梱の `logback.xml` では **出力しません**。再現しない起動遅延など、調査時だけ点きます。

[dev/logback/logback.xml](logback/logback.xml) では次を設定済みです。

- `ROLLING`（`logs/app.log`）の ThresholdFilter を DEBUG
- `<logger name="logbook.internal.metrics.StartupTiming" level="DEBUG" />`

調査用に手元や観測ホストの logback へ足す断片:

```xml
<appender name="ROLLING" ...>
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
        <level>DEBUG</level>
    </filter>
</appender>
<logger name="logbook.internal.metrics.StartupTiming" level="DEBUG" />
<!-- 任意: ゲームデータ JSON の読込所要時間 -->
<logger name="logbook.internal.gamedata.GameDataLoader" level="DEBUG" />
```

`logbook` 全体は INFO のままにしてください。ROLLING のフィルタだけ下げても、他の DEBUG は出ません。

ログ例:

```
起動フェーズ完了: phase=httpClient elapsedMs=2192 totalMs=6689
起動マイルストーン: milestone=windowShown jvmMs=8870 launcherMs=4372
```

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

#### JSON 形式（LoggingEventCompositeJsonEncoder）

MDC キーと SLF4J KeyValue を JSON フィールドとして出力するには、`LoggingEventCompositeJsonEncoder` を使用します。サンプルは `AccessLogJson` appender です。数値フィールド（`elapsedMs` 等）は KeyValue で付与し、MDC からは除外して重複を防ぎます。

```xml
<encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
    <providers>
        <timestamp/>
        <version/>
        <message/>
        <loggerName/>
        <threadName/>
        <logLevel/>
        <logLevelValue/>
        <mdc>
            <excludeMdcKeyName>elapsedMs</excludeMdcKeyName>
            <!-- 他の数値キーも同様に除外 -->
        </mdc>
        <keyValuePairs/>
    </providers>
</encoder>
```

ロガー側で appender を差し替えます。

```xml
<logger name="logbook.internal.proxy.AccessLog" level="DEBUG" additivity="false">
    <appender-ref ref="AccessLogJson" />
</logger>
```

1 行 1 JSON となり、ログ集約基盤への取り込みに適しています。`elapsedMs` 等の数値は JSON 上で数値型として出力されるため、集計時にキャストは不要です。

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

`ProxyAccessLogger` が設定するキーです。テキスト出力時は MDC の文字列値を使用します。JSON 出力時は MDC キーがフィールド名になり、数値項目（`elapsedMs` 等）は KeyValue として数値型で出力されます。

| MDC キー | 内容 |
|----------|------|
| `clientAddr` | 接続元 IP |
| `clientPort` | 接続元ポート |
| `method` | HTTP メソッド |
| `uri` | リクエスト URI（クエリ含む） |
| `uriPath` | パスのみ（集計用、クエリ除外） |
| `requestId` | リクエスト相関 ID（コンテンツリスナー処理ログとの紐づけ用） |
| `host` | Host ヘッダー |
| `status` | HTTP ステータス（未受信時は 0） |
| `requestSize` | リクエストボディサイズ（バイト） |
| `responseSize` | レスポンスボディサイズ（圧縮後、バイト） |
| `contentLength` | Content-Length（未設定時 -1） |
| `contentEncoding` | Content-Encoding |
| `contentType` | Content-Type |
| `transferEncoding` | Transfer-Encoding |
| `httpVersion` | HTTP バージョン（例: HTTP/1.1） |
| `elapsedMs` | 処理時間（ミリ秒）。**ブラウザへの応答完了まで**を計測 |
| `upstreamLatencyMs` | アップストリーム TTFB 相当（ミリ秒、計測不可時 -1） |
| `upstreamBodyMs` | レスポンスボディ受信時間（ミリ秒、計測不可時 -1） |
| `proxyOverheadMs` | プロキシ側オーバーヘッド（ミリ秒、計測不可時 -1） |
| `outcome` | 結果（`COMPLETE` / `CLIENT_DISCONNECT` / `UPSTREAM_DISCONNECT` 等） |
| `errorDetail` | エラー詳細（正常時は空文字） |

`outcome` の取りうる値は `ProxyAccessLogger.Outcome` 列挙型を参照してください。

**計測範囲**: アクセスログの `elapsedMs` は、クライアント（ブラウザ）への HTTP レスポンス送信完了までを計測します。ゲーム API の JSON 解析や艦娘データ更新などの内部処理は含まれません（後述のコンテンツリスナー処理ログで計測）。

---

## コンテンツリスナー処理ログ

`ContentListenerSpi` および `APIListenerSpi` の `accept()` 処理時間を記録します。`requestId` でアクセスログと紐づけできます。

**計測範囲**: アクセスログの応答完了**後**に Virtual Thread 上で実行される内部処理を計測します。アクセスログの `elapsedMs` と足し算しても総処理時間にはなりません（非同期・並列実行のため）。

実装は `ProxyContentListenerLogger` が MDC に項目を設定し、専用ロガー `logbook.internal.proxy.ContentListenerLog` へ DEBUG 出力します。1 リクエストにつき、登録されたリスナーごとに次のようなログが出力されます。

| `layer` | `handlerClass` の例 | 役割 |
|---------|---------------------|------|
| `dispatcher` | `logbook.internal.APIListener` | JSON 復号・`APIListenerSpi` へのディスパッチ（`ContentListenerSpi` 層） |
| `handler` | `logbook.api.ApiPortPort` 等 | 各 API ハンドラーの実処理（`APIListenerSpi` 層） |

同一リクエストで `dispatcher` 1 行と `handler` 複数行が出ることがあります。`dispatcher` と各 `handler` は並列実行されるため、それぞれの `elapsedMs` を足し算しても合計処理時間にはなりません。

### 有効化

`dev/logback/logback.xml` では次のロガーで制御します。

```xml
<logger name="logbook.internal.proxy.ContentListenerLog" level="DEBUG" additivity="false">
    <appender-ref ref="ContentListenerLog" />
</logger>
```

- `level="DEBUG"` … 有効
- `level="OFF"` … 無効

JSON 形式は `ContentListenerLogJson` appender を参照してください（`AccessLogJson` と同様の `LoggingEventCompositeJsonEncoder` 設定）。

### MDC キー一覧

| MDC キー | 内容 |
|----------|------|
| `requestId` | リクエスト相関 ID（アクセスログと同一値） |
| `method` | HTTP メソッド |
| `uriPath` | パスのみ（集計用、クエリ除外） |
| `layer` | 処理層（`dispatcher` / `handler`） |
| `handlerClass` | 処理クラス名（FQCN。例: `logbook.api.ApiPortPort`） |
| `elapsedMs` | `accept()` の処理時間（ミリ秒）。JSON では数値型 |
| `outcome` | 結果（`SUCCESS` / `ERROR`） |
| `errorDetail` | エラー詳細（正常時は空文字） |

`layer` の取りうる値は `ProxyContentListenerLogger.Layer`、`outcome` の取りうる値は `ProxyContentListenerLogger.Outcome` 列挙型を参照してください。

---

## API スキーマ検知ログ

API レスポンス JSON のうち、bean / ハンドラが把握していないキーを記録します。実装は `ApiSchemaLog` が専用ロガー `logbook.internal.api.ApiSchemaLog` へ DEBUG 出力します。

一般向け（同梱 `logback.xml`）ではこのロガーに appender を付けないため出力されません。開発ホストでは `dev/logback/logback.xml` の JSON appender を使います。

### 対応範囲

| 区分 | 状態 | 内容 |
|------|------|------|
| リクエスト文脈 | 対応済み | `APIListener.createTask` が全ハンドラで `ApiSchemaLog.openRequest`（uri / requestId / handlerClass） |
| `api_start2/getData` | 対応済み | `ApiStart2` の `api_data` KNOWN + 各マスタ bean の `at` / `ignore` / `reportUnknown` |
| 母港・メンバー・日常系 | 対応済み（第1波） | 下記 bean / ハンドラ。`api_data` を選択読取する主要ハンドラは KNOWN 付き |
| 戦闘系 | 対応済み（第2波） | `BattleTypes` ネスト・各 `IBattle` 具象・`BattleResult` に `at` / `reportUnknown` |

**第1波の bean（`at` + `reportUnknown`）**: `Basic`, `Ship`, `SlotItem`, `Useitem`, `DeckPort`, `Material`, `Ndock`, `Kdock`, `MapStartNext`, `MissionResult`, `QuestList`, `Createitem`, `Mapinfo`, `MapTypes`（および既存の start2 マスタ bean）

**第1波のハンドラ `api_data` KNOWN**: `ApiStart2`, `ApiPortPort`, `ApiGetMemberRequireInfo`, `ApiGetMemberShipDeck`, `ApiGetMemberShip3`, `ApiGetMemberMapinfo`

**第2波の bean（`at` + `reportUnknown`）**: `BattleTypes`（Kouku / Stage* / Hougeki 等ネスト）、`SortieBattle` / `SortieAirbattle` / `SortieLdAirbattle` / `SortieLdShooting`、`BattleMidnightBattle` / `BattleMidnightSpMidnight`、連合系 `CombinedBattle*`、`BattleResult`（およびネスト）

戦闘レスポンスは bean の `toBattle` / `toBattleResult` に集約されているため、ハンドラ側の `KNOWN` セットは原則不要。

### 有効化

`dev/logback/logback.xml` では次のロガーで制御します。

```xml
<logger name="logbook.internal.api.ApiSchemaLog" level="DEBUG" additivity="false">
    <appender-ref ref="ApiSchemaLogJson" />
</logger>
```

- `level="DEBUG"` … 有効
- `level="OFF"` … 無効

出力先は `logs/api-schema-json.log`（`ApiSchemaLogJson` appender）です。

### 仕組み

1. `APIListener.createTask` が `ApiSchemaLog.openRequest(uriPath, requestId, handlerClass)` でリクエスト文脈を MDC に載せる（ハンドラ側で個別に open しない）
2. `JsonHelper.bind(json).at("…").set…(...).ignore(…).reportUnknown()` で、バインドも ignore もしなかったキーを報告する
3. ハンドラ直下など bind しない箇所は `JsonHelper.reportUnknownKeys(json, jsonPath, knownKeys)` を使う
4. 同一 `(uriPath, jsonPath, field)` はプロセス内で 1 回だけ報告する（重複抑制）

**既知キーの意味**: 「処理するキー」だけでなく、「未対応でよいと確認済みのキー」も含める。bind では `set` が前者、`ignore` が後者。ハンドラの `api_data` では `HANDLED_API_DATA_KEYS` と `IGNORED_API_DATA_KEYS` の和を `KNOWN_API_DATA_KEYS` として渡し、**新規追加キーだけ**がログに出る。

### MDC キー一覧

| MDC キー | 内容 |
|----------|------|
| `event` | 固定値 `api_unknown_field` |
| `uriPath` | リクエスト URI パス |
| `jsonPath` | JSON 上の位置（例: `api_data`、`api_data.api_mst_ship[]`） |
| `field` | 未知のキー名 |
| `handlerClass` | ハンドラ FQCN（例: `logbook.api.ApiStart2`） |
| `requestId` | リクエスト相関 ID（アクセスログ・キャプチャと同一） |

メッセージは固定文字列 `"api unknown field"` です。フィールドはすべて MDC 経由（JSON appender の `<mdc>` プロバイダ）で出力します。

### 他 API への追加手順（概要）

1. リクエスト文脈は `APIListener` 側で付与済み。ハンドラで `openRequest` を重ねない
2. bean の `JsonHelper.bind` に `.at("…")` と、使うキーは `.set…`、意図的未対応は `.ignore(…)`、末尾に `.reportUnknown()` を付ける
3. ハンドラ直下のオブジェクトは `reportUnknownKeys` と `KNOWN`（対応済み ∪ 意図的未対応）を用意する
4. キャプチャ突合でノイズになった既存キーは `ignore` / `IGNORED_API_DATA_KEYS` に移す（start2 と同じ手順）

`api_max_slotplus` など実装が必要な未知キーは別途対応する。

## API レスポンス記録（開発者向け）

kcsapi のレスポンス JSON を `{captureDir}/segments/{日付}.jsonl.zst` に JSONL + zstd で保存します。
`ReverseConnectHandler.invoke()` 入口の `ApiCaptureHook` が対象 URI のボディ原文を 1 回記録します。
対象 URI はプロパティ `logbook/capture/api-capture-rules.properties` で定義します。

| ビルド | ルール |
|--------|--------|
| 配布（通常の `mvn package`） | **空**（記録を ON にしてもキャプチャされない） |
| 開発（`mvn -Pdev package`） | [`dev/api-capture-rules.properties`](api-capture-rules.properties) を同梱（現行: `/kcsapi/`・`/kcs2/js/`・`/kcs2/version.json`・`/kcs2/resources/map/`） |

`--dev` / `-Dlogbook.dev=true` ではルールは切り替わりません（ビルド成果物の同梱内容が正）。
POST リクエストは `request` フィールドにボディ原文、レスポンスは `response` フィールドに解凍後原文として同梱します。
**別途インデックスファイルは持たず**、各レコードの `requestId` でアクセスログと紐づけます。

### UI 表示の有効化

通常の設定画面には表示されません。次のいずれかを指定してください。

- 環境変数: `LOGBOOK_API_CAPTURE_UI=1`
- システムプロパティ: `-Dlogbook.apiCapture.ui=true`

### devGate（任意）

リリース JAR でも UI 表示を dev モード限定にする場合:

```
-Dlogbook.apiCapture.devGate=true
```

このとき UI 表示には `-Dlogbook.dev=true` または `-Dlogbook.apiCapture.force=true` も必要です。

### 記録の有効化

1. 上記で UI を表示
2. 設定 → 通信 → 「API レスポンスを記録する」を ON（初回は同意ダイアログ）
3. 保存先を指定して OK
4. 記録中はメインウィンドウタイトルに `[API記録中]` が付きます

### 対象 URI の変更（開発者向け）

ルールは Java ではなくプロパティで管理します。

1. [`dev/api-capture-rules.properties`](api-capture-rules.properties) を編集
2. `mvn -Pdev package` で再ビルド

形式:

```properties
prefix.1=/kcsapi/
prefix.2=/kcs2/js/
prefix.3=/kcs2/version.json
prefix.4=/kcs2/resources/map/
```

プロセス内の一時追加のみ `ApiCapturePolicy.register(...)` を使えます（上書きではなく末尾追加）。

ボディはテキストを UTF-8 原文、バイナリを Base64 で可逆保存します（スキーマ v3。詳細は document の `docs/api-capture/format.md`）。
**304 Not Modified は記録しません**（ボディなし。アクセスログで確認）。
分析時に必要なパースは `read_segments.py` 等で行います。map 資産のファイル復元は document の `tools/client-assets/extract_map_assets.py`。

### ログとの突合

```powershell
# アクセスログから requestId を取得
Select-String "api_port/port" logs/access-json.log

# セグメントを展開して requestId で検索
zstd -d captures/segments/2026-07-11.jsonl.zst -c | Select-String "req-uuid-here"
```

---

## 関連ドキュメント

- ビルド手順: [how-to-build.md](../how-to-build.md)
- 開発モード（`--dev` / `-Dlogbook.dev=true`）: [how-to-build.md](../how-to-build.md) の「実行時オプション」
