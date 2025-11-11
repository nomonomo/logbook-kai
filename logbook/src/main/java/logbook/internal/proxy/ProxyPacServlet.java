package logbook.internal.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import logbook.bean.AppConfig;
import logbook.plugin.PluginServices;
import lombok.extern.slf4j.Slf4j;

/**
 * プロキシ自動設定ファイル（proxy.pac）を返すサーブレット
 */
@Slf4j
public final class ProxyPacServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    /** Content-Type: application/x-ns-proxy-autoconfig */
    private static final String CONTENT_TYPE = "application/x-ns-proxy-autoconfig";
    
    /** プレースホルダー {port} */
    private static final String PLACEHOLDER_PORT = "{port}";
    
    /** 読み込まれたPACファイルのテンプレート内容（{port}プレースホルダーを含む） */
    private String pacTemplate;
    
    @Override
    public void init() throws ServletException {
        super.init();
        
        try {
            // リソースからproxy.pacファイルを読み込む（初期化時に一度だけ実行）
            try (InputStream in = PluginServices.getResourceAsStream("logbook/proxy.pac")) {
                if (in == null) {
                    throw new IOException("リソース 'logbook/proxy.pac' が見つかりません");
                }
                
                // Java 9以降のreadAllBytes()を使用して一括読み込み
                byte[] bytes = in.readAllBytes();
                pacTemplate = new String(bytes, StandardCharsets.UTF_8);
                log.debug("proxy.pacテンプレートを読み込みました");
            }
        } catch (IOException e) {
            log.error("proxy.pacファイルの読み込みに失敗しました", e);
            throw new ServletException("プロキシ自動設定ファイルの読み込みに失敗しました", e);
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // ポート番号を取得して置き換え（String操作でシンプルに処理）
        int port = AppConfig.get().getListenPort();
        String processedContent = pacTemplate.replace(PLACEHOLDER_PORT, String.valueOf(port));
        
        // Content-Typeを設定（charsetを含めないためsetHeaderを直接使用）
        response.setHeader("Content-Type", CONTENT_TYPE);
        
        // レスポンスを書き込む（OutputStreamを使用してcharsetの自動追加を防ぐ）
        byte[] contentBytes = processedContent.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(contentBytes.length);
        response.getOutputStream().write(contentBytes);
        
        log.debug("proxy.pacファイルを返却しました（ポート: {}）", port);
    }
}

