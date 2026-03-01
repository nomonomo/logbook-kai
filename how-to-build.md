# ビルド手順

## 通常ビルド

以下のコマンドで JAR をビルドします。

```
mvn package
```
---

## ビルドオプション（プロファイル）

### -Pdev（開発・デプロイ用）

`-Pdev` を付けてビルドすると、JAR の Implementation-Version（バージョン情報）に**ビルド日時**が付与されます。アプリ内のバージョン表示は次の形式になります。

- **例**: `26.1.3-2026-02-24T06:01:23Z`  
  - 前半: プロジェクトのバージョン（例: 26.1.3）  
  - 後半: ビルド日時（UTC、ISO 8601 形式）

デプロイや開発中のビルドを区別したいときに利用してください。

```
mvn -Pdev package
```

### -Pextract-sources clean generate-sources（依存ライブラリのソース展開）

`-Pextract-sources` を付けて `clean generate-sources` を実行すると、**依存ライブラリのソース JAR を展開**し、プロジェクト直下の `lib-sources` に出力します。

- **clean**: 既存の `lib-sources` のみを削除します（`target` は削除されません）。バージョンアップ時に古いソースが残らないようにするためです。
- **generate-sources**: `maven-dependency-plugin` の `unpack-dependencies` により、compile スコープの依存関係の **sources** クラシファイア付き JAR を展開します。
- **除外されるライブラリ**: JavaFX・ControlsFX・Lombok・JUnit など、プラットフォーム固有またはツール系のソースは除外されます。

```
mvn -Pextract-sources clean generate-sources
```
