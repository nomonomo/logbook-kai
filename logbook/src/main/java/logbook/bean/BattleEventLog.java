package logbook.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 戦闘に付随して発生した非戦闘イベントのログ行。
 */
@Data
public class BattleEventLog implements Serializable {

    private static final long serialVersionUID = -5104185159948983544L;

    private String time = "";
    private String type = "";
    private String area = "";
    private String cell = "";
    private String content = "";
    private String intercept = "";
    private String fformation = "";
    private String eformation = "";
    private String dispseiku = "";
    private String ftouch = "";
    private String etouch = "";
    private String efleet = "";
    private List<String> enemyShips = new ArrayList<>();
    private List<String> enemyHp = new ArrayList<>();
    private List<String> baseHp = new ArrayList<>();
    private String gimmick = "";
}
