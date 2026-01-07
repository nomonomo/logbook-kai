#!/bin/bash

# スクリプトのディレクトリに移動
cd "$(dirname "$0")"

# ======================================
# 自動更新チェック
# ======================================
if [ -d "update/logbook" ]; then
    if [ -f "update_apply.sh" ]; then
        echo "更新を適用します..."
        bash update_apply.sh
        if [ $? -ne 0 ]; then
            echo "更新の適用に失敗しました。"
            read -p "Enterキーを押して終了してください..."
            exit $?
        fi
        # 更新後、そのまま起動に進む
    fi
fi

# ======================================
# JVM設定: G1 GC + メモリ最適化
# ======================================

# JVMオプション
CUSTOM_OPT="-Xms256M -Xmx2G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=2M -XX:+UseStringDeduplication"

# JVMオプションを変更する場合は以下をコメントアウト
JVM_OPT="$CUSTOM_OPT"

# Javaアプリケーションの起動
"$(dirname "$0")/logbook/bin/java" $JVM_OPT -m logbook

