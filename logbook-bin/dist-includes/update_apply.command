#!/bin/bash

# スクリプトのディレクトリに移動
cd "$(dirname "$0")"

# ログディレクトリの作成
if [ ! -d "logs" ]; then
    mkdir -p "logs" 2>/dev/null
fi

# ログファイルのパス
LOG_FILE="logs/update.log"

# ログファイルにタイムスタンプを記録
echo "========================================" >> "$LOG_FILE"
echo "更新処理開始: $(date)" >> "$LOG_FILE"
echo "========================================" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# ログとコンソールの両方に出力する関数
logecho() {
    if [ "$1" = "." ]; then
        echo "" >> "$LOG_FILE"
        echo ""
    else
        echo "$*" >> "$LOG_FILE"
        echo "$*"
    fi
}

logecho "========================================"
logecho "自動更新 更新適用"
logecho "========================================"
logecho ""

# 更新ファイル確認
if [ ! -d "update/logbook" ]; then
    logecho "エラー: 更新ファイルが見つかりません。"
    logecho ""
    sleep 3
    echo "更新処理終了（エラー）: $(date)" >> "$LOG_FILE"
    exit 1
fi

# [1/4] バックアップの削除
logecho "[1/4] バックアップを削除中..."
if [ -d "logbook_old" ]; then
    rm -rf "logbook_old" 2>> "$LOG_FILE"
    if [ -d "logbook_old" ]; then
        logecho "  警告: バックアップの削除に失敗しました。続行します。"
    fi
fi
logecho "  完了"
logecho ""

# [2/4] バックアップ作成
logecho "[2/4] バックアップ作成中..."
if [ -d "logbook" ]; then
    mv "logbook" "logbook_old" >> "$LOG_FILE" 2>&1
    if [ $? -ne 0 ]; then
        logecho "エラー: バックアップの作成に失敗しました。"
        logecho "logbookフォルダが使用中の可能性があります。"
        logecho ""
        echo "エラーコード: $?" >> "$LOG_FILE"
        sleep 5
        echo "更新処理終了（エラー）: $(date)" >> "$LOG_FILE"
        exit 1
    fi
else
    logecho "  警告: logbookフォルダが見つかりません。新規インストールとして処理します。"
fi
logecho "  完了"
logecho ""

# [3/4] 新バージョンを配置
logecho "[3/4] 新バージョンを配置中..."
mv "update/logbook" "logbook" >> "$LOG_FILE" 2>&1
if [ $? -ne 0 ]; then
    logecho "エラー: 新バージョンの配置に失敗しました。"
    logecho ""
    echo "エラーコード: $?" >> "$LOG_FILE"
    logecho "バックアップを復元中..."
    if [ -d "logbook_old" ]; then
        mv "logbook_old" "logbook" >> "$LOG_FILE" 2>&1
        if [ $? -eq 0 ]; then
            logecho "バックアップ復元完了"
        else
            logecho "致命的エラー: バックアップ復元に失敗しました。"
            logecho "logbook_old フォルダを手動で logbook にリネームしてください。"
            echo "バックアップ復元エラーコード: $?" >> "$LOG_FILE"
        fi
    fi
    logecho ""
    sleep 5
    echo "更新処理終了（エラー）: $(date)" >> "$LOG_FILE"
    exit 1
fi
logecho "  完了"
logecho ""

# 更新バージョンを読み込み
NEW_VERSION="不明"
if [ -f "update/update.json" ]; then
    NEW_VERSION=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' "update/update.json" | sed 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
fi
echo "更新バージョン: $NEW_VERSION" >> "$LOG_FILE"

# [4/4] テンポラリ削除
logecho "[4/4] テンポラリ削除中..."
rm -rf "update" 2>> "$LOG_FILE"
logecho "  完了"
logecho ""

# 更新完了マーカー
echo "$NEW_VERSION" > ".update_completed"
echo "更新完了マーカー作成: .update_completed" >> "$LOG_FILE"

logecho "========================================"
logecho "更新が完了しました！"
logecho "バージョン: $NEW_VERSION"
logecho "========================================"
logecho ""
logecho "バックアップ: logbook_old/"
logecho "問題がなければ後で削除してください。"
logecho ""

echo "更新処理終了（成功）: $(date)" >> "$LOG_FILE"
echo "========================================" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

exit 0

