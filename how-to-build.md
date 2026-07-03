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

`-Pdev` を付けてビルドすると、開発向けテスト（`test.profile=dev`）が有効になります。

```
mvn -Pdev package
```

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
