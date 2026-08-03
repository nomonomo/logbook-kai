package logbook.bean;

import java.io.Serializable;
import java.util.List;

import jakarta.json.JsonObject;

import logbook.internal.JsonHelper;
import logbook.proxy.RequestMetaData;
import lombok.Data;

@Data
public class Createitem implements Serializable {

    private static final long serialVersionUID = 27343263577644496L;

    /** api_item1 */
    private Integer item1;

    /** api_item2 */
    private Integer item2;

    /** api_item3 */
    private Integer item3;

    /** api_item4 */
    private Integer item4;

    /** api_create_flag */
    private Boolean createFlag;

    /** api_material */
    private List<Integer> material;

    /** api_get_items */
    private List<SlotItem> getItems;

    /** SlotItem */
    private SlotItem slotItem;

    /** 秘書艦 */
    private Ship secretary;

    /**
     * <code>JsonObject</code>と<code>RequestMetaData</code>から{@link Createitem}を構築します
     *
     * @param json JsonObject
     * @param req RequestMetaData
     * @return {@link Createitem}
     */
    public static Createitem toCreateitem(JsonObject json, RequestMetaData req) {
        Createitem bean = new Createitem();
        bean.setItem1(Integer.valueOf(req.getParameter("api_item1", "0")));
        bean.setItem2(Integer.valueOf(req.getParameter("api_item2", "0")));
        bean.setItem3(Integer.valueOf(req.getParameter("api_item3", "0")));
        bean.setItem4(Integer.valueOf(req.getParameter("api_item4", "0")));

        JsonHelper.bind(json)
                .at("api_data")
                .setBoolean("api_create_flag", bean::setCreateFlag)
                .setIntegerList("api_material", bean::setMaterial)
                .set("api_get_items", bean::setGetItems, JsonHelper.toList(SlotItem::toSlotItem))
                // type3 別未装備 ID。所持装備は SlotItemCollection（get_items / slot_item）で管理し未使用
                .ignore("api_unset_items")
                .reportUnknown();

        Ship secretary = null;
        DeckPort port = DeckPortCollection.get()
                .getDeckPortMap()
                .get(1);
        if (port != null) {
            List<Integer> ships = port.getShip();
            if (ships != null) {
                Integer id = ships.get(0);
                secretary = ShipCollection.get()
                        .getShipMap()
                        .get(id);
            }
        }
        bean.setSecretary(secretary);

        return bean;
    }
}
