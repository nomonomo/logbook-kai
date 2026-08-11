package logbook.internal.log;

import java.util.List;
import java.util.StringJoiner;

import logbook.bean.BattleEventLog;

/**
 * 戦闘イベントログ。
 */
public class BattleEventLogFormat extends LogFormatBase<BattleEventLog> {

    @Override
    public String name() {
        return "戦闘イベントログ";
    }

    @Override
    public String header() {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add("日付").add("種別").add("海域").add("マス")
                .add("内容").add("艦隊行動").add("味方陣形").add("敵陣形")
                .add("制空権").add("味方触接").add("敵触接").add("敵艦隊");
        for (int i = 1; i <= 12; i++) {
            joiner.add("敵艦" + i).add("敵艦" + i + "HP");
        }
        for (int i = 1; i <= 3; i++) {
            joiner.add("基地" + i + "HP");
        }
        return joiner.add("ギミック").toString();
    }

    @Override
    public String format(BattleEventLog event) {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(wrap(event.getTime()))
                .add(wrap(event.getType()))
                .add(wrap(event.getArea()))
                .add(wrap(event.getCell()))
                .add(wrap(event.getContent()))
                .add(wrap(event.getIntercept()))
                .add(wrap(event.getFformation()))
                .add(wrap(event.getEformation()))
                .add(wrap(event.getDispseiku()))
                .add(wrap(event.getFtouch()))
                .add(wrap(event.getEtouch()))
                .add(wrap(event.getEfleet()));
        addPairs(joiner, event.getEnemyShips(), event.getEnemyHp());
        addValues(joiner, event.getBaseHp(), 3);
        return joiner.add(wrap(event.getGimmick())).toString();
    }

    private static void addPairs(StringJoiner joiner, List<String> names, List<String> hp) {
        for (int i = 0; i < 12; i++) {
            joiner.add(wrap(valueAt(names, i)));
            joiner.add(wrap(valueAt(hp, i)));
        }
    }

    private static String valueAt(List<String> values, int index) {
        return values != null && values.size() > index && values.get(index) != null
                ? values.get(index)
                : "";
    }

    private static void addValues(StringJoiner joiner, List<String> values, int size) {
        for (int i = 0; i < size; i++) {
            joiner.add(wrap(valueAt(values, i)));
        }
    }
}
