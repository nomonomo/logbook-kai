SETLOCAL

cd /d %~dp0

REM ======================================
REM 自動更新チェック
REM ======================================
if exist "update\logbook" (
    if exist "update_apply.bat" (
        echo 更新を適用します...
        call update_apply.bat
        if %ERRORLEVEL% NEQ 0 (
            echo 更新の適用に失敗しました。
            pause
            exit /b %ERRORLEVEL%
        )
        REM 更新成功後、そのまま起動に進む
    )
)

REM ======================================
REM 推奨設定: G1 GC + メモリ最適化
REM ======================================

REM カスタム設定
SET CUSTOM_OPT=-Xms256M -Xmx2G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=2M -XX:+UseStringDeduplication

REM カスタム設定を無効にする場合は以下をコメント
SET JVM_OPT=%CUSTOM_OPT%
chcp 65001
%~dp0\logbook\bin\javaw %JVM_OPT% -m logbook
