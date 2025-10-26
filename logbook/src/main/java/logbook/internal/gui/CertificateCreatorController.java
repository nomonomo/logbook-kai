package logbook.internal.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import logbook.internal.gui.CertificateService.CertificateInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 証明書作成コントローラー
 */
@Slf4j
public class CertificateCreatorController extends WindowController {

    /** 証明書サービス */
    private final CertificateService certificateService = new CertificateService();

    /** 既存のルート証明書を使用する */
    @FXML
    private CheckBox useRootCertificate;

    /** ルート証明書ファイルパス */
    @FXML
    private TextField rootCertificatePath;

    /** ルート証明書ファイル参照ボタン */
    @FXML
    private Button rootCertificatePathRef;

    /** ルート証明書内容確認ボタン */
    @FXML
    private Button rootCertificateVerify;

    /** ルート証明書秘密鍵ファイルパス */
    @FXML
    private TextField rootCertificateKeyPath;

    /** ルート証明書秘密鍵ファイル参照ボタン */
    @FXML
    private Button rootCertificateKeyPathRef;

    /** ルート証明書秘密鍵パスワード */
    @FXML
    private TextField rootCertificatePassword;

    /** Common Name (CN) */
    @FXML
    private TextField commonName;

    /** Organization (O) */
    @FXML
    private TextField organization;

    /** 出力先ディレクトリパス */
    @FXML
    private TextField outputDirectory;

    /** 出力先ディレクトリ参照ボタン */
    @FXML
    private Button outputDirRef;

    /** サーバー証明書パスワード */
    @FXML
    private TextField serverCertPassword;

    /** CA証明書パスワード */
    @FXML
    private TextField caCertPassword;

    @FXML
    void initialize() {
        // ルート証明書チェックボックスのリスナー設定
        this.useRootCertificate.selectedProperty().addListener((obs, oldVal, newVal) -> {
            this.rootCertificatePath.setDisable(!newVal);
            this.rootCertificatePathRef.setDisable(!newVal);
            this.rootCertificateVerify.setDisable(!newVal);
            this.rootCertificateKeyPath.setDisable(!newVal);
            this.rootCertificateKeyPathRef.setDisable(!newVal);
            this.rootCertificatePassword.setDisable(!newVal);
        });
        
        // 出力先ディレクトリのデフォルト値を設定
        this.outputDirectory.setText(new File("").getAbsolutePath());
    }

    /**
     * ルート証明書ファイル選択
     */
    @FXML
    void selectRootCertificate(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("ルート証明書ファイルの選択");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("証明書ファイル (*.crt, *.pem)", "*.crt", "*.pem"));
        
        // デフォルトフォルダは起動時ディレクトリ
        String current = this.rootCertificatePath.getText();
        if (current != null && !current.isEmpty()) {
            Path path = Paths.get(current);
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                fc.setInitialDirectory(parent.toFile());
            }
        } else {
            fc.setInitialDirectory(new File("").getAbsoluteFile());
        }
        
        File selected = fc.showOpenDialog(this.getWindow());
        if (selected != null && selected.exists()) {
            this.rootCertificatePath.setText(selected.getAbsolutePath());
        }
    }

    /**
     * ルート証明書秘密鍵ファイル選択
     */
    @FXML
    void selectRootCertificateKey(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("ルート証明書秘密鍵ファイルの選択");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("秘密鍵ファイル (*.key, *.pem)", "*.key", "*.pem"));
        
        // デフォルトフォルダは起動時ディレクトリ
        String current = this.rootCertificateKeyPath.getText();
        if (current != null && !current.isEmpty()) {
            Path path = Paths.get(current);
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                fc.setInitialDirectory(parent.toFile());
            }
        } else {
            fc.setInitialDirectory(new File("").getAbsoluteFile());
        }
        
        File selected = fc.showOpenDialog(this.getWindow());
        if (selected != null && selected.exists()) {
            this.rootCertificateKeyPath.setText(selected.getAbsolutePath());
        }
    }

    /**
     * ルート証明書内容確認
     */
    @FXML
    void verifyRootCertificate(ActionEvent event) {
        String certPath = this.rootCertificatePath.getText();
        if (certPath == null || certPath.isEmpty()) {
            showError("ルート証明書ファイルを指定してください。");
            return;
        }
        
        Path certFile = Paths.get(certPath);
        if (!Files.exists(certFile)) {
            showError("指定されたルート証明書ファイルが見つかりません。");
            return;
        }
        
        try {
            // 証明書情報を取得
            CertificateInfo certInfo = this.certificateService.getCertificateInfo(certPath);
            
            // 証明書情報を表示
            StringBuilder info = new StringBuilder();
            info.append("ファイルパス: ").append(certInfo.filePath()).append("\n");
            info.append("証明書タイプ: X.509 (PEM形式)\n");
            info.append("\n");
            info.append("Subject: ").append(certInfo.subject()).append("\n");
            info.append("Issuer: ").append(certInfo.issuer()).append("\n");
            info.append("Serial: ").append(certInfo.serialNumber()).append("\n");
            info.append("有効期限: ").append(certInfo.notBefore()).append(" ～ ").append(certInfo.notAfter()).append("\n");
            info.append("署名アルゴリズム: ").append(certInfo.sigAlgName()).append("\n");
            
            // ダイアログ表示
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            alert.initOwner(this.getWindow());
            alert.setTitle("ルート証明書情報");
            alert.setHeaderText("ルート証明書情報");
            
            TextArea textArea = new TextArea(info.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefWidth(800);
            textArea.setPrefHeight(300);
            
            alert.getDialogPane().setContent(textArea);
            alert.showAndWait();
            
        } catch (Exception e) {
            log.error("ルート証明書の読み込みに失敗しました", e);
            showError("ルート証明書の読み込みに失敗しました。\nエラー: " + e.getMessage());
        }
    }

    /**
     * 出力先ディレクトリ選択
     */
    @FXML
    void selectOutputDirectory(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("証明書出力先ディレクトリの選択");
        
        // デフォルトフォルダは現在の出力先ディレクトリ
        String current = this.outputDirectory.getText();
        if (current != null && !current.isEmpty()) {
            File dir = new File(current);
            if (dir.exists() && dir.isDirectory()) {
                dc.setInitialDirectory(dir);
            }
        } else {
            dc.setInitialDirectory(new File("").getAbsoluteFile());
        }
        
        File selected = dc.showDialog(this.getWindow());
        if (selected != null && selected.isDirectory()) {
            this.outputDirectory.setText(selected.getAbsolutePath());
        }
    }

    /**
     * 証明書作成
     */
    @FXML
    void create(ActionEvent event) {
        // 入力チェック
        if (this.outputDirectory.getText() == null || this.outputDirectory.getText().isEmpty()) {
            showError("出力先ディレクトリを指定してください。");
            return;
        }
        
        File outputDir = new File(this.outputDirectory.getText());
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            showError("指定された出力先ディレクトリが存在しないか、ディレクトリではありません。");
            return;
        }
        
        // ルート証明書使用時のチェック
        if (this.useRootCertificate.isSelected()) {
            if (this.rootCertificatePath.getText() == null || this.rootCertificatePath.getText().isEmpty()) {
                showError("ルート証明書ファイルを指定してください。");
                return;
            }
            
            Path rootCertFile = Paths.get(this.rootCertificatePath.getText());
            if (!Files.exists(rootCertFile)) {
                showError("指定されたルート証明書ファイルが見つかりません。");
                return;
            }
            
            if (this.rootCertificatePassword.getText() == null || this.rootCertificatePassword.getText().isEmpty()) {
                showError("ルート証明書パスワードを入力してください。");
                return;
            }
        }
        
        try {
            String cn = this.commonName.getText();
            String org = this.organization.getText();
            String serverPassword = this.serverCertPassword.getText();
            String caPassword = this.caCertPassword.getText();
            String outputDirPath = this.outputDirectory.getText();
            
            log.info("証明書作成開始:");
            log.info("  Common Name: {}", cn);
            log.info("  Organization: {}", org);
            log.info("  Output Directory: {}", outputDirPath);
            log.info("  Server Password: {}", serverPassword);
            log.info("  CA Password: {}", caPassword);
            log.info("  Use Root Certificate: {}", this.useRootCertificate.isSelected());
            
            if (this.useRootCertificate.isSelected()) {
                log.info("  Root Certificate: {}", this.rootCertificatePath.getText());
                log.info("  Root Certificate Password: {}", this.rootCertificatePassword.getText());
                
                // 既存のルート証明書を使用してサーバー証明書を作成
                this.certificateService.createServerCertificateWithExistingCA(
                    this.rootCertificatePath.getText(),
                    this.rootCertificateKeyPath.getText(),
                    this.rootCertificatePassword.getText(),
                    cn, org, outputDirPath, serverPassword, caPassword);
            } else {
                // 新規にCA証明書とサーバー証明書を作成
                this.certificateService.createNewCAAndServerCertificate(cn, org, outputDirPath, serverPassword, caPassword);
            }
            
            showInfo("証明書作成が完了しました。\n\n" +
                    "作成された証明書:\n" +
                    "  - logbook-ca.crt : ブラウザにインストールするCA証明書\n" +
                    "  - kancolle.p12   : サーバーが使用する証明書\n\n" +
                    "ブラウザに logbook-ca.crt をインストールしてください。");
            
        } catch (Exception e) {
            log.error("証明書作成に失敗しました", e);
            showError("証明書作成に失敗しました。\nエラー: " + e.getMessage());
        }
    }

    /**
     * 閉じる
     */
    @FXML
    void close(ActionEvent event) {
        this.getWindow().close();
    }

    /**
     * エラーメッセージ表示
     */
    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
        InternalFXMLLoader.setGlobal(alert.getDialogPane());
        alert.initOwner(this.getWindow());
        alert.setTitle("エラー");
        alert.setHeaderText("エラー");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 情報メッセージ表示
     */
    private void showInfo(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
        InternalFXMLLoader.setGlobal(alert.getDialogPane());
        alert.initOwner(this.getWindow());
        alert.setTitle("情報");
        alert.setHeaderText("情報");
        alert.setContentText(message);
        alert.showAndWait();
    }
}

