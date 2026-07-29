package logbook.api;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

import logbook.bean.AppConfig;
import logbook.bean.Maparea;
import logbook.bean.MapareaCollection;
import logbook.bean.MapinfoMst;
import logbook.bean.MapinfoMstCollection;
import logbook.bean.Mission;
import logbook.bean.MissionCollection;
import logbook.bean.ShipMst;
import logbook.bean.ShipMstCollection;
import logbook.bean.Shipgraph;
import logbook.bean.ShipgraphCollection;
import logbook.bean.SlotitemEquiptype;
import logbook.bean.SlotitemEquiptypeCollection;
import logbook.bean.SlotitemMst;
import logbook.bean.SlotitemMstCollection;
import logbook.bean.Stype;
import logbook.bean.StypeCollection;
import logbook.bean.UseitemMst;
import logbook.bean.UseitemMstCollection;
import logbook.internal.Config;
import logbook.internal.JsonHelper;
import logbook.internal.LoggerHolder;
import logbook.internal.api.ApiSchemaLog;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_start2
 *
 */
@API("/kcsapi/api_start2/getData")
public class ApiStart2 implements APIListenerSpi {

    /** Collection に反映するキー */
    private static final Set<String> HANDLED_API_DATA_KEYS = Set.of(
            "api_mst_ship",
            "api_mst_shipgraph",
            "api_mst_slotitem_equiptype",
            "api_mst_stype",
            "api_mst_slotitem",
            "api_mst_useitem",
            "api_mst_mission",
            "api_mst_maparea",
            "api_mst_mapinfo");

    /** 未対応でよいと確認済みのキー */
    private static final Set<String> IGNORED_API_DATA_KEYS = Set.of(
            "api_mst_equip_exslot",
            "api_mst_equip_exslot_ship",
            "api_mst_equip_limit_exslot",
            "api_mst_payitem",
            "api_mst_item_shop",
            "api_mst_mapbgm",
            "api_mst_const",
            "api_mst_shipupgrade",
            "api_mst_bgm",
            "api_mst_equip_ship",
            "api_mst_furniture");

    /** 未知キー報告の対象外（対応済み + 意図的未対応） */
    private static final Set<String> KNOWN_API_DATA_KEYS = Stream
            .concat(HANDLED_API_DATA_KEYS.stream(), IGNORED_API_DATA_KEYS.stream())
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        try (ApiSchemaLog.Scope ignored = ApiSchemaLog.openRequest(
                req.getRequestURI(), req.getRequestId(), getClass().getName())) {
            JsonObject data = json.getJsonObject("api_data");
            if (data != null) {
                JsonHelper.reportUnknownKeys(data, "api_data", KNOWN_API_DATA_KEYS);
                this.apiMstShip(data.getJsonArray("api_mst_ship"));
                this.apiMstShipgraph(data.getJsonArray("api_mst_shipgraph"));
                this.apiMstSlotitemEquiptype(data.getJsonArray("api_mst_slotitem_equiptype"));
                this.apiMstStype(data.getJsonArray("api_mst_stype"));
                this.apiMstSlotitem(data.getJsonArray("api_mst_slotitem"));
                this.apiMstUseitem(data.getJsonArray("api_mst_useitem"));
                this.apiMstMission(data.getJsonArray("api_mst_mission"));
                this.apiMstMaparea(data.getJsonArray("api_mst_maparea"));
                this.apiMstMapinfo(data.getJsonArray("api_mst_mapinfo"));
                this.store(data);
            }
            Config.getDefault().store();
        }
    }

    /**
     * api_data.api_mst_ship
     *
     * @param array api_mst_ship
     */
    private void apiMstShip(JsonArray array) {
        ShipMstCollection.get()
                .setShipMap(JsonHelper.toMap(array, ShipMst::getId, ShipMst::toShip));
    }

    /**
     * api_data.api_mst_shipgraph
     *
     * @param array api_mst_shipgraph
     */
    private void apiMstShipgraph(JsonArray array) {
        Map<Integer, ShipMst> map = ShipMstCollection.get()
                .getShipMap();
        for (JsonValue val : array) {
            JsonObject item = (JsonObject) val;
            Integer key = item.getInt("api_id");
            ShipMst bean = map.get(key);
            if (bean != null) {
                bean.setGraph(item.getString("api_filename"));
            }
        }
        ShipgraphCollection.get()
                .setShipgraphMap(JsonHelper.toMap(array, Shipgraph::getId, Shipgraph::toShipgraph));
    }

    /**
     * api_data.api_mst_slotitem_equiptype
     *
     * @param array api_mst_slotitem_equiptype
     */
    private void apiMstSlotitemEquiptype(JsonArray array) {
        SlotitemEquiptypeCollection.get()
                .setEquiptypeMap(
                        JsonHelper.toMap(array, SlotitemEquiptype::getId, SlotitemEquiptype::toSlotitemEquiptype));
    }

    /**
     * api_data.api_mst_stype
     *
     * @param array api_mst_stype
     */
    private void apiMstStype(JsonArray array) {
        StypeCollection.get()
                .setStypeMap(JsonHelper.toMap(array, Stype::getId, Stype::toStype));
    }

    /**
     * api_data.api_mst_slotitem
     *
     * @param array api_mst_slotitem
     */
    private void apiMstSlotitem(JsonArray array) {
        SlotitemMstCollection.get()
                .setSlotitemMap(JsonHelper.toMap(array, SlotitemMst::getId, SlotitemMst::toSlotitem));
    }

    /**
     * api_data.api_mst_useitem
     *
     * @param array api_mst_useitem
     */
    private void apiMstUseitem(JsonArray array) {
        UseitemMstCollection.get()
                .setUseitemMap(JsonHelper.toMap(array, UseitemMst::getId, UseitemMst::toUseitem));
    }

    /**
     * api_data.api_mst_mission
     *
     * @param array api_mst_mission
     */
    private void apiMstMission(JsonArray array) {
        MissionCollection.get()
                .setMissionMap(JsonHelper.toMap(array, Mission::getId, Mission::toMission));
    }

    /**
     * api_data.api_mst_maparea
     *
     * @param array api_mst_maparea
     */
    private void apiMstMaparea(JsonArray array) {
        MapareaCollection.get()
                .getMaparea().putAll(JsonHelper.toMap(array, Maparea::getId, Maparea::toMaparea));
    }

    /**
     * api_data.api_mst_mapinfo
     *
     * @param array api_mst_mapinfo
     */
    private void apiMstMapinfo(JsonArray array) {
        MapinfoMstCollection.get()
                .getMapinfo().putAll(JsonHelper.toMap(array, MapinfoMst::getId, MapinfoMst::toMapinfoMst));
    }

    /**
     * store
     * 
     * @param obj api_data
     */
    private void store(JsonObject root) {
        if (AppConfig.get().isStoreApiStart2()) {
            try {
                String dir = AppConfig.get().getStoreApiStart2Dir();
                if (dir == null || "".equals(dir))
                    return;

                Path dirPath = Paths.get(dir);
                Path parent = dirPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                JsonWriterFactory factory = Json
                        .createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
                for (Entry<String, JsonValue> entry : root.entrySet()) {
                    String key = entry.getKey();
                    JsonValue val = entry.getValue();
                    JsonObject obj = Json.createObjectBuilder().add(key, val).build();

                    Path outPath = dirPath.resolve(key + ".json");

                    try (OutputStream out = Files.newOutputStream(outPath)) {
                        try (JsonWriter writer = factory.createWriter(out)) {
                            writer.write(obj);
                        }
                    }
                }
            } catch (Exception e) {
                LoggerHolder.get().warn("api_start2の保存に失敗しました", e);
            }
        }
    }
}
