@echo off
setlocal enabledelayedexpansion

echo ======================================
echo CA�ؖ����ƃT�[�o�[�ؖ����쐬�X�N���v�g
echo ======================================
echo.

rem ============================================
rem Step 1: CA�ؖ������쐬
rem ============================================
echo �yStep 1/6�z CA�ؖ������쐬��...

if exist logbook-ca.p12 (
    echo �yINFO�z ������CA�ؖ������폜���܂�...
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
    echo �yERROR�z CA�ؖ����̍쐬�Ɏ��s���܂����B
    pause
    exit /b 1
)

echo �ySUCCESS�z CA�ؖ������쐬���܂���: logbook-ca.p12
echo.

rem ============================================
rem Step 2: CA�ؖ������G�N�X�|�[�g
rem ============================================
echo �yStep 2/6�z CA�ؖ������G�N�X�|�[�g��...

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
    echo �yERROR�z CA�ؖ����̃G�N�X�|�[�g�Ɏ��s���܂����B
    pause
    exit /b 1
)

echo �ySUCCESS�z CA�ؖ������G�N�X�|�[�g���܂���: logbook-ca.crt
echo �yINFO�z ���̃t�@�C�����u���E�U�ɃC���|�[�g���Ă��������B
echo.

rem ============================================
rem Step 3: �T�[�o�[�ؖ����̔閧�����쐬
rem ============================================
echo �yStep 3/6�z �T�[�o�[�ؖ������쐬��...

if exist kancolle.p12 (
    echo �yINFO�z �����̃T�[�o�[�ؖ������폜���܂�...
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
    echo �yERROR�z �T�[�o�[�ؖ����̍쐬�Ɏ��s���܂����B
    pause
    exit /b 1
)

echo �ySUCCESS�z �T�[�o�[�ؖ����̌��y�A���쐬���܂����B
echo.

rem ============================================
rem Step 4: �ؖ��������v��(CSR)���쐬
rem ============================================
echo �yStep 4/6�z �ؖ��������v��(CSR)���쐬��...

if exist kancolle.csr (
    del /F /Q kancolle.csr 2>nul
)

keytool -certreq ^
  -alias kancolle-cert ^
  -keystore kancolle.p12 ^
  -storepass changeit ^
  -file kancolle.csr

if errorlevel 1 (
    echo �yERROR�z CSR�̍쐬�Ɏ��s���܂����B
    pause
    exit /b 1
)

echo �ySUCCESS�z CSR���쐬���܂���: kancolle.csr
echo.

rem ============================================
rem Step 5: CA�ŃT�[�o�[�ؖ����ɏ���
rem ============================================
echo �yStep 5/6�z CA�ŃT�[�o�[�ؖ����ɏ�����...

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
    echo �yERROR�z �ؖ����̏����Ɏ��s���܂����B
    pause
    exit /b 1
)

echo �ySUCCESS�z �ؖ����ɏ������܂���: kancolle-signed.crt
echo.

rem ============================================
rem Step 6: �ؖ����`�F�[����keystore�ɃC���|�[�g
rem ============================================
echo �yStep 6/6�z �ؖ����`�F�[�����C���|�[�g��...

rem �܂�CA�ؖ������C���|�[�g
keytool -importcert ^
  -alias logbook-ca ^
  -keystore kancolle.p12 ^
  -storepass changeit ^
  -file logbook-ca.crt ^
  -noprompt

if errorlevel 1 (
    echo �yERROR�z CA�ؖ����̃C���|�[�g�Ɏ��s���܂����B
    pause
    exit /b 1
)

rem ���ɏ������ꂽ�ؖ������C���|�[�g
keytool -importcert ^
  -alias kancolle-cert ^
  -keystore kancolle.p12 ^
  -storepass changeit ^
  -file kancolle-signed.crt

if errorlevel 1 (
    echo �yERROR�z �������ꂽ�ؖ����̃C���|�[�g�Ɏ��s���܂����B
    pause
    exit /b 1
)

echo �ySUCCESS�z �ؖ����`�F�[�����C���|�[�g���܂����B
echo.
rem ============================================
rem ����
rem ============================================
echo ======================================
echo �����I
echo ======================================
echo.
echo �쐬���ꂽ�ؖ���:
echo   - logbook-ca.crt : �u���E�U�ɃC���X�g�[������CA�ؖ���
echo   - kancolle.p12   : �T�[�o�[���g�p����ؖ���
echo.
echo �y�d�v�z �u���E�U�� logbook-ca.crt ���C���X�g�[�����Ă�������:
echo   1. logbook-ca.crt ���_�u���N���b�N
echo   2. �ؖ����̃C���X�g�[�� ���N���b�N
echo   3. �M�����ꂽ���[�g�ؖ��@�� ��I��
echo   4. ���� -^> ���� �̏��ɃN���b�N
echo.

pause