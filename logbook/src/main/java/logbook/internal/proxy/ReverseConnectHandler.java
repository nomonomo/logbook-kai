//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package logbook.internal.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.compression.brotli.BrotliCompression;
import org.eclipse.jetty.compression.gzip.GzipCompression;

import logbook.internal.ThreadManager;
import logbook.plugin.PluginServices;
import logbook.proxy.ContentListenerSpi;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jetty.http.HttpException;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.Retainable;
import org.eclipse.jetty.io.RetainableByteBuffer;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.TunnelSupport;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.HostPort;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.X509;

/**
 * <p>HTTP CONNECTメソッドをサポートする{@link Handler}実装。完全なHTTPパース機能を持つ。</p>
 * <p>このハンドラーは2つのモードで動作可能：</p>
 * <ul>
 * <li><b>HTTP インターセプトモード</b> (デフォルト): クライアントからのHTTPリクエストとサーバーからのHTTPレスポンスの
 *     両方をパースし、CaptureHolderに保存してContentListenerSpiプラグインで処理する。</li>
 * <li><b>透過トンネルモード</b>: パースを行わず、クライアントとサーバー間でバイトストリームを単純に転送する。</li>
 * </ul>
 * 
 * <p><b>アーキテクチャ:</b></p>
 * <ul>
 * <li><b>DownstreamConnection</b>: クライアント→プロキシ間の接続を処理。HttpParser.RequestHandlerを使用して
 *     HTTPリクエストをパースし、リクエストボディをCaptureHolderに保存する。</li>
 * <li><b>UpstreamConnection</b>: プロキシ→サーバー間の接続を処理。HttpParser.ResponseHandlerを使用して
 *     HTTPレスポンスをパースし、レスポンスボディをCaptureHolderに保存する。</li>
 * <li><b>HttpClientConnectionListener</b>: 各トンネルのCaptureHolderを管理し、HTTPトランザクション完了時に
 *     ContentListenerSpiプラグインを呼び出す。</li>
 * </ul>
 * 
 * <h3>Usage Example: Default HTTP Interception</h3>
 * <pre>{@code
 * // By default, both HTTP request and response parsing are enabled
 * // Content is automatically captured in CaptureHolder and processed by plugins
 * ReverseConnectHandler handler = new ReverseConnectHandler();
 * server.setHandler(handler);
 * }</pre>
 * 
 * <h3>Usage Example: Custom HTTP Interception</h3>
 * <pre>{@code
 * ReverseConnectHandler handler = new ReverseConnectHandler() {
 *     @Override
 *     protected Connection.Listener createConnectionListener(ConnectContext connectContext) {
 *         return new HttpClientConnectionListener(connectContext, getHttpClient()) {
 *             @Override
 *             public void onSuccess() {
 *                 // Handle successful HTTP transaction
 *                 // CaptureHolder now contains both request and response bodies
 *                 super.onSuccess();
 *                 
 *                 // Access captured data from CaptureHolder2
 *                 CaptureHolder2.HttpRequest request = getCaptureHolder().getCurrentRequest();
 *                 CaptureHolder2.HttpResponse response = getCaptureHolder().getCurrentResponse();
 *                 // Custom processing...
 *             }
 *         };
 *     }
 * };
 * }</pre>
 * 
 * <h3>Usage Example: Disable HTTP Parsing (Transparent Mode)</h3>
 * <pre>{@code
 * ReverseConnectHandler handler = new ReverseConnectHandler() {
 *     @Override
 *     protected DownstreamConnection newDownstreamConnection(EndPoint endPoint, ConcurrentMap<String, Object> context) {
 *         DownstreamConnection conn = super.newDownstreamConnection(endPoint, context);
 *         conn.setParseHttpRequest(false);  // Disable request parsing
 *         return conn;
 *     }
 *     
 *     @Override
 *     protected UpstreamConnection newUpstreamConnection(EndPoint endPoint, ConnectContext connectContext) {
 *         UpstreamConnection conn = super.newUpstreamConnection(endPoint, connectContext);
 *         conn.setParseHttpResponse(false);  // Disable response parsing
 *         return conn;
 *     }
 * };
 * }</pre>
 */
@Slf4j
public class ReverseConnectHandler extends Handler.Wrapper
{
    private final Set<String> whiteList = new HashSet<>();
    private final Set<String> blackList = new HashSet<>();
    private SslContextFactory.Server sslContextFactoryServer;  // For downstream (accepting client connections)
    private SslContextFactory.Client sslContextFactoryClient;  // For upstream (connecting to servers)
    private HttpClient httpClient;  // HttpClient for managing connections
    private Executor executor;
    private Scheduler scheduler;
    private ByteBufferPool bufferPool;
    
    /**
     * ContentListenerSpiプラグインのリスト（全トンネルで共有）
     * doStart()で初期化される
     */
    private static List<ContentListenerSpi> contentListeners;
    
    private SelectorManager selector;
    private long connectTimeout = 15000;
    private long idleTimeout = 30000;
    private int bufferSize = 4096;

    public ReverseConnectHandler()
    {
        this(null);
    }

    public ReverseConnectHandler(Handler handler)
    {
        super(handler);
    }
    
    /**
     * SSL Context Factoryを指定してReverseConnectHandlerを構築する。
     * 
     * @param sslServer サーバー証明書用のSSL Context Factory
     * @param sslClient クライアント接続用のSSL Context Factory
     */
    public ReverseConnectHandler(SslContextFactory.Server sslServer, SslContextFactory.Client sslClient)
    {
        super(null);
        this.sslContextFactoryServer = sslServer;
        this.sslContextFactoryClient = sslClient;
    }
    
    /**
     * Set the SSL Context Factory for server (downstream) connections.
     * This is called when the certificate is reloaded.
     * 
     * @param sslServer the new SSL Context Factory
     */
    public void setSslContextFactoryServer(SslContextFactory.Server sslServer)
    {
        this.sslContextFactoryServer = sslServer;
        log.info("Updated SSL Context Factory for downstream connections");
    }

    public Executor getExecutor()
    {
        return executor;
    }

    public void setExecutor(Executor executor)
    {
        this.executor = executor;
    }

    public Scheduler getScheduler()
    {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler)
    {
        updateBean(this.scheduler, scheduler);
        this.scheduler = scheduler;
    }

    public ByteBufferPool getByteBufferPool()
    {
        return bufferPool;
    }

    public void setByteBufferPool(ByteBufferPool bufferPool)
    {
        updateBean(this.bufferPool, bufferPool);
        this.bufferPool = bufferPool;
    }

    /**
     * Get the timeout, in milliseconds, to connect to the remote server.
     * @return the timeout, in milliseconds, to connect to the remote server
     */
    public long getConnectTimeout()
    {
        return connectTimeout;
    }

    /**
     * Set the timeout, in milliseconds, to connect to the remote server.
     * @param connectTimeout the timeout, in milliseconds, to connect to the remote server
     */
    public void setConnectTimeout(long connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Get the idle timeout, in milliseconds.
     * @return the idle timeout, in milliseconds
     */
    public long getIdleTimeout()
    {
        return idleTimeout;
    }

    /**
     * Set the idle timeout, in milliseconds.
     * @param idleTimeout the idle timeout, in milliseconds
     */
    public void setIdleTimeout(long idleTimeout)
    {
        this.idleTimeout = idleTimeout;
    }

    public int getBufferSize()
    {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize)
    {
        this.bufferSize = bufferSize;
    }
    
    /**
     * Get the HttpClient used for connection management.
     * @return the HttpClient instance
     */
    public HttpClient getHttpClient()
    {
        return httpClient;
    }
    
    /**
     * Set the HttpClient to use for connection management.
     * @param httpClient the HttpClient instance
     */
    public void setHttpClient(HttpClient httpClient)
    {
        updateBean(this.httpClient, httpClient);
        this.httpClient = httpClient;
    }

    @Override
    protected void doStart() throws Exception
    {
        if (executor == null)
            executor = getServer().getThreadPool();

        if (scheduler == null)
        {
            scheduler = getServer().getScheduler();
            addBean(scheduler);
        }

        if (bufferPool == null)
        {
            bufferPool = getServer().getByteBufferPool();
            addBean(bufferPool);
        }

        // Initialize HttpClient if not already set
        if (httpClient == null)
        {
            httpClient = newHttpClient();
            addBean(httpClient);
        }

        addBean(selector = newSelectorManager());
        selector.setConnectTimeout(getConnectTimeout());

        // SSL Context Factoryはコンストラクタで設定済み
        // （ProxyServerImplから渡される）
        
        // Initialize content listeners
        initializeContentListeners();

        super.doStart();
    }
    
    /**
     * Creates a new HttpClient instance.
     * Override this method to customize the HttpClient.
     * 
     * @return a new HttpClient instance
     */
    protected HttpClient newHttpClient()
    {
        HttpClient client = new HttpClient();
        client.setExecutor(getExecutor());
        client.setScheduler(getScheduler());
        client.setByteBufferPool(getByteBufferPool());
        client.setConnectTimeout(getConnectTimeout());
        client.setIdleTimeout(getIdleTimeout());
        
        log.debug("Created HttpClient for ReverseConnectHandler");
        
        return client;
    }

    protected SelectorManager newSelectorManager()
    {
        return new ConnectManager(getExecutor(), getScheduler(), 1);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        if (HttpMethod.CONNECT.is(request.getMethod()))
        {
            TunnelSupport tunnelSupport = request.getTunnelSupport();
            if (tunnelSupport == null)
            {
                Response.writeError(request, response, callback, HttpStatus.NOT_IMPLEMENTED_501);
                return true;
            }

            if (tunnelSupport.getProtocol() == null)
            {
                HttpURI httpURI = request.getHttpURI();
                String serverAddress = httpURI.getAuthority();
                
                // 証明書にマッチしないホストは、後続のConnectHandler（proxy2）で処理される
                if (serverAddress != null && !isHostAllowedBySslContext(serverAddress))
                {
                    log.debug("CONNECT host {} not allowed by SSL certificate validation, passing to next handler", serverAddress);
                    return super.handle(request, response, callback);
                }
                
                log.debug("CONNECT request for {}", serverAddress);
                handleConnect(request, response, callback, serverAddress);
                return true;
            }
        }
        return super.handle(request, response, callback);
    }

    /**
     * <p>Handles a CONNECT request.</p>
     * <p>CONNECT requests may have authentication headers such as {@code Proxy-Authorization}
     * that authenticate the client with the proxy.</p>
     *
     * @param request the jetty request
     * @param response the jetty response
     * @param callback the callback with which to generate a response
     * @param serverAddress the remote server address in the form {@code host:port}
     */
    protected void handleConnect(Request request, Response response, Callback callback, String serverAddress)
    {
        try
        {
            boolean proceed = handleAuthentication(request, response, serverAddress);
            if (!proceed)
            {
                log.debug("Missing proxy authentication");
                sendConnectResponse(request, response, callback, HttpStatus.PROXY_AUTHENTICATION_REQUIRED_407);
                return;
            }

            HostPort hostPort = new HostPort(serverAddress);
            String host = hostPort.getHost();
            int port = hostPort.getPort(80);

            if (!validateDestination(host, port))
            {
                log.debug("Destination {}:{} forbidden", host, port);
                sendConnectResponse(request, response, callback, HttpStatus.FORBIDDEN_403);
                return;
            }

            log.debug("Connecting to {}:{}", host, port);

            connectToServer(request, host, port, new Promise<>()
            {
                @Override
                public void succeeded(SocketChannel channel)
                {
                    ConnectContext connectContext = new ConnectContext(request, response, callback, request.getTunnelSupport().getEndPoint());
                    // 接続先情報をAttributesに保存（SSL証明書エラーログで使用）
                    connectContext.getContext().put("targetServerAddress", serverAddress);
                    log.debug("connected to server: {}", channel.isConnected() ? "connected" : "not connected");
                    if (channel.isConnected())
                        selector.accept(channel, connectContext);
                    else
                        selector.connect(channel, connectContext);
                }

                @Override
                public void failed(Throwable x)
                {
                    onConnectFailure(request, response, callback, x);
                }
            });
        }
        catch (Exception x)
        {
            onConnectFailure(request, response, callback, x);
        }
    }

    protected void connectToServer(Request request, String host, int port, Promise<SocketChannel> promise)
    {
        SocketChannel channel = null;
        try
        {
            channel = SocketChannel.open();
            channel.socket().setTcpNoDelay(true);
            channel.configureBlocking(false);
            InetSocketAddress address = newConnectAddress(host, port);
            channel.connect(address);
            promise.succeeded(channel);
        }
        catch (Throwable x)
        {
            close(channel);
            promise.failed(x);
        }
    }

    private void close(Closeable closeable)
    {
        try
        {
            if (closeable != null)
                closeable.close();
        }
        catch (Throwable x)
        {
            log.trace("IGNORED", x);
        }
    }

    /**
     * Creates the server address to connect to.
     *
     * @param host The host from the CONNECT request
     * @param port The port from the CONNECT request
     * @return The InetSocketAddress to connect to.
     */
    protected InetSocketAddress newConnectAddress(String host, int port)
    {
        return new InetSocketAddress(host, port);
    }

    protected void onConnectSuccess(ConnectContext connectContext, UpstreamConnection upstreamConnection)
    {
        ConcurrentMap<String, Object> context = connectContext.getContext();
        Request request = connectContext.getRequest();
        prepareContext(request, context);

        EndPoint downstreamEndPoint = connectContext.getEndPoint();
        DownstreamConnection downstreamConnection = newDownstreamConnection(downstreamEndPoint, context);
        // downstreamConnection.setConnectContext(connectContext);
        downstreamConnection.setInputBufferSize(getBufferSize());
        
        // Add connection listener if needed - share the same listener instance between connections
        Connection.Listener listener = createConnectionListener(connectContext);
        if (listener != null)
        {
            downstreamConnection.addConnectionListener(listener);
            
            // Add HTTP client listener to both downstream and upstream connections
            // This ensures that request info (from downstream) and response info (from upstream)
            // are captured by the same listener instance
            if (listener instanceof HttpClientConnectionListener)
            {
                HttpClientConnectionListener httpClientListener = (HttpClientConnectionListener) listener;
                
                // ダウンストリーム（クライアント→プロキシ）とアップストリーム（プロキシ→サーバー）の
                // 両方に同じリスナーインスタンスを設定し、HTTPリクエスト/レスポンスをキャプチャ
                downstreamConnection.setHttpClientListener(httpClientListener);
                upstreamConnection.setHttpClientListener(httpClientListener);
            }
        }

        upstreamConnection.setConnection(downstreamConnection);
        downstreamConnection.setConnection(upstreamConnection);
        
        // DownstreamConnectionのEndPointを確認（SSL wrapping後のEndPoint）
        boolean downstreamSSL = downstreamConnection.getEndPoint() instanceof SslConnection.SslEndPoint;
        boolean upstreamSSL = upstreamConnection.getEndPoint() instanceof SslConnection.SslEndPoint;
        log.debug("Connection setup completed: {}<->{}  (downstream SSL: {}, upstream SSL: {})", 
            downstreamConnection, upstreamConnection, downstreamSSL, upstreamSSL);

        Response response = connectContext.getResponse();
        upgradeConnection(request, downstreamConnection);
        sendConnectResponse(request, response, connectContext.callback, HttpStatus.OK_200);
    }

    protected void onConnectFailure(Request request, Response response, Callback callback, Throwable failure)
    {
        log.debug("CONNECT failed", failure);
        sendConnectResponse(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500);
    }

    private void sendConnectResponse(Request request, Response response, Callback callback, int statusCode)
    {
        try
        {
            response.getHeaders().put(HttpFields.CONTENT_LENGTH_0);
            if (statusCode != HttpStatus.OK_200)
            {
                response.getHeaders().put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE);
                Response.writeError(request, response, callback, statusCode);
            }
            else
            {
                response.setStatus(HttpStatus.OK_200);
                callback.succeeded();
            }
            log.debug("CONNECT response sent {} {}", request.getConnectionMetaData().getProtocol(), statusCode);
        }
        catch (Throwable x)
        {
            log.debug("Could not send CONNECT response", x);
        }
    }

    /**
     * <p>Handles the authentication before setting up the tunnel to the remote server.</p>
     * <p>The default implementation returns true.</p>
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param address the address of the remote server in the form {@code host:port}.
     * @return true to allow to connect to the remote host, false otherwise
     */
    protected boolean handleAuthentication(Request request, Response response, String address)
    {
        return true;
    }

    protected DownstreamConnection newDownstreamConnection(EndPoint endPoint, ConcurrentMap<String, Object> context)
    {
        DownstreamConnection downstreamConnection;
        
        // Check if we should use SSL for downstream connection (client to proxy)
        if (shouldUseSSLForDownstream(endPoint, context))
        {
            try
            {
                // Create SSL-wrapped endpoint for downstream connection
                EndPoint sslEndPoint = wrapWithSSLForDownstream(endPoint, context);
                downstreamConnection = new DownstreamConnection(sslEndPoint, getExecutor(), getByteBufferPool(), context);
                
                // Set the DownstreamConnection as the connection for the SslEndPoint
                if (sslEndPoint instanceof SslConnection.SslEndPoint)
                {
                    sslEndPoint.setConnection(downstreamConnection);
                    
                    log.debug("Set DownstreamConnection to SslEndPoint");
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to create SSL downstream connection, falling back to plain connection", e);
                downstreamConnection = new DownstreamConnection(endPoint, getExecutor(), getByteBufferPool(), context);
            }
        }
        else
        {
            downstreamConnection = new DownstreamConnection(endPoint, getExecutor(), getByteBufferPool(), context);
        }
        
        // HTTPリクエストのパース機能をデフォルトで有効化
        downstreamConnection.setParseHttpRequest(true);
        
        return downstreamConnection;
    }

    protected UpstreamConnection newUpstreamConnection(EndPoint endPoint, ConnectContext connectContext)
    {
        UpstreamConnection upstreamConnection;
        
        // Check if we should use SSL for upstream connection
        if (shouldUseSSLForUpstream(connectContext))
        {
            try
            {
                // Create SSL-wrapped endpoint for upstream connection
                EndPoint sslEndPoint = wrapWithSSL(endPoint, connectContext);
                log.debug("Created SSL upstream connection");
                upstreamConnection = new UpstreamConnection(sslEndPoint, getExecutor(), getByteBufferPool(), connectContext);
            }
            catch (Exception e)
            {
                log.warn("Failed to create SSL upstream connection, falling back to plain connection", e);
                upstreamConnection = new UpstreamConnection(endPoint, getExecutor(), getByteBufferPool(), connectContext);
            }
        }
        else
        {
            upstreamConnection = new UpstreamConnection(endPoint, getExecutor(), getByteBufferPool(), connectContext);
        }
        
        // HTTPレスポンスのパース機能をデフォルトで有効化
        upstreamConnection.setParseHttpResponse(true);
        
        // HttpClientConnectionListenerはonConnectSuccess()で追加され、
        // ダウンストリームとアップストリームで同じインスタンスが共有される
        
        return upstreamConnection;
    }
    
    /**
     * Determines if SSL should be used for the upstream connection based on the connect context.
     * Override this method to customize SSL behavior.
     * 
     * @param connectContext the connection context
     * @return true if SSL should be used, false otherwise
     */
    protected boolean shouldUseSSLForUpstream(ConnectContext connectContext)
    {
        // By default, use SSL for upstream (proxy to server) connections if sslContextFactoryClient is available
        // Override this method to disable SSL or customize based on specific conditions (e.g., port-based)
        return sslContextFactoryClient != null;
    }
    
    /**
     * Wraps an EndPoint with SSL/TLS encryption using SslConnection.
     * 
     * @param endPoint the endpoint to wrap
     * @param connectContext the connection context
     * @return SSL-wrapped endpoint
     * @throws Exception if SSL wrapping fails
     */
    protected EndPoint wrapWithSSL(EndPoint endPoint, ConnectContext connectContext) throws Exception
    {
        if (sslContextFactoryClient == null)
            throw new IllegalStateException("SslContextFactory.Client not initialized");
        
        // Get target host and port for SNI
        Request request = connectContext.getRequest();
        String serverAddress = request.getHttpURI().getAuthority();
        HostPort hostPort = new HostPort(serverAddress);
        String host = hostPort.getHost();
        int port = hostPort.getPort(443);
        
        // Create SSLEngine in client mode for upstream connection with SNI
        SSLEngine sslEngine = sslContextFactoryClient.newSSLEngine(host, port);
        sslEngine.setUseClientMode(true);
        
        log.debug("Creating SSL client connection to {}:{} with SNI", host, port);
        
        // Create SslConnection wrapping the endpoint
        // Note: SslConnection lifecycle is managed by the EndPoint and will be closed
        // when the connection is terminated, so this is not a resource leak
        @SuppressWarnings("resource")
        SslConnection sslConnection = new SslConnection(
            getByteBufferPool(),
            getExecutor(),
            sslContextFactoryClient,
            endPoint,
            sslEngine,
            true,  // useDirectBuffersForEncryption
            true   // useDirectBuffersForDecryption
        );
        
        // Configure SSL connection
        sslConnection.setRenegotiationAllowed(sslContextFactoryClient.isRenegotiationAllowed());
        sslConnection.setRenegotiationLimit(sslContextFactoryClient.getRenegotiationLimit());
        
        // Return the SSL endpoint which will handle encryption/decryption
        return sslConnection.getSslEndPoint();
    }
    
    /**
     * Determines if SSL should be used for the downstream connection (client to proxy).
     * Override this method to customize SSL behavior.
     * 
     * @param endPoint the client endpoint
     * @param context the connection context
     * @return true if SSL should be used, false otherwise
     */
    protected boolean shouldUseSSLForDownstream(EndPoint endPoint, ConcurrentMap<String, Object> context)
    {
        // MITMモード: プロキシがSSL終端を行い、HTTPリクエストをキャプチャ
        // ブラウザ → プロキシ: SSL終端（プロキシの証明書 *.kancolle-server.com）
        // プロキシ → サーバー: 新しいSSL接続（サーバーの証明書）
        //
        // これにより、プロキシがHTTPリクエスト/レスポンスの内容を解読・キャプチャできる
        // 前提: ブラウザにCA証明書（logbook-ca.crt）がインストールされている
        return sslContextFactoryServer != null;
    }
    
    /**
     * Wraps an EndPoint with SSL/TLS encryption for downstream connection (client to proxy).
     * This creates a server-mode SSL connection to accept encrypted connections from clients.
     * 
     * @param endPoint the endpoint to wrap
     * @param context the connection context
     * @return SSL-wrapped endpoint
     * @throws Exception if SSL wrapping fails
     */
    protected EndPoint wrapWithSSLForDownstream(EndPoint endPoint, ConcurrentMap<String, Object> context) throws Exception
    {
        if (sslContextFactoryServer == null)
            throw new IllegalStateException("SslContextFactory.Server not initialized");
        
        // Create SSLEngine in server mode for downstream connection (accepting from client)
        SSLEngine sslEngine = sslContextFactoryServer.newSSLEngine();
        sslEngine.setUseClientMode(false); // Server mode - accepting connections
        
        // Request client authentication if needed (optional)
        // sslEngine.setNeedClientAuth(false);
        // sslEngine.setWantClientAuth(false);
        
        log.debug("Creating SSL server connection for downstream from {}", endPoint.getRemoteSocketAddress());
        
        // Create SslConnection wrapping the endpoint
        // Note: SslConnection lifecycle is managed by the EndPoint and will be closed
        // when the connection is terminated, so this is not a resource leak
        @SuppressWarnings("resource")
        SslConnection sslConnection = new SslConnection(
            getByteBufferPool(),
            getExecutor(),
            sslContextFactoryServer,
            endPoint,
            sslEngine,
            true,  // useDirectBuffersForEncryption
            true   // useDirectBuffersForDecryption
        );
        
        // Configure SSL connection
        sslConnection.setRenegotiationAllowed(sslContextFactoryServer.isRenegotiationAllowed());
        sslConnection.setRenegotiationLimit(sslContextFactoryServer.getRenegotiationLimit());
        
        // Jetty標準パターン：親コンテナ（ReverseConnectHandler）からSslHandshakeListenerを自動取得
        // ProxyServerImplがaddBean()で登録されたSslHandshakeListenerインスタンスを取得し、
        // SslConnectionに自動的に追加する（SslConnectionFactory.configure()と同じパターン）
        getBeans(SslHandshakeListener.class)
            .forEach(sslConnection::addHandshakeListener);
        
        log.debug("SSL downstream connection prepared with {} handshake listener(s), waiting for client handshake",
            getBeans(SslHandshakeListener.class).size());
        
        // Return the SSL endpoint which will handle encryption/decryption
        // Note: The SslConnection will be opened when the DownstreamConnection is set and opened
        return sslConnection.getSslEndPoint();
    }

    protected void prepareContext(Request request, ConcurrentMap<String, Object> context)
    {
    }
    
    /**
     * ContentListenerSpiプラグインを初期化する。
     * doStart()から呼ばれる。
     */
    private void initializeContentListeners()
    {
        try
        {
            contentListeners = PluginServices.instances(ContentListenerSpi.class)
                .collect(Collectors.toList());
            log.info("Initialized {} content listeners", contentListeners.size());
        }
        catch (Exception e)
        {
            log.error("Failed to initialize content listeners", e);
            contentListeners = Collections.emptyList();
        }
    }
    
    /**
     * ContentListenerSpiプラグインのリストを取得する。
     * @return ContentListenerSpiのリスト（変更不可）
     */
    private static List<ContentListenerSpi> getContentListeners()
    {
        return contentListeners != null ? contentListeners : Collections.emptyList();
    }
    
    /**
     * Creates a Connection.Listener for the downstream connection.
     * Override this method to provide a custom listener (e.g., HttpClient).
     * 
     * @param connectContext the connection context
     * @return a Connection.Listener or null if no listener is needed
     */
    protected Connection.Listener createConnectionListener(ConnectContext connectContext)
    {
        // Create a listener that integrates with HttpClient
        if (httpClient != null)
        {
            return new HttpClientConnectionListener(connectContext, httpClient);
        }
        return null;
    }
    
    /**
     * HttpClientと統合するConnection.Listener実装。
     * 接続ライフサイクルを追跡し、HttpClientリソースを管理する。
     * onContentとonSuccessメソッドを通じてHTTPコンテンツ処理のフックを提供する。
     * <p>
     * 注: HttpClientConnectionListenerインスタンスはCONNECTトンネルごとに1つ作成される。
     * 1つのトンネルは複数のHTTPリクエスト/レスポンス交換を運ぶことができる。
     * CaptureHolder2が完全なメタデータサポートでHTTPトランザクションを管理する。
     * </p>
     */
    public static class HttpClientConnectionListener implements Connection.Listener
    {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HttpClientConnectionListener.class);
        private final ConnectContext connectContext;
        private final HttpClient httpClient;
        private final CaptureHolder2 captureHolder;
        
        /**
         * クライアント（ダウンストリーム）がHTTPトランザクション進行中に異常終了したことを示すフラグ。
         * 
         * <p><b>設定条件：</b></p>
         * <pre>{@code
         * DownstreamConnection.onClose(cause) {
         *     if (cause != null && hasValidRequest()) {
         *         clientDisconnectedEarly = true;  // ★ここで設定
         *     }
         * }
         * }</pre>
         * 
         * <p><b>使用箇所：</b></p>
         * <pre>{@code
         * HttpClientConnectionListener.onSuccess() {
         *     if (clientDisconnectedEarly) {
         *         return;  // 不完全なHTTPデータの処理をスキップ
         *     }
         *     // 正常なHTTPデータのみ処理
         * }
         * }</pre>
         * 
         * <p><b>volatileの理由：</b>
         * DownstreamConnection.onClose()とHttpClientConnectionListener.onSuccess()は
         * 異なるスレッドで実行される可能性があるため、メモリ可視性を保証する。</p>
         */
        private volatile boolean clientDisconnectedEarly = false;
        
        public HttpClientConnectionListener(ConnectContext connectContext, HttpClient httpClient)
        {
            this.connectContext = connectContext;
            this.httpClient = httpClient;
            // Create CaptureHolder2 for this tunnel (manages all HTTP transactions)
            this.captureHolder = new CaptureHolder2();
            
            log.debug("Created CaptureHolder2 for tunnel: {}", connectContext.getRequest().getHttpURI());
        }
        
        /**
         * Get the CaptureHolder2 for this tunnel connection.
         * @return the CaptureHolder2 instance
         */
        public CaptureHolder2 getCaptureHolder()
        {
            return captureHolder;
        }
        
        /**
         * HTTPトランザクションが進行中かどうかをチェックする。
         * 
         * <p>このメソッドは、HTTPリクエスト行（例：GET /api HTTP/1.1）が受信されてから、
         * HTTPトランザクションが完了してonSuccess()が呼ばれるまでの間、trueを返す。</p>
         * 
         * <p><b>ライフサイクル：</b></p>
         * <ul>
         * <li>false: 接続開始時（TLSハンドシェイクのみ）</li>
         * <li>true:  RequestParserHandler.startRequest()呼び出し後</li>
         * <li>true:  HTTPリクエスト/レスポンス処理中</li>
         * <li>false: onSuccess()でcompleteTransaction()が呼ばれた後</li>
         * </ul>
         * 
         * <p><b>使用目的：</b></p>
         * <ul>
         * <li>DownstreamConnection.onClose()：HTTPトランザクション進行中の異常終了を検知</li>
         * <li>UpstreamConnection.onClose()：HTTPパーサー未完了時のフォールバック判定</li>
         * </ul>
         * 
         * <p><b>注意：</b>このメソッド単独では異常終了を検知できません。
         * 必ず{@code cause != null}との組み合わせで使用してください。</p>
         * 
         * @return HTTPトランザクションが進行中の場合true、それ以外はfalse
         */
        public boolean hasValidRequest()
        {
            return captureHolder.getCurrentRequest().getUri() != null;
        }
        
        @Override
        public void onOpened(Connection connection)
        {
            Request request = connectContext.getRequest();
            String uri = request != null ? request.getHttpURI().toString() : "unknown";
            log.debug("Downstream connection opened with HttpClient: {} for URI: {}", connection, uri);
            
            // HttpClient can track this connection if needed
            // For example, register connection metrics, connection pool management, etc.
        }
        
        @Override
        public void onClosed(Connection connection)
        {
            // 接続のクローズを記録
            // clientDisconnectedEarlyフラグは、DownstreamConnection.onClose(cause)で
            // cause != nullの場合のみ設定される（異常終了のみ）
            String connectionType = connection instanceof ReverseConnectHandler.DownstreamConnection 
                ? "downstream" : "upstream";
            log.debug("Connection closed ({}): {}", connectionType, connection);
            
            // HttpClient cleanup if needed
            // For example, release connection resources, update connection pool, etc.
        }
        
        /**
         * Called when HTTP request/response completes successfully.
         * This method efficiently processes HTTP transaction data from CaptureHolder2.
         */
        public void onSuccess()
        {
            // Get current transaction from CaptureHolder2 for early checks
            CaptureHolder2.HttpRequest httpRequest = captureHolder.getCurrentRequest();
            
            // クライアントが早期切断（異常終了）した場合は、すべての処理をスキップ
            // clientDisconnectedEarlyは、DownstreamConnection.onClose(cause != null)で設定される
            if (clientDisconnectedEarly)
            {
                log.debug("Skipping HTTP transaction processing for {} {} due to client early disconnection", 
                    httpRequest.getMethod(), httpRequest.getUri());
                return;
            }
            
            // HTTPリクエストが受信されていない場合（TLSハンドシェイクのみ、または接続クローズ）はスキップ
            if (httpRequest.getUri() == null)
            {
                return;
            }
            
            log.debug("HTTP transaction completed: {} {}", httpRequest.getMethod(), httpRequest.getUri());
            
            try
            {
                // Get current transaction from CaptureHolder2
                CaptureHolder2 holder = this.captureHolder;
                if (holder != null)
                {
                    CaptureHolder2.HttpTransaction transaction = holder.getCurrentTransaction();
                    CaptureHolder2.HttpResponse httpResponse = transaction.getResponse();
                    
                    // Check if we have meaningful data
                    if (httpRequest.getMethod() == null && httpResponse.getStatus() == 0)
                    {
                        log.debug("No HTTP data to process (empty transaction)");
                        return;
                    }
                    
                    // Content-Lengthとの不一致チェック（圧縮後のデータサイズで比較）
                    // ネットワーク障害やサーバー側の切断で不完全なデータを検出
                    // 非同期処理を開始する前に検証し、不完全なデータは早期にリジェクト
                    long expectedLength = httpResponse.getContentLength();
                    if (expectedLength > 0)
                    {
                        long actualLength = httpResponse.getBodySize();  // 圧縮後の受信バイト数
                        
                        if (actualLength != expectedLength)
                        {
                            log.warn("Content-Length mismatch detected - rejecting incomplete data: expected {} bytes, but received {} bytes ({} bytes missing, {}% received). ContentListenerSpi will not be invoked for request {} {}",
                                expectedLength, actualLength, expectedLength - actualLength, 
                                String.format("%.1f", (actualLength * 100.0 / expectedLength)),
                                httpRequest.getMethod(), httpRequest.getUri());
                            return;
                        }
                    }
                    
                    // リスナーが登録されているかチェック
                    // リスナーが空なら非同期処理を起動する必要なし
                    List<ContentListenerSpi> listeners = ReverseConnectHandler.getContentListeners();
                    if (listeners.isEmpty())
                    {
                        log.debug("No content listeners registered, skipping processing for {} {}", 
                            httpRequest.getMethod(), httpRequest.getUri());
                        return;
                    }
                    
                    // Create RequestMetaDataWrapper efficiently from CaptureHolder2.HttpRequest
                    RequestMetaDataWrapper req = new RequestMetaDataWrapper();
                    if (httpRequest.getMethod() != null)
                    {
                        // Use efficient set() method that copies all metadata at once
                        req.set(httpRequest);
                    }
                    else
                    {
                        // Fallback: Use minimal info if HttpRequest is incomplete
                        req.setMethod("UNKNOWN");
                        req.setRequestURI(httpRequest.getUri() != null ? httpRequest.getUri() : "unknown");
                    }
                    
                    // Create ResponseMetaDataWrapper efficiently from CaptureHolder2.HttpResponse
                    ResponseMetaDataWrapper res = new ResponseMetaDataWrapper();
                    if (httpResponse.getStatus() != 0)
                    {
                        // Use efficient set() method that copies all metadata at once
                        res.set(httpResponse);
                    }
                    else
                    {
                        // Fallback: Use Jetty Response if HttpResponse is incomplete
                        Response response = connectContext.getResponse();
                        if (response != null)
                        {
                            res.set(response);
                        }
                    }

                    log.debug("HTTP transaction data captured for processing: {} {} (req: {} bytes, res: {} bytes)", 
                        httpRequest.getMethod(), httpRequest.getUri(), 
                        httpRequest.getBodySize(), httpResponse.getBodySize());
                    
                    // Mark transaction as complete and prepare for next one (Keep-Alive support)
                    holder.completeTransaction();
                    
                    // Process captured data
                    // invoke()自体は軽量（リスナーループ + test() + clone()のみ）なので同期的に実行
                    // 各リスナーの実際の処理（JSONパース、ファイルI/Oなど）は invoke()内で非同期化される
                    this.invoke(req, res);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to process HTTP transaction data", e);
            }
        }
        
        public ConnectContext getConnectContext()
        {
            return connectContext;
        }
        
        public HttpClient getHttpClient()
        {
            return httpClient;
        }
        
        /**
         * Invoke content listeners with the captured request/response data.
         * This method efficiently processes data from CaptureHolder2.HttpTransaction.
         * 
         * @param baseReq the base request metadata
         * @param baseRes the base response metadata
         */
        private void invoke(RequestMetaDataWrapper baseReq, ResponseMetaDataWrapper baseRes)
        {
            // Get content listeners (validated in onSuccess(), guaranteed non-empty here)
            List<ContentListenerSpi> listeners = ReverseConnectHandler.getContentListeners();
            
            log.debug("Processing request: {}", baseReq.getRequestURI());
            
            // Process each listener
            for (ContentListenerSpi listener : listeners)
            {
                // listener.test()の保護（ユーザー実装の例外をキャッチ）
                // test()の最適化: cloneを省略してbaseReqを直接使用
                // 注意: test()内でgetParameterMap()を取得して変更すると、後続リスナーに影響する可能性がある
                // （既存実装のAPIListener/ImageListenerはgetRequestURI()のみ使用するため現時点では問題なし）
                boolean isInterested;
                try
                {
                    isInterested = listener.test(baseReq);
                }
                catch (Exception e)
                {
                    log.warn("Listener {} failed during test() - skipping this listener", 
                        listener.getClass().getSimpleName(), e);
                    continue;
                }
                
                if (!isInterested)
                {
                    log.debug("Listener {} not interested in request {}", 
                        listener.getClass().getSimpleName(), baseReq.getRequestURI());
                    continue;
                }
                
                // accept()用にclone（並列実行の安全性確保）
                // clone()は内部でCloneNotSupportedExceptionを処理済みなのでtry不要
                RequestMetaDataWrapper req = baseReq.clone();
                ResponseMetaDataWrapper res = baseRes.clone();
                
                // Process listener asynchronously
                Runnable task = () -> {
                    try
                    {
                        log.debug("Processing request {} with listener {}", 
                            req.getRequestURI(), listener.getClass().getSimpleName());
                        
                        listener.accept(req, res);
                        
                        log.debug("Successfully processed request {} with listener {}", 
                            req.getRequestURI(), listener.getClass().getSimpleName());
                    }
                    catch (Exception e)
                    {
                        log.warn("Content listener {} failed to process request", 
                            listener.getClass().getSimpleName(), e);
                    }
                };
                
                // Virtual Thread Executorで非同期実行
                // Virtual Threadsは常に利用可能なため、null/shutdownチェック不要
                try
                {
                    ThreadManager.getExecutorService().submit(task);
                }
                catch (RejectedExecutionException e)
                {
                    // アプリケーション終了時のみ発生（稀）
                    log.debug("Listener {} processing rejected - application is shutting down", 
                        listener.getClass().getSimpleName());
                }
            }
        }
    }
    
    /**
     * Example Connection.Listener implementation for monitoring downstream connections.
     * You can extend this class or create your own implementation.
     */
    public static class DownstreamConnectionListener implements Connection.Listener
    {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DownstreamConnectionListener.class);
        
        @Override
        public void onOpened(Connection connection)
        {
            log.debug("Downstream connection opened: {}", connection);
        }
        
        @Override
        public void onClosed(Connection connection)
        {
            log.debug("Downstream connection closed: {}", connection);
        }
    }

    private void upgradeConnection(Request request, Connection connection)
    {
        // Set the new connection as request attribute so that
        // Jetty understands that it has to upgrade the connection.
        request.setAttribute(HttpStream.UPGRADE_CONNECTION_ATTRIBUTE, connection);
        log.debug("Upgraded connection to {}", connection);
    }

    /**
     * <p>Reads (with non-blocking semantic) into the given {@code buffer} from the given {@code endPoint}.</p>
     *
     * @param endPoint the endPoint to read from
     * @param buffer the buffer to read data into
     * @param context the context information related to the connection
     * @return the number of bytes read (possibly 0 since the read is non-blocking)
     * or -1 if the channel has been closed remotely
     * @throws IOException if the endPoint cannot be read
     */
    protected int read(EndPoint endPoint, ByteBuffer buffer, ConcurrentMap<String, Object> context) throws IOException
    {
        return endPoint.fill(buffer);
    }

    /**
     * <p>Writes (with non-blocking semantic) the given buffer of data onto the given endPoint.</p>
     *
     * @param endPoint the endPoint to write to
     * @param buffer the buffer to write
     * @param callback the completion callback to invoke
     * @param context the context information related to the connection
     */
    protected void write(EndPoint endPoint, ByteBuffer buffer, Callback callback, ConcurrentMap<String, Object> context)
    {
        endPoint.write(callback, buffer);
    }

    public Set<String> getWhiteListHosts()
    {
        return whiteList;
    }

    public Set<String> getBlackListHosts()
    {
        return blackList;
    }

    /**
     * SSL証明書によるホスト検証を行う。
     * SslContextFactoryにロードされた証明書に対して、指定されたホストが一致するか確認する。
     * 
     * @param serverAddress サーバーアドレス（"host:port"形式）
     * @return ホストが証明書に一致する場合true、それ以外はfalse
     */
    private boolean isHostAllowedBySslContext(String serverAddress)
    {
        // 証明書が読み込まれていない場合は検証不可のため拒否
        if (sslContextFactoryServer == null)
        {
            return false;
        }

        // サーバーアドレスからホスト名を抽出
        HostPort hostPort = new HostPort(serverAddress);
        String host = hostPort.getHost();
            
        if (host == null)
        {
            return false;
        }
            
        String h = host.toLowerCase();
            
        try
        {
            // SslContextFactoryから証明書エイリアスを取得し、各証明書に対してホスト名をチェック
            Set<String> aliases = sslContextFactoryServer.getAliases();
            for (String alias : aliases)
            {
                X509 x509 = sslContextFactoryServer.getX509(alias);
                if (x509 != null && x509.matches(h))
                {
                    log.debug("ホスト {} は証明書エイリアス {} に一致します", h, alias);
                    return true;
                }
            }
            
            log.debug("ホスト {} はいずれの証明書にも一致しません", h);
            
            return false;
        }
        catch (Exception e)
        {
            log.warn("ホスト {} の証明書検証中にエラーが発生しました", serverAddress, e);
            return false; // エラー時は拒否
        }
    }

    /**
     * Checks the given {@code host} and {@code port} against whitelist and blacklist.
     *
     * @param host the host to check
     * @param port the port to check
     * @return true if it is allowed to connect to the given host and port
     */
    public boolean validateDestination(String host, int port)
    {
        String hostPort = host + ":" + port;
        if (!whiteList.isEmpty())
        {
            if (!whiteList.contains(hostPort))
            {
                log.debug("Host {}:{} not whitelisted", host, port);
                return false;
            }
        }
        if (!blackList.isEmpty())
        {
            if (blackList.contains(hostPort))
            {
                log.debug("Host {}:{} blacklisted", host, port);
                return false;
            }
        }
        return true;
    }
    
    protected class ConnectManager extends SelectorManager
    {
        protected ConnectManager(Executor executor, Scheduler scheduler, int selectors)
        {
            super(executor, scheduler, selectors);
        }

        @Override
        protected EndPoint newEndPoint(SelectableChannel channel, ManagedSelector selector, SelectionKey key)
        {
            SocketChannelEndPoint endPoint = new SocketChannelEndPoint((SocketChannel)channel, selector, key, getScheduler());
            endPoint.setIdleTimeout(getIdleTimeout());
            return endPoint;
        }

        @Override
        public Connection newConnection(SelectableChannel channel, EndPoint endpoint, Object attachment) throws IOException
        {
            ReverseConnectHandler.log.debug("Connected to {}", ((SocketChannel)channel).getRemoteAddress());
            ConnectContext connectContext = (ConnectContext)attachment;
            
            // Create upstream connection (potentially SSL-wrapped)
            UpstreamConnection connection = newUpstreamConnection(endpoint, connectContext);
            connection.setInputBufferSize(getBufferSize());
            
            // If the endpoint is SSL-wrapped, we need to handle the SSL connection lifecycle
            if (endpoint instanceof SslConnection.SslEndPoint sslEndPoint)
            {
                // Get the parent SslConnection and ensure it's opened
                SslConnection sslConnection = sslEndPoint.getSslConnection();
                
                // The SslConnection itself needs to be opened
                sslConnection.onOpen();
                
                ReverseConnectHandler.log.debug("SSL connection established for upstream");
            }
            
            return connection;
        }

        @Override
        protected void connectionFailed(SelectableChannel channel, final Throwable ex, final Object attachment)
        {
            close(channel);
            ConnectContext connectContext = (ConnectContext)attachment;
            onConnectFailure(connectContext.request, connectContext.response, connectContext.callback, ex);
        }
    }

    public static class ConnectContext
    {
        private final ConcurrentMap<String, Object> context = new ConcurrentHashMap<>();
        private final Request request;
        private final Response response;
        private final Callback callback;
        private final EndPoint endPoint;

        public ConnectContext(Request request, Response response, Callback callback, EndPoint endPoint)
        {
            this.request = request;
            this.response = response;
            this.callback = callback;
            this.endPoint = endPoint;
        }

        public ConcurrentMap<String, Object> getContext()
        {
            return context;
        }

        public Request getRequest()
        {
            return request;
        }

        public Response getResponse()
        {
            return response;
        }

        public Callback getCallback()
        {
            return callback;
        }

        public EndPoint getEndPoint()
        {
            return endPoint;
        }

    }

    public class UpstreamConnection extends TunnelConnection
    {
        private final ConnectContext connectContext;
        private HttpClientConnectionListener httpClientListener;
        private HttpParser httpParser;
        private boolean parseHttpResponse = true;  // HTTPレスポンスを常にパース

        public UpstreamConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, ConnectContext connectContext)
        {
            // ConnectContextのcontextを直接共有（コピー不要）
            super(endPoint, executor, bufferPool, connectContext.getContext());
            this.connectContext = connectContext;
        }

        @Override
        public void onOpen()
        {
            super.onOpen();
            onConnectSuccess(connectContext, this);
            fillInterested();
        }
        
        /**
         * Enable or disable HTTP response parsing for upstream connection.
         * <p>When enabled, HTTP responses from the server will be parsed and notified to listeners.</p>
         * 
         * @param enable true to enable HTTP parsing, false to disable
         */
        public void setParseHttpResponse(boolean enable)
        {
            this.parseHttpResponse = enable;
            if (enable && httpParser == null)
            {
                // Create HTTP parser for response parsing
                httpParser = new HttpParser(new ResponseParserHandler());
                log.debug("HTTP response parser enabled for upstream connection {}", this);
            }
        }
        
        /**
         * Set the connection listener to track HTTP events.
         * 
         * @param listener the listener to set
         */
        public void setHttpClientListener(HttpClientConnectionListener listener)
        {
            this.httpClientListener = listener;
            log.debug("Set HTTP client listener {} to {}", listener, this);
        }
        
        /**
         * Notify the HttpClientConnectionListener of content received.
         * Content is stored directly in CaptureHolder2 for efficiency.
         */
        private void notifyContentListeners(ByteBuffer buffer, int offset, int length)
        {
            if (httpClientListener == null)
            {
                return;
            }
            
            // Extract bytes from buffer for storing in CaptureHolder2
            byte[] bytes;
            if (buffer.hasArray())
            {
                // Buffer has backing array
                bytes = Arrays.copyOfRange(buffer.array(), buffer.arrayOffset() + offset, buffer.arrayOffset() + offset + length);
            }
            else
            {
                // Direct buffer - need to read manually
                int savedPosition = buffer.position();
                buffer.position(offset);
                bytes = new byte[length];
                buffer.get(bytes);
                buffer.position(savedPosition);
            }
            
            // Store response body directly in CaptureHolder2
            httpClientListener.getCaptureHolder().getCurrentResponse().addBodyChunk(bytes);
        }
        
        /**
         * Notify the HttpClientConnectionListener of successful completion.
         */
        private void notifySuccessListeners()
        {
            if (httpClientListener != null)
            {
                try
                {
                    httpClientListener.onSuccess();
                }
                catch (Exception e)
                {
                    log.debug("Error notifying listener of success", e);
                }
            }
        }
        
        /**
         * HttpParser.ResponseHandler implementation for parsing HTTP responses from upstream server.
         * This handler directly populates CaptureHolder2 with response metadata and body.
         */
        private class ResponseParserHandler implements HttpParser.ResponseHandler
        {
            @Override
            public void startResponse(HttpVersion version, int status, String reason)
            {
                log.debug("HTTP Response: {} {} {}", version, status, reason);
                
                // Store response status line directly in CaptureHolder2
                if (httpClientListener != null)
                {
                    httpClientListener.getCaptureHolder().getCurrentResponse().setStatusLine(
                        version.toString(), status, reason);
                }
            }
            
            @Override
            public void parsedHeader(HttpField field)
            {
                log.debug("HTTP Response Header: {}: {}", field.getName(), field.getValue());
                
                // Store header directly in CaptureHolder2
                if (httpClientListener != null)
                {
                    httpClientListener.getCaptureHolder().getCurrentResponse().addHeader(
                        field.getName(), field.getValue());
                }
            }
            
            @Override
            public boolean headerComplete()
            {
                log.debug("HTTP Response Headers complete");
                return false;
            }
            
            @Override
            public boolean content(ByteBuffer buffer)
            {
                int length = buffer.remaining();
                log.debug("HTTP Response Content: {} bytes", length);
                
                // Notify listeners and store content in CaptureHolder2
                notifyContentListeners(buffer, buffer.position(), length);
                return false;
            }
            
            @Override
            public boolean contentComplete()
            {
                log.debug("HTTP Response Content complete");
                return false;
            }
            
            @Override
            public void parsedTrailer(HttpField field)
            {
                log.debug("HTTP Response Trailer: {}", field);
            }
            
            @Override
            public boolean messageComplete()
            {
                log.debug("HTTP Response Message complete");
                
                // Notify success listeners when HTTP response is complete
                notifySuccessListeners();
                
                return false;
            }
            
            @Override
            public void badMessage(HttpException failure)
            {
                log.warn("Bad HTTP response message: {}", failure != null ? failure.toString() : "unknown");
            }
            
            @Override
            public void earlyEOF()
            {
                log.warn("Early EOF while parsing HTTP response");
            }
        }

        @Override
        protected int read(EndPoint endPoint, ByteBuffer buffer) throws IOException
        {
            // SslEndPoint automatically handles decryption if SSL is enabled
            // Save buffer state before reading
            int positionBefore = buffer.position();
            
            int read = ReverseConnectHandler.this.read(endPoint, buffer, getContext());
            
            if (read > 0)
            {
                // If HTTP parsing is enabled, parse the buffer
                if (parseHttpResponse && httpParser != null)
                {
                    try
                    {
                        // Reset parser if it's in END state (previous message completed)
                        if (httpParser.isComplete())
                        {
                            log.debug("Resetting HTTP response parser for new message");
                            httpParser.reset();
                        }
                        
                        // Save current position
                        int positionAfter = buffer.position();
                        
                        // Create a read-only view for parsing (to avoid modifying buffer state)
                        buffer.position(positionBefore);
                        ByteBuffer parseBuffer = buffer.slice();
                        parseBuffer.limit(read);
                        
                        // Parse HTTP response
                        httpParser.parseNext(parseBuffer);
                        
                        // Restore buffer position
                        buffer.position(positionAfter);
                    }
                    catch (Exception e)
                    {
                        log.debug("Failed to parse HTTP response", e);
                    }
                }
                else
                {
                    // In tunnel mode, just notify content listeners if any
                    if (httpClientListener != null)
                    {
                        notifyContentListeners(buffer, positionBefore, read);
                    }
                }
                
                if (log.isTraceEnabled())
                {
                    try
                    {
                        // Current buffer position after read
                        int positionAfter = buffer.position();
                        
                        // Read buffer content as string (from the data just read)
                        // Data is between positionBefore and positionAfter
                        buffer.position(positionBefore);
                        byte[] bytes = new byte[read];
                        buffer.get(bytes);
                        String content = new String(bytes, StandardCharsets.UTF_8);
                        
                        // Restore buffer position to after read
                        buffer.position(positionAfter);
                        
                        log.trace("Read {} bytes from server (upstream) {}: [{}]", read, this, content);
                    }
                    catch (Exception e)
                    {
                        log.trace("Failed to log buffer content", e);
                    }
                }
            }
            return read;
        }

        @Override
        protected void write(EndPoint endPoint, ByteBuffer buffer, Callback callback)
        {
            // SslEndPoint automatically handles encryption if SSL is enabled
            if (log.isTraceEnabled() && buffer.hasRemaining())
            {
                int remaining = buffer.remaining();
                
                // Save current buffer position
                int position = buffer.position();
                int limit = buffer.limit();
                
                // Read buffer content as string
                byte[] bytes = new byte[remaining];
                buffer.get(bytes);
                String content = new String(bytes, StandardCharsets.UTF_8);
                
                // Restore buffer position
                buffer.position(position);
                buffer.limit(limit);
                
                log.trace("Writing {} bytes to server (upstream) {}: [{}]", remaining, this, content);
            }
            ReverseConnectHandler.this.write(endPoint, buffer, callback, getContext());
        }
        
        @Override
        public void onClose(Throwable cause)
        {
            // 正常終了（cause == null）かつHTTPリクエストが受信されている場合、
            // successリスナーを通知してHTTPトランザクションを完了させる
            // これはHTTPパーサーがmessageComplete()を呼ばなかった場合のフォールバック処理
            // 通常は、ResponseParserHandler.messageComplete()で処理済みのため、
            // onSuccess()内でcurrentRequestURIがnullにクリアされており、重複は発生しない
            if (cause == null && httpClientListener != null)
            {
                if (httpClientListener.hasValidRequest())
                {
                    log.debug("Fallback: notifying success listeners on UpstreamConnection close (HTTP parser may not have completed)");
                    notifySuccessListeners();
                }
                else
                {
                    log.debug("Skipping notifySuccessListeners() on UpstreamConnection close - no valid HTTP request received");
                }
            }
            // 異常終了（cause != null）の場合は、DownstreamConnection.onClose()で
            // clientDisconnectedEarlyフラグが既に設定されているため、何もしない
            
            // Cleanup HTTP parser if present (正常・異常どちらでも必ず実行)
            if (httpParser != null)
            {
                try
                {
                    httpParser.close();
                }
                catch (Exception e)
                {
                    log.debug("Error closing HTTP parser", e);
                }
            }
            
            super.onClose(cause);
        }
    }

    public class DownstreamConnection extends TunnelConnection implements Connection.UpgradeTo
    {
        private ByteBuffer buffer;
        private HttpClientConnectionListener httpClientListener;
        private HttpParser httpParser;
        private boolean parseHttpRequest = false;

        public DownstreamConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, ConcurrentMap<String, Object> context)
        {
            super(endPoint, executor, bufferPool, context);
        }
        
        /**
         * Add a Connection.Listener to this connection.
         * The listener will be notified of connection lifecycle events.
         * 
         * @param listener the listener to add
         */
        public void addConnectionListener(Connection.Listener listener)
        {
            addEventListener(listener);
            log.debug("Added connection listener {} to {}", listener, this);
        }
        
        /**
         * Remove a Connection.Listener from this connection.
         * 
         * @param listener the listener to remove
         */
        public void removeConnectionListener(Connection.Listener listener)
        {
            removeEventListener(listener);
            log.debug("Removed connection listener {} from {}", listener, this);
        }
        
        /**
         * Enable or disable HTTP request parsing for downstream connection.
         * <p>When enabled, HTTP requests from the client will be parsed and stored in CaptureHolder.</p>
         * 
         * @param enable true to enable HTTP parsing, false to disable
         */
        public void setParseHttpRequest(boolean enable)
        {
            this.parseHttpRequest = enable;
            
            if (enable && httpParser == null)
            {
                // HTTPリクエストパーサーを作成
                httpParser = new HttpParser(new RequestParserHandler());
                log.debug("HTTP request parser enabled for downstream connection");
            }
        }
        
        /**
         * Set the connection listener to track HTTP events.
         * 
         * @param listener the listener to set
         */
        public void setHttpClientListener(HttpClientConnectionListener listener)
        {
            this.httpClientListener = listener;
            log.debug("Set HTTP client listener {} to {}", listener, this);
        }
        
        /**
         * Notify the HttpClientConnectionListener of content received.
         * Content is stored directly in CaptureHolder2 for efficiency.
         */
        private void notifyContentListeners(ByteBuffer buffer, int offset, int length)
        {
            if (httpClientListener == null)
            {
                return;
            }
            
            // Extract bytes from buffer for storing in CaptureHolder2
            byte[] bytes;
            if (buffer.hasArray())
            {
                // Buffer has backing array
                bytes = Arrays.copyOfRange(buffer.array(), buffer.arrayOffset() + offset, buffer.arrayOffset() + offset + length);
            }
            else
            {
                // Direct buffer - need to read manually
                int savedPosition = buffer.position();
                buffer.position(offset);
                bytes = new byte[length];
                buffer.get(bytes);
                buffer.position(savedPosition);
            }
            
            // Store request body directly in CaptureHolder2
            httpClientListener.getCaptureHolder().getCurrentRequest().addBodyChunk(bytes);
            
            log.debug("Captured {} bytes of HTTP request content", length);
        }
        
        /**
         * HttpParser.RequestHandler implementation for parsing HTTP requests from client.
         * This handler directly populates CaptureHolder2 with request metadata and body.
         */
        private class RequestParserHandler implements HttpParser.RequestHandler
        {
            @Override
            public void startRequest(String method, String uri, HttpVersion version)
            {
                log.debug("HTTP Request: {} {} {}", method, uri, version);
                
                // CaptureHolder2にリクエスト行を直接保存
                if (httpClientListener != null)
                {
                    httpClientListener.getCaptureHolder().getCurrentRequest().setRequestLine(
                        method, uri, version.toString());
                }
            }
            
            @Override
            public void parsedHeader(HttpField field)
            {
                log.debug("HTTP Request Header: {}: {}", field.getName(), field.getValue());
                
                // Store header directly in CaptureHolder2
                if (httpClientListener != null)
                {
                    httpClientListener.getCaptureHolder().getCurrentRequest().addHeader(
                        field.getName(), field.getValue());
                }
            }
            
            @Override
            public boolean headerComplete()
            {
                log.debug("HTTP Request Headers complete");
                return false;
            }
            
            @Override
            public boolean content(ByteBuffer buffer)
            {
                int length = buffer.remaining();
                log.debug("HTTP Request Content: {} bytes", length);
                
                // Store request body in CaptureHolder2 via listeners
                notifyContentListeners(buffer, buffer.position(), length);
                return false;
            }
            
            @Override
            public boolean contentComplete()
            {
                log.debug("HTTP Request Content complete");
                return false;
            }
            
            @Override
            public void parsedTrailer(HttpField field)
            {
                log.debug("HTTP Request Trailer: {}: {}", field.getName(), field.getValue());
            }
            
            @Override
            public boolean messageComplete()
            {
                if (httpClientListener != null)
                {
                    var req = httpClientListener.getCaptureHolder().getCurrentRequest();
                    log.debug("HTTP Request Message complete: {} {}", req.getMethod(), req.getUri());
                }
                
                return false;
            }
            
            @Override
            public void badMessage(HttpException failure)
            {
                log.warn("Bad HTTP request message: {}", failure != null ? failure.toString() : "unknown");
            }
            
            @Override
            public void earlyEOF()
            {
                log.warn("Early EOF while parsing HTTP request");
            }
        }

        @Override
        public void onUpgradeTo(ByteBuffer buffer)
        {
            this.buffer = buffer;
        }

        @Override
        public void onOpen()
        {
            super.onOpen();

            // Check if this is an SSL endpoint that needs handshake
            EndPoint endPoint = getEndPoint();
            if (endPoint instanceof SslConnection.SslEndPoint)
            {
                log.debug("Downstream SSL connection opened, waiting for SSL handshake from client");
                
                // SSL endpoint will handle handshake automatically when we register interest
                // The SslConnection is already opened through the connection hierarchy
                // Just register interest in reading to start the SSL handshake
                fillInterested();
                return;
            }

            if (buffer == null)
            {
                fillInterested();
                return;
            }

            // HTTPパース機能が有効な場合、初期バッファをパース
            if (parseHttpRequest && httpParser != null && buffer.hasRemaining())
            {
                try
                {
                    // パーサーがEND状態の場合、リセット
                    if (httpParser.isComplete())
                    {
                        httpParser.reset();
                    }
                    
                    // 初期HTTPリクエストをパース
                    httpParser.parseNext(buffer.duplicate());
                    
                    log.debug("Parsed initial HTTP request buffer: {} bytes", buffer.remaining());
                }
                catch (Exception e)
                {
                    log.debug("Failed to parse initial HTTP request", e);
                }
            }

            int remaining = buffer.remaining();
            write(getConnection().getEndPoint(), buffer, new Callback()
            {
                @Override
                public void succeeded()
                {
                    buffer = null;
                    log.debug("Wrote initial {} bytes to server {}", remaining, DownstreamConnection.this);
                    fillInterested();
                }

                @Override
                public void failed(Throwable x)
                {
                    buffer = null;
                    log.debug("Failed to write initial {} bytes to server {}", remaining, DownstreamConnection.this, x);
                    close();
                    getConnection().close();
                }
            });
        }

        @Override
        protected int read(EndPoint endPoint, ByteBuffer buffer) throws IOException
        {
            // Save buffer position BEFORE reading
            int positionBefore = buffer.position();
            
            int read = ReverseConnectHandler.this.read(endPoint, buffer, getContext());
            
            if (read > 0)
            {
                // HTTPパース機能が有効な場合、バッファをパース
                if (parseHttpRequest && httpParser != null)
        {
            try
            {
                        // パーサーがEND状態の場合（前のメッセージ完了済み）、リセット
                        if (httpParser.isComplete())
                        {
                            log.debug("Resetting HTTP request parser for new message");
                            httpParser.reset();
                        }
                        
                        // 現在位置を保存
                        int positionAfter = buffer.position();
                        
                        // パース用の読み取り専用ビューを作成（バッファ状態を変更しないため）
                        buffer.position(positionBefore);
                        ByteBuffer parseBuffer = buffer.slice();
                        parseBuffer.limit(read);
                        
                        // HTTPリクエストをパース
                        httpParser.parseNext(parseBuffer);
                        
                        // バッファ位置を復元
                        buffer.position(positionAfter);
            }
            catch (Exception e)
            {
                        log.debug("Failed to parse HTTP request", e);
                    }
                }
                else
                {
                    // トンネルモード: リスナーがあればコンテンツを通知
                    if (httpClientListener != null)
                    {
                        notifyContentListeners(buffer, positionBefore, read);
                    }
                }
                
            if (log.isTraceEnabled())
                {
                    try
                    {
                        // Current buffer position after read
                        int positionAfter = buffer.position();
                        
                        // Read buffer content as string (from the data just read)
                        buffer.position(positionBefore);
                        byte[] bytes = new byte[read];
                        buffer.get(bytes);
                        String content = new String(bytes, StandardCharsets.UTF_8);
                        
                        // Restore buffer position to after read
                        buffer.position(positionAfter);
                        
                        log.trace("Read {} bytes from client {}: [{}]", read, this, content);
                    }
                    catch (Exception e)
                    {
                        log.trace("Failed to log buffer content", e);
                    }
                }
            }
            return read;
        }

        @Override
        protected void write(EndPoint endPoint, ByteBuffer buffer, Callback callback)
        {
            if (log.isTraceEnabled() && buffer.hasRemaining())
            {
                int remaining = buffer.remaining();
                
                // Save current buffer position
                int position = buffer.position();
                int limit = buffer.limit();
                
                // Read buffer content as string
                byte[] bytes = new byte[remaining];
                buffer.get(bytes);
                String content = new String(bytes, StandardCharsets.UTF_8);
                
                // Restore buffer position
                buffer.position(position);
                buffer.limit(limit);
                
                log.trace("Writing {} bytes to server {}: [{}]", remaining, this, content);
            }
            ReverseConnectHandler.this.write(endPoint, buffer, callback, getContext());
        }
        
        @Override
        public void onClose(Throwable cause)
        {
            // クライアントが異常切断した場合（cause != null）、リスナーに通知
            // 正常終了（cause == null）の場合は何もしない
            //
            // hasValidRequest()で「HTTPトランザクション進行中」かどうかを判定
            // cause != null と組み合わせることで、
            // 「HTTPトランザクション進行中に異常終了した」ことを正確に検知
            //
            // 重要：hasValidRequest()単独では異常を検知できない
            // - HTTPトランザクション進行中 && 正常動作中 → hasValidRequest() = true
            // - HTTPトランザクション進行中 && 異常終了 → hasValidRequest() = true && cause != null ★これを検知
            // - TLS接続のみ（HTTPなし） && 異常終了 → hasValidRequest() = false && cause != null（フラグ設定不要）
            if (cause != null && httpClientListener != null)
            {
                if (httpClientListener.hasValidRequest())
                {
                    var req = httpClientListener.getCaptureHolder().getCurrentRequest();
                    log.debug("Client (downstream) disconnected with error during HTTP transaction: {} {}", 
                            req.getMethod(), req.getUri());
                    httpClientListener.clientDisconnectedEarly = true;
                }
            }
            
            // Cleanup HTTP parser if present (正常・異常どちらでも必ず実行)
            if (httpParser != null)
            {
                try
                {
                    httpParser.close();
                }
                catch (Exception e)
                {
                    log.debug("Error closing HTTP parser", e);
                }
            }
            
            super.onClose(cause);
        }
    }

    private abstract static class TunnelConnection extends AbstractConnection.NonBlocking
    {
        private final IteratingCallback pipe = new ProxyIteratingCallback();
        private final ByteBufferPool bufferPool;
        private final ConcurrentMap<String, Object> context;
        private TunnelConnection connection;

        protected TunnelConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, ConcurrentMap<String, Object> context)
        {
            super(endPoint, executor);
            this.bufferPool = bufferPool;
            this.context = context;
        }

        public ByteBufferPool getByteBufferPool()
        {
            return bufferPool;
        }

        public ConcurrentMap<String, Object> getContext()
        {
            return context;
        }

        public Connection getConnection()
        {
            return connection;
        }

        public void setConnection(TunnelConnection connection)
        {
            this.connection = connection;
        }

        @Override
        public void onFillable()
        {
            pipe.iterate();
        }

        protected abstract int read(EndPoint endPoint, ByteBuffer buffer) throws IOException;

        protected abstract void write(EndPoint endPoint, ByteBuffer buffer, Callback callback);

        protected void close(Throwable failure)
        {
            getEndPoint().close(failure);
        }

        @Override
        public String toConnectionString()
        {
            EndPoint endPoint = getEndPoint();
            return String.format("%s@%x[l:%s<=>r:%s]",
                TypeUtil.toShortName(getClass()),
                hashCode(),
                endPoint.getLocalSocketAddress(),
                endPoint.getRemoteSocketAddress());
        }

        private class ProxyIteratingCallback extends IteratingCallback
        {
            private RetainableByteBuffer buffer;
            private int filled;

            @Override
            protected Action process()
            {
                buffer = bufferPool.acquire(getInputBufferSize(), true);
                ByteBuffer byteBuffer = buffer.getByteBuffer();
                int filled;
                
                // read()を実行
                // SSL handshakeエラーは、SslConnection.SslHandshakeListenerで既に処理済み
                try
                {
                    filled = this.filled = read(getEndPoint(), byteBuffer);
                }
                catch (IOException x)
                {
                    // read()でのIO例外（SSL handshakeエラーを含む）
                    log.debug("Could not fill {}", TunnelConnection.this, x);
                    buffer.release();
                    disconnect(x);
                    return Action.SUCCEEDED;
                }
                
                // read()成功後の処理
                // 注: write(), fillInterested(), shutdownOutput()は例外をスローせず、
                //     エラーはonFailure()コールバックで通知される
                if (filled > 0)
                {
                    write(connection.getEndPoint(), byteBuffer, this);
                    return Action.SCHEDULED;
                }

                buffer = Retainable.release(buffer);

                if (filled == 0)
                {
                    fillInterested(this);
                    return Action.SCHEDULED;
                }

                connection.getEndPoint().shutdownOutput();
                return Action.SUCCEEDED;
            }

            @Override
            protected void onSuccess()
            {
                log.debug("Wrote {} bytes {}", filled, TunnelConnection.this);
                buffer = Retainable.release(buffer);
            }

            @Override
            protected void onFailure(Throwable x)
            {
                // write()コールバック失敗時のエラー処理
                // Java 21のpattern matchingでより簡潔に例外タイプを処理
                switch (x)
                {
                    case TimeoutException e -> 
                        log.debug("Connection timeout while writing {} bytes {}: {}", 
                            filled, TunnelConnection.this, e.getMessage());
                    case ClosedChannelException e -> 
                        log.debug("Connection closed while writing {} bytes {}", 
                            filled, TunnelConnection.this);
                    case EofException e -> 
                        log.debug("Connection closed while writing {} bytes {}", filled, TunnelConnection.this);
                    default -> 
                        log.debug("Failed to write {} bytes {}", filled, TunnelConnection.this, x);
                }
                disconnect(x);
            }

            @Override
            protected void onCompleteFailure(Throwable cause)
            {
                buffer = Retainable.release(buffer);
            }

            @Override
            public InvocationType getInvocationType()
            {
                return InvocationType.NON_BLOCKING;
            }

            private void disconnect(Throwable x)
            {
                TunnelConnection.this.close(x);
                connection.close(x);
            }
        }
    }
    
    /**
     * リクエストメタデータのラッパークラス。完全なHTTPヘッダーサポート付き。
     * Jetty Core 12 API用に設計され、CaptureHolder2と統合される。
     */
    static class RequestMetaDataWrapper implements RequestMetaData, Cloneable
    {
        private String contentType;
        private String method;
        private String requestURI;
        private String queryString;
        private Map<String, String> headers = new LinkedHashMap<>();
        private byte[] requestBodyBytes = null;  // Store as byte array, not InputStream
        private Map<String, List<String>> parameterMap = null;  // Lazy-initialized parameter map

        /**
         * Initialize from CaptureHolder2.HttpRequest.
         * This is the primary and most efficient initialization method.
         */
        void set(CaptureHolder2.HttpRequest httpRequest)
        {
            this.method = httpRequest.getMethod();
            this.requestURI = httpRequest.getUri();
            this.headers = new LinkedHashMap<>(httpRequest.getHeaders());
            this.contentType = httpRequest.getContentType();
            
            // Extract query string from URI
            if (requestURI != null && requestURI.contains("?"))
            {
                int queryIndex = requestURI.indexOf('?');
                this.queryString = requestURI.substring(queryIndex + 1);
            }
            
            // Store body as byte array (not InputStream) for thread-safe reuse
            if (httpRequest.getBodySize() > 0)
            {
                this.requestBodyBytes = httpRequest.getBodyAsBytes();
            }
        }

        /**
         * Legacy initialization from Jetty Request (for CONNECT request metadata).
         * Less efficient than set(CaptureHolder2.HttpRequest).
         */
        void set(Request req)
        {
            this.contentType = req.getHeaders().get(HttpHeader.CONTENT_TYPE);
            this.method = req.getMethod();
            this.requestURI = req.getHttpURI().getPath();
            this.queryString = req.getHttpURI().getQuery();
            
            // Copy headers
            req.getHeaders().forEach(field -> 
                headers.put(field.getName(), field.getValue())
            );
        }
        
        /**
         * Set body from InputStream (legacy support).
         * Converts to byte array for thread-safe reuse.
         */
        void set(InputStream body) throws IOException
        {
            if (body != null)
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = body.read(buffer)) != -1)
                {
                    baos.write(buffer, 0, bytesRead);
                }
                this.requestBodyBytes = baos.toByteArray();
            }
        }
        
        void setMethod(String method)
        {
            this.method = method;
        }
        
        void setRequestURI(String requestURI)
        {
            this.requestURI = requestURI;
            
            // Extract query string from URI
            if (requestURI != null && requestURI.contains("?"))
            {
                int queryIndex = requestURI.indexOf('?');
                this.queryString = requestURI.substring(queryIndex + 1);
            }
        }
        
        void setContentType(String contentType)
        {
            this.contentType = contentType;
        }
        
        void addHeader(String name, String value)
        {
            headers.put(name, value);
        }
        
        /**
         * Get all HTTP headers.
         */
        public Map<String, String> getHeaders()
        {
            return Collections.unmodifiableMap(headers);
        }
        
        /**
         * Get a specific HTTP header value.
         */
        public String getHeader(String name)
        {
            return headers.get(name);
        }

        @Override
        public String getContentType()
        {
            return contentType;
        }

        @Override
        public String getMethod()
        {
            return method;
        }

        @Override
        public Map<String, List<String>> getParameterMap()
        {
            // Lazy initialization of parameter map
            if (parameterMap == null)
            {
                parameterMap = parseParameters();
            }
            return parameterMap;
        }
        
        /**
         * Parse URL parameters from query string and request body.
         */
        private Map<String, List<String>> parseParameters()
        {
            Map<String, List<String>> params = new LinkedHashMap<>();
            
            // Parse query string parameters
            if (queryString != null && !queryString.isEmpty())
            {
                parseParameterString(queryString, params);
            }
            
            // Parse POST body parameters (application/x-www-form-urlencoded)
            if ("POST".equalsIgnoreCase(method) && requestBodyBytes != null && requestBodyBytes.length > 0)
            {
                String bodyContentType = contentType != null ? contentType.toLowerCase() : "";
                if (bodyContentType.contains("application/x-www-form-urlencoded"))
                {
                    try
                    {
                        String bodyString = new String(requestBodyBytes, StandardCharsets.UTF_8);
                        parseParameterString(bodyString, params);
                    }
                    catch (Exception e)
                    {
                        log.warn("Failed to parse POST body parameters", e);
                    }
                }
            }
            
            return params;
        }
        
        /**
         * Parse URL-encoded parameter string into a parameter map.
         */
        private void parseParameterString(String paramString, Map<String, List<String>> params)
        {
            if (paramString == null || paramString.isEmpty())
            {
                return;
            }
            
            String[] pairs = paramString.split("&");
            for (String pair : pairs)
            {
                int idx = pair.indexOf('=');
                String key;
                String value;
                
                if (idx > 0)
                {
                    key = urlDecode(pair.substring(0, idx));
                    value = urlDecode(pair.substring(idx + 1));
                }
                else if (idx == 0)
                {
                    // =value (no key)
                    continue;
                }
                else
                {
                    // key with no value
                    key = urlDecode(pair);
                    value = "";
                }
                
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
        
        /**
         * URL decode a string.
         */
        private String urlDecode(String encoded)
        {
            try
            {
                return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            }
            catch (Exception e)
            {
                log.warn("Failed to URL decode: {}", encoded, e);
                return encoded;
            }
        }

        @Override
        public String getQueryString()
        {
            return queryString;
        }

        @Override
        public String getRequestURI()
        {
            return requestURI;
        }

        @Override
        public Optional<InputStream> getRequestBody()
        {
            // Return a new ByteArrayInputStream each time for thread-safe reuse
            if (requestBodyBytes != null && requestBodyBytes.length > 0)
            {
                return Optional.of(new ByteArrayInputStream(requestBodyBytes));
            }
            return Optional.empty();
        }
        
        @Override
        public RequestMetaDataWrapper clone()
        {
            try
            {
                RequestMetaDataWrapper copy = (RequestMetaDataWrapper) super.clone();
                // Deep copy headers map
                copy.headers = new LinkedHashMap<>(this.headers);
                // Byte array is immutable reference, no need to copy the array itself
                // (multiple clones can share the same byte array safely)
                // Parameter map is lazy-initialized, share the reference if already computed
                copy.parameterMap = this.parameterMap;
                return copy;
            }
            catch (CloneNotSupportedException e)
            {
                // This should never happen as we implement Cloneable
                RequestMetaDataWrapper copy = new RequestMetaDataWrapper();
                copy.contentType = this.contentType;
                copy.method = this.method;
                copy.requestURI = this.requestURI;
                copy.queryString = this.queryString;
                copy.headers = new LinkedHashMap<>(this.headers);
                copy.requestBodyBytes = this.requestBodyBytes;  // Share the byte array reference
                copy.parameterMap = this.parameterMap;  // Share the parameter map reference
                return copy;
            }
        }
    }

    /**
     * レスポンスメタデータのラッパークラス。完全なHTTPヘッダーサポート付き。
     * Jetty Core 12 API用に設計され、CaptureHolder2と統合される。
     */
    static class ResponseMetaDataWrapper implements ResponseMetaData, Cloneable
    {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResponseMetaDataWrapper.class);
        
        // Jetty公式の圧縮API（静的インスタンスで再利用）
        // 注: 静的初期化はResponseMetaDataWrapperの初回使用時に実行される（遅延初期化）
        private static final GzipCompression GZIP_COMPRESSION;
        private static final BrotliCompression BROTLI_COMPRESSION;  // 初期化失敗時はnull
        
        static
        {
            // GzipCompressionの初期化（標準Java APIのため必ず成功）
            try
            {
                GZIP_COMPRESSION = new GzipCompression();
                GZIP_COMPRESSION.start();
            }
            catch (Exception e)
            {
                // Gzip解凍は必須機能のため、初期化失敗時はアプリケーション起動を停止
                throw new ExceptionInInitializerError("Failed to initialize GzipCompression: " + e.getMessage());
            }
            
            // BrotliCompressionの初期化（ネイティブライブラリのため失敗する可能性あり）
            BROTLI_COMPRESSION = initializeBrotliCompression();
        }
        
        /**
         * BrotliCompressionを初期化する。
         * 初期化に失敗した場合はnullを返す（Brotli解凍は使用不可となる）。
         * 
         * @return 初期化済みのBrotliCompressionインスタンス、または失敗時はnull
         */
        private static BrotliCompression initializeBrotliCompression()
        {
            try
            {
                BrotliCompression brotli = new BrotliCompression();
                brotli.start();
                return brotli;
            }
            catch (Throwable e)
            {
                log.warn("Failed to initialize BrotliCompression (Brotli decompression will not be available): {}", 
                    e.getMessage());
                return null;  // 初期化失敗時はnullを返す
            }
        }
        
        private int status;
        private String reason;
        private String contentType;
        private Map<String, String> headers = new LinkedHashMap<>();
        private byte[] responseBodyBytes = null;  // Store as byte array, not InputStream

        /**
         * Initialize from CaptureHolder2.HttpResponse.
         * This is the primary and most efficient initialization method.
         */
        void set(CaptureHolder2.HttpResponse httpResponse)
        {
            log.debug("ResponseMetaDataWrapper.set(HttpResponse) called: status={}, bodySize={}", 
                httpResponse.getStatus(), httpResponse.getBodySize());
            
            this.status = httpResponse.getStatus();
            this.reason = httpResponse.getReason();
            this.headers = new LinkedHashMap<>(httpResponse.getHeaders());
            this.contentType = httpResponse.getContentType();
            
            // レスポンスボディの処理（圧縮解凍を含む）
            if (httpResponse.getBodySize() > 0)
            {
                this.responseBodyBytes = processResponseBody(httpResponse);
            }
        }
        
        /**
         * レスポンスボディを処理し、必要に応じて解凍する。
         * 
         * @param httpResponse HTTPレスポンス
         * @return 処理済みのボディバイト配列
         */
        private byte[] processResponseBody(CaptureHolder2.HttpResponse httpResponse)
        {
            byte[] bodyBytes = getBodyBytes(httpResponse);
            if (bodyBytes == null || bodyBytes.length == 0)
            {
                return new byte[0];
            }
            
            log.debug("Response headers: {}", headers);
            
            String contentEncoding = getHeaderCaseInsensitive(headers, "Content-Encoding");
            CompressionType compressionType = detectCompressionType(contentEncoding, bodyBytes);
            
            return decompressIfNeeded(bodyBytes, compressionType, contentEncoding);
        }
        
        /**
         * HTTPレスポンスからボディバイトを取得する（圧縮後の生データ）。
         */
        private byte[] getBodyBytes(CaptureHolder2.HttpResponse httpResponse)
        {
            log.debug("Attempting to get body bytes: reportedSize={}", httpResponse.getBodySize());
            
            byte[] bodyBytes = httpResponse.getBodyAsBytes();
            
            log.debug("Successfully retrieved body bytes: actualSize={}", bodyBytes != null ? bodyBytes.length : 0);
            
            if (bodyBytes == null || bodyBytes.length == 0)
            {
                log.warn("Body bytes is null or empty despite getBodySize() = {}", httpResponse.getBodySize());
            }
            
            return bodyBytes;
        }
        
        /**
         * 圧縮形式を検出する。
         */
        private CompressionType detectCompressionType(String contentEncoding, byte[] bodyBytes)
        {
            log.debug("Content-Encoding header value: '{}', body size: {} bytes", contentEncoding, bodyBytes.length);
            
            // ヘッダーベースの検出
            CompressionType type = switch (contentEncoding == null ? "" : contentEncoding.toLowerCase())
            {
                case "gzip" -> CompressionType.GZIP;
                case "br" -> CompressionType.BROTLI;
                case "" -> CompressionType.NONE;
                default -> {
                    log.error("Unsupported Content-Encoding: '{}' (only 'gzip' and 'br' are supported)", contentEncoding);
                    yield CompressionType.NONE;
                }
            };
            
            // マジックバイトによるフォールバック検出（gzipのみ）
            if (type == CompressionType.NONE && bodyBytes.length >= 2)
            {
                boolean isGzipMagic = (bodyBytes[0] & 0xFF) == 0x1f && (bodyBytes[1] & 0xFF) == 0x8b;
                if (isGzipMagic)
                {
                    if (contentEncoding != null)
                    {
                        log.warn("gzip圧縮データをマジックバイトで検出しましたが、Content-Encodingは'{}'でした", contentEncoding);
                    }
                    else
                    {
                        log.debug("gzip圧縮データをマジックバイトで検出しました（Content-Encodingヘッダーなし）");
                    }
                    return CompressionType.GZIP;
                }
            }
            
            return type;
        }
        
        /**
         * 必要に応じてボディを解凍する。
         */
        private byte[] decompressIfNeeded(byte[] bodyBytes, CompressionType type, String contentEncoding)
        {
            return switch (type)
            {
                case GZIP -> decompressGzip(bodyBytes, contentEncoding);
                case BROTLI -> decompressBrotli(bodyBytes, contentEncoding);
                case NONE -> bodyBytes;
            };
        }
        
        /**
         * Gzip形式のデータを解凍する。
         */
        private byte[] decompressGzip(byte[] bodyBytes, String contentEncoding)
        {
            try (InputStream compressedStream = new ByteArrayInputStream(bodyBytes);
                 InputStream gzipStream = GZIP_COMPRESSION.newDecoderInputStream(compressedStream);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                gzipStream.transferTo(baos);
                byte[] decompressed = baos.toByteArray();
                
                log.debug("Response body ungzipped: {} bytes -> {} bytes (Content-Encoding: {})", 
                    bodyBytes.length, decompressed.length, contentEncoding);
                
                return decompressed;
            }
            catch (IOException e)
            {
                log.error("Failed to decompress Gzip data: {}", e.getMessage(), e);
                log.warn("Using raw compressed body due to Gzip decompression failure");
                return bodyBytes;
            }
        }
        
        /**
         * Brotli形式のデータを解凍する。
         */
        private byte[] decompressBrotli(byte[] bodyBytes, String contentEncoding)
        {
            if (BROTLI_COMPRESSION == null)
            {
                log.warn("Brotli decompression requested but BrotliCompression is not available (initialization failed at startup)");
                log.warn("Using raw compressed body (Brotli-compressed data will not be decompressed)");
                return bodyBytes;
            }
            
            log.debug("Starting Brotli decompression: input size = {} bytes", bodyBytes.length);
            
            try (InputStream compressedStream = new ByteArrayInputStream(bodyBytes);
                 InputStream brotliStream = BROTLI_COMPRESSION.newDecoderInputStream(compressedStream);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                brotliStream.transferTo(baos);
                byte[] decompressed = baos.toByteArray();
                
                log.debug("Response body decompressed (Brotli): {} bytes -> {} bytes (Content-Encoding: {})", 
                    bodyBytes.length, decompressed.length, contentEncoding);
                
                return decompressed;
            }
            catch (IOException e)
            {
                log.error("Failed to decompress Brotli data: {}", e.getMessage(), e);
                log.warn("Using raw compressed body due to Brotli decompression failure");
                return bodyBytes;
            }
        }
        
        /**
         * 圧縮形式の列挙型
         */
        private enum CompressionType
        {
            NONE,    // 非圧縮
            GZIP,    // Gzip圧縮
            BROTLI   // Brotli圧縮
        }

        /**
         * Legacy initialization from Jetty Response (for CONNECT response metadata).
         * Less efficient than set(CaptureHolder2.HttpResponse).
         */
        void set(Response res)
        {
            log.debug("ResponseMetaDataWrapper.set(Response) called: status={}", res.getStatus());
            
            this.status = res.getStatus();
            this.contentType = res.getHeaders().get(HttpHeader.CONTENT_TYPE);
            
            // Copy headers
            res.getHeaders().forEach(field -> 
                headers.put(field.getName(), field.getValue())
            );
        }
        
        void setStatus(int status)
        {
            this.status = status;
        }
        
        void setReason(String reason)
        {
            this.reason = reason;
        }
        
        void setContentType(String contentType)
        {
            this.contentType = contentType;
        }
        
        void addHeader(String name, String value)
        {
            headers.put(name, value);
        }
        
        /**
         * Get all HTTP headers.
         */
        public Map<String, String> getHeaders()
        {
            return Collections.unmodifiableMap(headers);
        }
        
        /**
         * Get a specific HTTP header value.
         */
        public String getHeader(String name)
        {
            return headers.get(name);
        }
        
        /**
         * Get a specific HTTP header value (case-insensitive).
         * HTTPヘッダーは大文字小文字を区別しないため、このメソッドを使用する。
         */
        private static String getHeaderCaseInsensitive(Map<String, String> headers, String name)
        {
            if (headers == null || name == null)
            {
                return null;
            }
            
            // 完全一致を優先
            String value = headers.get(name);
            if (value != null)
            {
                return value;
            }
            
            // ケースインセンシティブ検索
            for (Map.Entry<String, String> entry : headers.entrySet())
            {
                if (name.equalsIgnoreCase(entry.getKey()))
                {
                    return entry.getValue();
                }
            }
            
            return null;
        }
        
        /**
         * Get the HTTP status reason phrase.
         */
        public String getReason()
        {
            return reason;
        }

        @Override
        public int getStatus()
        {
            return status;
        }

        @Override
        public String getContentType()
        {
            return contentType;
        }

        @Override
        public Optional<InputStream> getResponseBody()
        {
            log.debug("ResponseMetaDataWrapper.getResponseBody() called: bodyBytes={}, size={} bytes", 
                responseBodyBytes != null, responseBodyBytes != null ? responseBodyBytes.length : 0);
            
            // Return a new ByteArrayInputStream each time for thread-safe reuse
            if (responseBodyBytes != null && responseBodyBytes.length > 0)
            {
                return Optional.of(new ByteArrayInputStream(responseBodyBytes));
            }
            return Optional.empty();
        }
        
        @Override
        public ResponseMetaDataWrapper clone()
        {
            try
            {
                ResponseMetaDataWrapper copy = (ResponseMetaDataWrapper) super.clone();
                // Deep copy headers map
                copy.headers = new LinkedHashMap<>(this.headers);
                // Byte array is immutable reference, no need to copy the array itself
                // (multiple clones can share the same byte array safely)
                return copy;
            }
            catch (CloneNotSupportedException e)
            {
                // This should never happen as we implement Cloneable
                ResponseMetaDataWrapper copy = new ResponseMetaDataWrapper();
                copy.status = this.status;
                copy.reason = this.reason;
                copy.contentType = this.contentType;
                copy.headers = new LinkedHashMap<>(this.headers);
                copy.responseBodyBytes = this.responseBodyBytes;  // Share the byte array reference
                return copy;
            }
        }
    }
}
