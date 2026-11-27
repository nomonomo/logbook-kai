package logbook.notify;

/**
 * 戦力ゲージが、次の出撃で割り切れる状態になった。
 *
 * @param mapName 海域名。無ければ空文字
 * @param gaugeBefore 戦闘前のゲージ残量
 * @param damage ボス旗艦に与えたダメージ。オーバーキルは含まない
 * @param gaugeAfter 戦闘後のゲージ残量
 * @param bossMaxHp ボス旗艦の最大HP
 */
public record LastDance(
        String mapName,
        int gaugeBefore,
        int damage,
        int gaugeAfter,
        int bossMaxHp) implements Notification {

    public LastDance {
        if (mapName == null) {
            mapName = "";
        }
    }

    /**
     * トーストと Discord に出す本文です。
     *
     * @return 本文
     */
    public String message() {
        String body = "次の出撃でゲージを割り切れます。（残 " + this.gaugeAfter + " / ボス最大 " + this.bossMaxHp + "）";
        if (this.mapName.isEmpty()) {
            return body;
        }
        return this.mapName + " " + body;
    }
}
