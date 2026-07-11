package logbook.internal;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import logbook.Messages;
import logbook.api.API;
import logbook.api.APIListenerSpi;
import logbook.internal.Tuple.Pair;
import logbook.internal.proxy.ProxyContentListenerLogger;
import logbook.plugin.PluginServices;
import logbook.proxy.ContentListenerSpi;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * APIを受け取りJSONをAPIListenerSpiを実装したサービスプロバイダに送ります
 *
 */
@Slf4j
public final class APIListener implements ContentListenerSpi {

    private static final Logger contentListenerLog =
        LoggerFactory.getLogger("logbook.internal.proxy.ContentListenerLog");

    private final Map<String, List<Pair<String, APIListenerSpi>>> services;

    private final List<Pair<String, APIListenerSpi>> all = new ArrayList<>();

    public APIListener() {
        Function<APIListenerSpi, Stream<Pair<String, APIListenerSpi>>> mapper = impl -> {
            API target = impl.getClass().getAnnotation(API.class);
            if (target != null) {
                return Arrays.stream(target.value())
                        .map(k -> Tuple.of(k, impl));
            } else {
                this.all.add(Tuple.of(null, impl));
            }
            return Stream.empty();
        };
        this.services = PluginServices.instances(APIListenerSpi.class)
                .flatMap(mapper)
                .collect(Collectors.groupingBy(Pair::getKey));
    }

    @Override
    public boolean test(RequestMetaData requestMetaData) {
        String uri = requestMetaData.getRequestURI();
        return uri.startsWith("/kcsapi/") && (!this.all.isEmpty() || this.services.containsKey(uri)); //$NON-NLS-1$
    }

    @Override
    public void accept(RequestMetaData requestMetaData, ResponseMetaData responseMetaData) {
        try {
            // レスポンスのJSONを復号します
            InputStream stream = responseMetaData.getResponseBody().get();
            // レスポンスボディのJSONはsvdata=から始まるので除去します
            int read;
            while (((read = stream.read()) != -1) && (read != '=')) {
            }

            try (JsonReader jsonreader = Json.createReader(stream)) {
                JsonObject json = jsonreader.readObject();

                this.send(requestMetaData, responseMetaData, json);
            }
        } catch (Exception e) {
            log.warn(Messages.getString("APIListener.2"), e); //$NON-NLS-1$
            // 例外発生時のレスポンスの内容をログに出力する
            StringBuilder sb = new StringBuilder();
            sb.append("uri=");
            try {
                if (requestMetaData != null) {
                    sb.append(requestMetaData.getRequestURI());
                }
            } catch (Exception e2) {
                sb.append(e2.toString());
            }
            sb.append(",body=");
            try {
                if (responseMetaData != null) {
                    InputStream in = responseMetaData.getResponseBody().orElse(null);
                    if (in != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                        sb.append(new String(out.toByteArray(), StandardCharsets.UTF_8));
                        in.close();
                    }
                }
            } catch (Exception e2) {
                sb.append(e2.toString());
            }
            log.warn(sb.toString());
        }
    }

    void send(RequestMetaData req, ResponseMetaData res, JsonObject json) {
        String uri = req.getRequestURI();
        List<Pair<String, APIListenerSpi>> pairs = this.services.getOrDefault(uri, Collections.emptyList());

        for (Pair<String, APIListenerSpi> pair : pairs) {
            Runnable task = () -> this.createTask(pair, json, req, res);
            ThreadManager.getExecutorService().submit(task);
        }

        for (Pair<String, APIListenerSpi> pair : this.all) {
            Runnable task = () -> this.createTask(pair, json, req, res);
            ThreadManager.getExecutorService().submit(task);
        }
    }

    private void createTask(Pair<String, APIListenerSpi> pair, JsonObject json, RequestMetaData req,
            ResponseMetaData res) {
        APIListenerSpi handler = pair.getValue();
        long startNanos = System.nanoTime();
        ProxyContentListenerLogger.Outcome outcome = ProxyContentListenerLogger.Outcome.SUCCESS;
        String errorDetail = null;
        try {
            log.atDebug()
                .setMessage(() -> Messages.getString("APIListener.0", //$NON-NLS-1$
                        handler.getClass().getName(), req.getRequestURI()))
                .log();
            handler.accept(json, req, res);
        } catch (Exception e) {
            outcome = ProxyContentListenerLogger.Outcome.ERROR;
            errorDetail = ProxyContentListenerLogger.formatCause(e);
            log.warn(Messages.getString("APIListener.1"), e); //$NON-NLS-1$
            log.warn(json.toString());
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            ProxyContentListenerLogger.log(
                contentListenerLog, handler.getClass().getName(),
                ProxyContentListenerLogger.Layer.HANDLER, req, elapsedMs, outcome, errorDetail);
        }
    }
}
