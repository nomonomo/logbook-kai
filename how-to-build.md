# ビルド手順

## 通常ビルド

以下のコマンドで JAR をビルドします。

```
mvn package
```

MANIFEST.MF には次の情報が記録されます。

- **Implementation-Version**: プロジェクトのバージョン（例: `26.6.3`）
- **Build-Timestamp**: ビルド日時（UTC、ISO 8601 形式。例: `2026-02-24T06:01:23Z`）

---

## ビルドオプション（プロファイル）

### -Pdev（開発用テスト）

`-Pdev` を付けてビルドすると、Maven Surefire にシステムプロパティ `test.profile=dev` が渡され、**一部のテストだけが有効化**されます。通常の `mvn package` ではこれらのテストはスキップされます。

```
mvn -Pdev package
```

テストのみ実行する場合:

```
mvn -Pdev test
```

#### 有効化されるテスト

`@EnabledIfSystemProperty(named = "test.profile", matches = "dev")` により、次のテストメソッドが実行対象になります。

| テストクラス | メソッド | 内容 |
|-------------|---------|------|
| `ConfigJsonRoundTripTest` | `testConfigJsonRoundTrip` | `src/test/resources/logbook/config/` 配下の設定 JSON が Jackson の読み書きで内容を保つこと |
| `BattleLogsTest` | `testBattleLogJsonRoundTrip` | `src/test/resources/logbook/battlelog/` 配下の戦闘ログ JSON が読み書きで内容を保つこと |

いずれもディレクトリ内の `*.json` を走査して DynamicTest を生成します。ディレクトリは `.gitignore` を目印にクラスパスから解決します（`.gitignore` 自体はリポジトリに含まれる）。**`*.json` が 1 件もない場合はテストが失敗**します（`-Pdev` 実行時に JSON の配置漏れを検出するため）。

#### テストデータについて

上記 2 ディレクトリの `*.json` は `.gitignore` で除外されており、**リポジトリには含まれません**。開発者がローカルに実際の設定ファイルや戦闘ログ JSON を置いて検証するためのものです。

- `logbook/src/test/resources/logbook/config/` — ファイル名は Bean の完全修飾クラス名 + `.json`（例: `logbook.bean.AppConfig.json`）
- `logbook/src/test/resources/logbook/battlelog/` — 戦闘ログ JSON

`-Pdev` なしでも `BattleLogsTest.testCsvLine` など、プロファイル不要のテストは従来どおり実行されます。

---

## 実行時オプション

### 開発モード（バージョン表示にビルド日時を付与）

通常はバージョン番号のみ表示されます。開発中のビルドを識別したい場合は、次のいずれかを指定してください。

- コマンドライン引数: `--dev`（`-m logbook` の**後**でも可）
- システムプロパティ: `-Dlogbook.dev=true`（`-m logbook` の**前**に置く。後ろに書くとアプリ引数になり無効）

表示例: `26.6.3-2026-02-24T06:01:23Z`

開発・デバッグ向けの設定（JMX Exporter、アクセスログ、詳細 logback 等）は [dev/README.md](dev/README.md) を参照してください。

---

## その他

### -Pextract-sources clean generate-sources（依存ライブラリのソース展開）

`-Pextract-sources` を付けて `clean generate-sources` を実行すると、**依存ライブラリのソース JAR を展開**し、プロジェクト直下の `lib-sources` に出力します。

- **clean**: 既存の `lib-sources` のみを削除します（`target` は削除されません）。バージョンアップ時に古いソースが残らないようにするためです。
- **generate-sources**: `maven-dependency-plugin` の `unpack-dependencies` により、compile スコープの依存関係の **sources** クラシファイア付き JAR を展開します。
- **除外されるライブラリ**: JavaFX・ControlsFX・Lombok・JUnit など、プラットフォーム固有またはツール系のソースは除外されます。

```
mvn -Pextract-sources clean generate-sources
```
