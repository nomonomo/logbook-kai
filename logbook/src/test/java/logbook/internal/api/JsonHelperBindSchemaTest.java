package logbook.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import logbook.bean.ShipMst;
import logbook.internal.JsonHelper;

/**
 * {@link JsonHelper.Bind#reportUnknown()} と {@link JsonHelper#reportUnknownKeys} の動作検証。
 */
class JsonHelperBindSchemaTest {

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        ApiSchemaLog.clearReportedForTest();

        Logger logger = (Logger) LoggerFactory.getLogger("logbook.internal.api.ApiSchemaLog");
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger("logbook.internal.api.ApiSchemaLog");
        logger.detachAppender(appender);
        ApiSchemaLog.clearReportedForTest();
    }

    @Test
    void bindReportUnknownLogsUnboundKeysOnce() {
        JsonObject json = Json.createObjectBuilder()
                .add("api_id", 1)
                .add("api_sortno", 1)
                .add("api_sort_id", 1)
                .add("api_name", "test")
                .add("api_yomi", "test")
                .add("api_stype", 1)
                .add("api_ctype", 1)
                .add("api_afterlv", 0)
                .add("api_slot_num", 2)
                .add("api_maxeq", Json.createArrayBuilder().add(0).add(0))
                .add("api_aftershipid", 0)
                .add("api_taik", Json.createArrayBuilder().add(1).add(1))
                .add("api_souk", Json.createArrayBuilder().add(1).add(1))
                .add("api_houg", Json.createArrayBuilder().add(1).add(1))
                .add("api_raig", Json.createArrayBuilder().add(0).add(0))
                .add("api_tyku", Json.createArrayBuilder().add(0).add(0))
                .add("api_tais", Json.createArrayBuilder().add(0).add(0))
                .add("api_luck", Json.createArrayBuilder().add(1).add(1))
                .add("api_soku", 5)
                .add("api_leng", 1)
                .add("api_afterfuel", 0)
                .add("api_afterbull", 0)
                .add("api_fuel_max", 10)
                .add("api_bull_max", 10)
                .add("api_new_field", 99)
                .build();

        try (ApiSchemaLog.Scope ignored = ApiSchemaLog.openRequest(
                "/kcsapi/api_start2/getData", "req-1", "logbook.api.ApiStart2")) {
            ShipMst.toShip(json);
            ShipMst.toShip(json);
        }

        List<ILoggingEvent> events = appender.list.stream()
                .filter(event -> "api unknown field".equals(event.getMessage()))
                .toList();
        assertEquals(1, events.size());
        assertEquals("api_new_field", events.get(0).getMDCPropertyMap().get(ApiSchemaLog.MDC_FIELD));
        assertEquals("api_data.api_mst_ship[]", events.get(0).getMDCPropertyMap().get(ApiSchemaLog.MDC_JSON_PATH));
        assertEquals("/kcsapi/api_start2/getData",
                events.get(0).getMDCPropertyMap().get(ApiSchemaLog.MDC_URI_PATH));
    }

    @Test
    void reportUnknownWithoutAtDoesNotLog() {
        JsonObject json = Json.createObjectBuilder()
                .add("known", 1)
                .add("unknown", 2)
                .build();

        JsonHelper.bind(json)
                .setInteger("known", value -> {
                })
                .reportUnknown();

        assertTrue(appender.list.isEmpty());
    }

    @Test
    void reportUnknownKeysLogsTopLevelApiDataKeys() {
        JsonObject data = Json.createObjectBuilder()
                .add("api_mst_ship", Json.createArrayBuilder())
                .add("api_new_block", Json.createObjectBuilder())
                .build();

        try (ApiSchemaLog.Scope ignored = ApiSchemaLog.openRequest(
                "/kcsapi/api_start2/getData", "req-2", "logbook.api.ApiStart2")) {
            JsonHelper.reportUnknownKeys(data, "api_data", Set.of("api_mst_ship"));
        }

        List<ILoggingEvent> events = appender.list.stream()
                .filter(event -> "api unknown field".equals(event.getMessage()))
                .toList();
        assertEquals(1, events.size());
        assertEquals("api_new_block", events.get(0).getMDCPropertyMap().get(ApiSchemaLog.MDC_FIELD));
        assertEquals("api_data", events.get(0).getMDCPropertyMap().get(ApiSchemaLog.MDC_JSON_PATH));
    }
}
