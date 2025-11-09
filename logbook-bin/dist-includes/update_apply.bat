@echo off
setlocal enabledelayedexpansion

cd /d %~dp0

REM ログディレクトリの作成
if not exist "logs" (
    mkdir "logs" >nul 2>&1
)

REM ログファイルのパス
set "LOG_FILE=logs\update.log"

REM ログファイルにタイムスタンプを記録
echo ======================================== >> "%LOG_FILE%"
echo 更新処理開始: %date% %time% >> "%LOG_FILE%"
echo ======================================== >> "%LOG_FILE%"
echo. >> "%LOG_FILE%"

REM コンソールとログファイルの両方に出力する関数
goto :main

:logecho
if "%*"=="." (
    echo. >> "%LOG_FILE%"
    echo.
) else (
    echo %* >> "%LOG_FILE%"
    echo %*
)
goto :eof

:main

call :logecho ========================================
call :logecho 航海日誌 自動更新適用
call :logecho ========================================
call :logecho.

REM 更新ファイル確認
if not exist "update\logbook" (
    call :logecho エラー: 更新ファイルが見つかりません。
    call :logecho.
    timeout /t 3 /nobreak >nul
    echo 更新処理終了（エラー）: %date% %time% >> "%LOG_FILE%"
    exit /b 1
)

REM [1/4] 旧バックアップの削除
call :logecho [1/4] 旧バックアップを削除中...
if exist "logbook_old" (
    rmdir /s /q "logbook_old" 2>> "%LOG_FILE%"
    if exist "logbook_old" (
        call :logecho   警告: 旧バックアップの削除に失敗しました。続行します。
    )
)
call :logecho   完了
call :logecho.

REM [2/4] バックアップ作成
call :logecho [2/4] バックアップ作成中...
move "logbook" "logbook_old" >> "%LOG_FILE%" 2>&1

if %ERRORLEVEL% NEQ 0 (
    call :logecho エラー: バックアップの作成に失敗しました。
    call :logecho logbookディレクトリが使用中の可能性があります。
    call :logecho.
    echo エラーコード: %ERRORLEVEL% >> "%LOG_FILE%"
    timeout /t 5 /nobreak >nul
    echo 更新処理終了（エラー）: %date% %time% >> "%LOG_FILE%"
    exit /b 1
)
call :logecho   完了
call :logecho.

REM [3/4] 新バージョンを配置
call :logecho [3/4] 新バージョンを配置中...
move "update\logbook" "logbook" >> "%LOG_FILE%" 2>&1

if %ERRORLEVEL% NEQ 0 (
    call :logecho エラー: 新バージョンの配置に失敗しました。
    call :logecho.
    echo エラーコード: %ERRORLEVEL% >> "%LOG_FILE%"
    call :logecho ロールバック中...
    move "logbook_old" "logbook" >> "%LOG_FILE%" 2>&1
    
    if %ERRORLEVEL% EQU 0 (
        call :logecho ロールバック成功
    ) else (
        call :logecho 致命的エラー: ロールバックに失敗しました。
        call :logecho logbook_old フォルダを手動で logbook にリネームしてください。
        echo ロールバックエラーコード: %ERRORLEVEL% >> "%LOG_FILE%"
    )
    call :logecho.
    timeout /t 5 /nobreak >nul
    echo 更新処理終了（エラー）: %date% %time% >> "%LOG_FILE%"
    exit /b 1
)
call :logecho   完了
call :logecho.

REM 更新情報を読み込み
set NEW_VERSION=不明
if exist "update\update.json" (
    for /f "tokens=2 delims=:," %%a in ('type "update\update.json" ^| findstr "version"') do (
        set NEW_VERSION=%%~a
        set NEW_VERSION=!NEW_VERSION: =!
        set NEW_VERSION=!NEW_VERSION:"=!
    )
)
echo 更新バージョン: !NEW_VERSION! >> "%LOG_FILE%"

REM [4/4] クリーンアップ
call :logecho [4/4] クリーンアップ中...
rmdir /s /q "update" 2>> "%LOG_FILE%"
call :logecho   完了
call :logecho.

REM 更新完了マーカー
echo !NEW_VERSION! > ".update_completed"
echo 更新完了マーカー作成: .update_completed >> "%LOG_FILE%"

call :logecho ========================================
call :logecho 更新が完了しました！
call :logecho バージョン: !NEW_VERSION!
call :logecho ========================================
call :logecho.
call :logecho バックアップ: logbook_old\
call :logecho 問題がなければ後で削除してください。
call :logecho.

echo 更新処理終了（成功）: %date% %time% >> "%LOG_FILE%"
echo ======================================== >> "%LOG_FILE%"
echo. >> "%LOG_FILE%"

exit /b 0
