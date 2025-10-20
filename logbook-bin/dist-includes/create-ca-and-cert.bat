@echo off
setlocal enabledelayedexpansion

echo ======================================
echo CA証明書とサーバー証明書作成スクリプト
echo ======================================
echo.

rem ============================================
rem Step 1: CA証明書を作成
rem ============================================
echo 【Step 1/6】 CA証明書を作成中...

if exist logbook-ca.p12 (
    echo 【INFO】 既存のCA証明書を削除します...
    del /F /Q logbook-ca.p12 2>nul
)

keytool -genkeypair ^
  -alias logbook-ca ^
  -keyalg RSA ^
  -keysize 2048 ^
  -dname "CN=Logbook-Kai Root CA, OU=Development, O=Logbook, L=Tokyo, ST=Tokyo, C=JP" ^
  -validity 3650 ^
  -keystore logbook-ca.p12 ^
  -storetype PKCS12 ^
  -storepass capassword ^
  -keypass capassword ^
  -ext "bc=ca:true"

if errorlevel 1 (
    echo 【ERROR】 CA証明書の作成に失敗しました。
    pause
    exit /b 1
)

echo 【SUCCESS】 CA証明書を作成しました: logbook-ca.p12
echo.

rem ============================================
rem Step 2: CA証明書をエクスポート
rem ============================================
echo 【Step 2/6】 CA証明書をエクスポート中...

if exist logbook-ca.crt (
    del /F /Q logbook-ca.crt 2>nul
)

keytool -exportcert ^
  -alias logbook-ca ^
  -keystore logbook-ca.p12 ^
  -storepass capassword ^
  -file logbook-ca.crt ^
  -rfc

if errorlevel 1 (
    echo 【ERROR】 CA証明書のエクスポートに失敗しました。
    pause
    exit /b 1
)

echo 【SUCCESS】 CA証明書をエクスポートしました: logbook-ca.crt
echo 【INFO】 このファイルをブラウザにインポートしてください。
echo.

rem ============================================
rem Step 3: サーバー証明書の秘密鍵を作成
rem ============================================
echo 【Step 3/6】 サーバー証明書を作成中...

if exist kancolle.p12 (
    echo 【INFO】 既存のサーバー証明書を削除します...
    del /F /Q kancolle.p12 2>nul
)

keytool -genkeypair ^
  -alias kancolle-cert ^
  -keyalg RSA ^
  -keysize 2048 ^
  -dname "CN=*.kancolle-server.com, OU=Development, O=Logbook, L=Tokyo, ST=Tokyo, C=JP" ^
  -validity 3650 ^
  -keystore kancolle.p12 ^
  -storetype PKCS12 ^
  -storepass changeit ^
  -keypass changeit

if errorlevel 1 (
    echo 【ERROR】 サーバー証明書の作成に失敗しました。
    pause
    exit /b 1
)

echo 【SUCCESS】 サーバー証明書の鍵ペアを作成しました。
echo.

rem ============================================
rem Step 4: 証明書署名要求(CSR)を作成
rem ============================================
echo 【Step 4/6】 証明書署名要求(CSR)を作成中...

if exist kancolle.csr (
    del /F /Q kancolle.csr 2>nul
)

keytool -certreq ^
  -alias kancolle-cert ^
  -keystore kancolle.p12 ^
  -storepass changeit ^
  -file kancolle.csr

if errorlevel 1 (
    echo 【ERROR】 CSRの作成に失敗しました。
    pause
    exit /b 1
)

echo 【SUCCESS】 CSRを作成しました: kancolle.csr
echo.

rem ============================================
rem Step 5: CAでサーバー証明書に署名
rem ============================================
echo 【Step 5/6】 CAでサーバー証明書に署名中...

if exist kancolle-signed.crt (
    del /F /Q kancolle-signed.crt 2>nul
)

keytool -gencert ^
  -alias logbook-ca ^
  -keystore logbook-ca.p12 ^
  -storepass capassword ^
  -infile kancolle.csr ^
  -outfile kancolle-signed.crt ^
  -validity 3650 ^
  -ext "SAN=DNS:*.kancolle-server.com,DNS:kancolle-server.com" ^
  -ext "ku=digitalSignature,keyEncipherment" ^
  -ext "eku=serverAuth,clientAuth" ^
  -rfc

if errorlevel 1 (
    echo 【ERROR】 証明書の署名に失敗しました。
    pause
    exit /b 1
)

echo 【SUCCESS】 証明書に署名しました: kancolle-signed.crt
echo.

rem ============================================
rem Step 6: 証明書チェーンをkeystoreにインポート
rem ============================================
echo 【Step 6/6】 証明書チェーンをインポート中...

rem まずCA証明書をインポート
keytool -importcert ^
  -alias logbook-ca ^
  -keystore kancolle.p12 ^
  -storepass changeit ^
  -file logbook-ca.crt ^
  -noprompt

if errorlevel 1 (
    echo 【ERROR】 CA証明書のインポートに失敗しました。
    pause
    exit /b 1
)

rem 次に署名された証明書をインポート
keytool -importcert ^
  -alias kancolle-cert ^
  -keystore kancolle.p12 ^
  -storepass changeit ^
  -file kancolle-signed.crt

if errorlevel 1 (
    echo 【ERROR】 署名された証明書のインポートに失敗しました。
    pause
    exit /b 1
)

echo 【SUCCESS】 証明書チェーンをインポートしました。
echo.
rem ============================================
rem 完了
rem ============================================
echo ======================================
echo 完了！
echo ======================================
echo.
echo 作成された証明書:
echo   - logbook-ca.crt : ブラウザにインストールするCA証明書
echo   - kancolle.p12   : サーバーが使用する証明書
echo.
echo 【重要】 ブラウザに logbook-ca.crt をインストールしてください:
echo   1. logbook-ca.crt をダブルクリック
echo   2. 証明書のインストール をクリック
echo   3. 信頼されたルート証明機関 を選択
echo   4. 次へ -^> 完了 の順にクリック
echo.

pause