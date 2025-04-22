package logbook.internal.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.eclipse.jetty.client.AsyncRequestContent;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.ee10.proxy.AsyncProxyServlet;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingCallback;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import logbook.bean.AppConfig;
import logbook.internal.LoggerHolder;
import logbook.internal.ThreadManager;
import logbook.plugin.PluginServices;
import logbook.proxy.ContentListenerSpi;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * リバースプロキシ
 *
 */
public final class ReverseProxyServlet extends AsyncProxyServlet {

    private static final long serialVersionUID = 1L;

    /** リスナー */
    private transient List<ContentListenerSpi> listeners;

    /*
     * ProxyHeadersの追加を防ぐ
     */
    @Override
    protected void addProxyHeaders(HttpServletRequest clientRequest, Request proxyRequest)
    {
        if (!AppConfig.get().isUseProxy()) { // アップストリームプロキシがある場合は除外
            // Http1.1ではデフォルトがkeep-aliveだが、ブラウザアクセスについている為を追加します
            if (proxyRequest.getVersion() == HttpVersion.HTTP_1_1) {
                proxyRequest.headers(headers -> headers.add(HttpHeader.CONNECTION, "keep-alive"));
           }
        }
    }
    
    /*
     * レスポンスが帰ってきた
     */
    @Override
    protected void onResponseContent(HttpServletRequest request, HttpServletResponse response,
            Response proxyResponse, byte[] buffer, int offset, int length, Callback callback) {

        CaptureHolder holder = (CaptureHolder) request.getAttribute(Filter.CONTENT_HOLDER);
        if (holder == null) {
            holder = new CaptureHolder();
            request.setAttribute(Filter.CONTENT_HOLDER, holder);
        }
        // ストリームに書き込む
        holder.putResponse(buffer);

        super.onResponseContent(request, response, proxyResponse, buffer, offset, length, callback);
    }

    /*
     * レスポンスが完了した
     */
    @Override
    protected void onProxyResponseSuccess(HttpServletRequest request, HttpServletResponse response, Response proxyResponse) {
        try {
            if(response.getStatus() == HttpServletResponse.SC_OK) {
                CaptureHolder holder = (CaptureHolder) request.getAttribute(Filter.CONTENT_HOLDER);
                if (holder != null) {
                	RequestMetaDataWrapper req = new RequestMetaDataWrapper();
                    req.set(request);

                    ResponseMetaDataWrapper res = new ResponseMetaDataWrapper();
                    res.set(response);

                    Runnable task = () -> {
                        this.invoke(req, res, holder);
                    };
                    ThreadManager.getExecutorService().submit(task);
                }
            }
        } catch (Exception e) {
            LoggerHolder.get().warn("リバースプロキシ サーブレットで例外が発生 req=" + request, e);
        } finally {
            // Help GC
            request.removeAttribute(Filter.CONTENT_HOLDER);
        }
        super.onProxyResponseSuccess(request, response, proxyResponse);
    }

    /*
     * HttpClientを作成する
     */
    @Override
    protected HttpClient newHttpClient() {
        HttpClient client = super.newHttpClient();
        // プロキシを設定する
        if (AppConfig.get().isUseProxy()) {
            // ポート
            int port = AppConfig.get().getProxyPort();
            // ホスト
            String host = AppConfig.get().getProxyHost();
            // 設定する
            ProxyConfiguration proxyConfig = client.getProxyConfiguration();
            proxyConfig.addProxy(new HttpProxy(host, port));
        }
        return client;
    }

    @Override
    protected Request.Content proxyRequestContent(HttpServletRequest request, HttpServletResponse response, Request proxyRequest) throws IOException
    {
    	AsyncRequestContent content = new AsyncRequestContent();
        request.getInputStream().setReadListener(newReadListener(request, response, proxyRequest, content));
        return content;
    }
    protected ReadListener newReadListener(HttpServletRequest request, HttpServletResponse response, Request proxyRequest, AsyncRequestContent content)
    {
        return new StreamReader(request, response, proxyRequest, content);
    }

    protected class StreamReader extends IteratingCallback implements ReadListener
    {
        private final byte[] buffer = new byte[getHttpClient().getRequestBufferSize()];
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final Request proxyRequest;
        private final AsyncRequestContent content;

        protected StreamReader(HttpServletRequest request, HttpServletResponse response, Request proxyRequest, AsyncRequestContent content)
        {
            this.request = request;
            this.response = response;
            this.proxyRequest = proxyRequest;
            this.content = content;
        }

        @Override
        public void onDataAvailable()
        {
            iterate();
        }

        @Override
        public void onAllDataRead()
        {
            if (_log.isDebugEnabled())
                _log.debug("{} proxying content to upstream completed", getRequestId(request));
            content.close();
        }

        @Override
        public void onError(Throwable t)
        {
            onClientRequestFailure(request, proxyRequest, response, t);
        }

        @Override
        protected Action process() throws Exception
        {
            int requestId = _log.isDebugEnabled() ? getRequestId(request) : 0;
            ServletInputStream input = request.getInputStream();

            while (input.isReady())
            {
                int read = input.read(buffer);
                if (_log.isDebugEnabled())
                    _log.debug("{} asynchronous read {} bytes on {}", requestId, read, input);
                if (read > 0)
                {
                    if (_log.isDebugEnabled())
                        _log.debug("{} proxying content to upstream: {} bytes", requestId, read);
                    onRequestContent(request, proxyRequest, content, buffer, 0, read, this);
                    return Action.SCHEDULED;
                }
                else if (read < 0)
                {
                    if (_log.isDebugEnabled())
                        _log.debug("{} asynchronous read complete on {}", requestId, input);
                    return Action.SUCCEEDED;
                }
            }

            if (_log.isDebugEnabled())
                _log.debug("{} asynchronous read pending on {}", requestId, input);
            return Action.IDLE;
        }

        protected void onRequestContent(HttpServletRequest request, Request proxyRequest, AsyncRequestContent content, byte[] buffer, int offset, int length, Callback callback)
        {
            CaptureHolder holder = (CaptureHolder) request.getAttribute(Filter.CONTENT_HOLDER);
            if (holder == null) {
                holder = new CaptureHolder();
                request.setAttribute(Filter.CONTENT_HOLDER, holder);
            }
            // ストリームに書き込む
            holder.putRequest(Arrays.copyOfRange(buffer, offset, length));

        	content.write(ByteBuffer.wrap(buffer, offset, length), callback);
        }

        @Override
        protected void onCompleteFailure(Throwable cause)
        {
            onError(cause);
        }
    }

    
    private void invoke(RequestMetaDataWrapper baseReq, ResponseMetaDataWrapper baseRes, CaptureHolder holder) {
        try {
            if (this.listeners == null) {
                this.listeners = PluginServices.instances(ContentListenerSpi.class).collect(Collectors.toList());
            }
            for (ContentListenerSpi listener : this.listeners) {
                RequestMetaDataWrapper req = baseReq.clone();
                req.set(holder.getRequest());

                if (listener.test(req)) {
                    ResponseMetaDataWrapper res = baseRes.clone();
                    res.set(holder.getResponse());

                    Runnable task = () -> {
                        try {
                            listener.accept(req, res);
                        } catch (Exception e) {
                            LoggerHolder.get().warn("リバースプロキシ サーブレットで例外が発生", e);
                        }
                    };
                    ThreadManager.getExecutorService().submit(task);
                }
            }
            holder.clear();
        } catch (Exception e) {
            LoggerHolder.get().warn("リバースプロキシ サーブレットで例外が発生 req=" + baseReq.getRequestURI(), e);
        }
    }

    static class RequestMetaDataWrapper implements RequestMetaData, Cloneable {

        private String contentType;

        private String method;

        private Map<String, List<String>> parameterMap;

        private String queryString;

        private String requestURI;

        private Optional<InputStream> requestBody;

        @Override
        public String getContentType() {
            return this.contentType;
        }

        void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public String getMethod() {
            return this.method;
        }

        void setMethod(String method) {
            this.method = method;
        }

        @Override
        public Map<String, List<String>> getParameterMap() {
            return this.parameterMap;
        }

        void setParameterMap(Map<String, List<String>> parameterMap) {
            this.parameterMap = parameterMap;
        }

        @Override
        public String getQueryString() {
            return this.queryString;
        }

        void setQueryString(String queryString) {
            this.queryString = queryString;
        }

        @Override
        public String getRequestURI() {
            return this.requestURI;
        }

        void setRequestURI(String requestURI) {
            this.requestURI = requestURI;
        }

        @Override
        public Optional<InputStream> getRequestBody() {
            return this.requestBody;
        }

        void setRequestBody(Optional<InputStream> requestBody) {
            this.requestBody = requestBody;
        }

        void set(HttpServletRequest req) {
            this.setContentType(req.getContentType());
            this.setMethod(req.getMethod().toString());
            this.setQueryString(req.getQueryString());
            this.setRequestURI(req.getRequestURI());
        }

        void set(InputStream body) {
            String bodystr;
            try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                int len;
                char[] cbuf = new char[128];
                StringBuilder sb = new StringBuilder();
                while ((len = reader.read(cbuf)) > 0) {
                    sb.append(cbuf, 0, len);
                }
                bodystr = URLDecoder.decode(sb.toString(), "UTF-8");
            } catch (IOException e) {
                bodystr = "";
            }
            Map<String, List<String>> map = new LinkedHashMap<>();
            for (String part : bodystr.split("&")) {
                String key;
                String value;
                int idx = part.indexOf('=');
                if (idx > 0) {
                    key = part.substring(0, idx);
                    value = part.substring(idx + 1, part.length());
                } else {
                    key = part;
                    value = null;
                }
                map.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(value);
            }
            this.setParameterMap(map);
            this.setRequestBody(Optional.of(body));
        }

        @Override
        public RequestMetaDataWrapper clone() {
            RequestMetaDataWrapper clone = new RequestMetaDataWrapper();
            clone.setContentType(this.getContentType());
            clone.setMethod(this.getMethod());
            clone.setQueryString(this.getQueryString());
            clone.setRequestURI(this.getRequestURI());
            clone.setParameterMap(this.getParameterMap());
            clone.setRequestBody(this.getRequestBody());
            return clone;
        }
    }

    static class ResponseMetaDataWrapper implements ResponseMetaData, Cloneable {

        private int status;

        private String contentType;

        private Optional<InputStream> responseBody;

        @Override
        public int getStatus() {
            return this.status;
        }

        void setStatus(int status) {
            this.status = status;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public Optional<InputStream> getResponseBody() {
            return this.responseBody;
        }

        void setResponseBody(Optional<InputStream> responseBody) {
            this.responseBody = responseBody;
        }

        void set(HttpServletResponse res) {
            this.setStatus(res.getStatus());
            this.setContentType(res.getContentType());
        }

        void set(InputStream body) throws IOException {
            this.setResponseBody(Optional.of(ungzip(body)));
        }

        @Override
        public ResponseMetaDataWrapper clone() {
            ResponseMetaDataWrapper clone = new ResponseMetaDataWrapper();
            clone.setStatus(this.getStatus());
            clone.setContentType(this.getContentType());
            clone.setResponseBody(this.getResponseBody());
            return clone;
        }

        private static InputStream ungzip(InputStream body) throws IOException {
            body.mark(Short.BYTES);
            int magicbyte = body.read() << 8 ^ body.read();
            body.reset();
            if (magicbyte == 0x1f8b) {
                return new GZIPInputStream(body);
            }
            return body;
        }
    }
}