package logbook.bean;

import java.io.Serializable;

import lombok.Data;

/**
 * 戦闘イベントの詳細情報。
 * CSVの日付と同じ日時をファイル名に使用して紐付ける。
 */
@Data
public class BattleEventDetail implements Serializable {

    private static final long serialVersionUID = -8922558427529585979L;

    private String time;
    private String type;
    private MapStartNext next;
}
