SETLOCAL

REM ======================================
REM 推奨設定: G1 GC + メモリ最適化
REM ======================================
REM 注: jlinkビルド時に設定されたデフォルト値を使用
REM     必要に応じてここで上書き可能

REM カスタム設定（コメントアウトされています）
REM SET CUSTOM_OPT=-Xms256M -Xmx2G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=2M -XX:+UseStringDeduplication

REM デフォルト設定を使用（jlinkビルド時に設定済み）
SET JVM_OPT=

REM カスタム設定を有効にする場合は以下をアンコメント
REM SET JVM_OPT=%CUSTOM_OPT%

%~dp0\logbook\bin\javaw %JVM_OPT% -m logbook
