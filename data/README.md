# ゲームデータ（data/）

マップ・任務条件・識別札など、アプリ本体の再リリースなしで更新可能なデータを置きます。

## 構成

| パス | 内容 |
|------|------|
| `manifest.json` | データ版・配信ファイルの SHA-256 |
| `map/mapping.json` | セル番号 → マス記号 |
| `seaarea/seaarea.json` | イベント識別札 |
| `quest/src/{questNo}.json` | 任務条件（**編集用**） |
| `quest/quests.json` | 任務条件バンドル（**配信用・生成物**。空白なしで出力） |

生成・検証ツールはリポジトリルートの Maven モジュール [`logbook-data`](../logbook-data/) です。

## 配信対象（manifest / raw / JAR 同梱）

- `map/mapping.json`
- `seaarea/seaarea.json`
- `quest/quests.json`
- `manifest.json`

`quest/src/` は編集用のため配信・JAR 同梱しません。

## Maven（親プロジェクトから）

```bash
# 通常アプリビルド（quests.json は必ず再生成。manifest は更新しない）
mvn -pl logbook,logbook-bin -am package

# data 更新（quests 再生成 + manifest の sha / version 更新 + 正当化テスト）
mvn -pl logbook-data,logbook -am verify -Pgamedata-update
```

`-Pgamedata-update` は次をまとめて有効にします。

- pack に `--update-sha`（配信ファイルの `sha256` 再計算。中身が変わっていれば `version` を自動 +1。同じなら manifest は触らない）
- `GameDataTool verify`
- `logbook` の Surefire で `groups=gamedata`（正当化テストのみ）

| 対象 | 版の例 | 意味 |
|------|--------|------|
| アプリ本体 | `26.7.2` | カレンダー版（年月 + 月内連番） |
| データ `version` | `1`, `2`, `3` … | 配信内容が変わるたびに +1（ツールが自動） |
| `formatVersion` | `1` | スキーマ破壊時のみ手動で加算 |

## 更新手順

1. 任務なら `quest/src/{questNo}.json` を編集（マップ・識別札は各 JSON を直接編集）
2. 上記の data 更新コマンドを実行（`version` の手上げは不要）
3. 変更された `quest/quests.json` / `manifest.json` などをコミット
4. `master` へ push（`data/**` のみの変更ではアプリのフルビルドは走らない）

CI（Data Validate）は同じ更新コマンド相当のあと、`data/` に未コミット差分がないことを要求します。ローカルでコマンドを忘れたり結果をコミットし忘れたりすると失敗します。

## ランタイム

- 「ゲームデータ更新チェック」有効時、起動時・設定画面、および前回チェックから 12 時間経過後に GitHub 上の `data/` を確認し、新しければ `config/gamedata/` へダウンロード
- ローカル外部と同梱のマニフェスト版を比較し、新しい方を読み込み（同版なら外部）
- 任務は `quest/quests.json` を読み込みます
