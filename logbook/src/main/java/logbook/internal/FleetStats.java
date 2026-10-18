package logbook.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

import logbook.bean.Ship;
import lombok.Value;

/**
 * 艦隊タブに出している合計値。
 * 制空・触接・Lv・火力・対空・対潜・索敵・TP は退避艦を除く。
 * 判定式(33) と速度は、現行の艦隊タブと同じく退避艦を含む。
 */
@Value
public class FleetStats {

    /** 最速の文字色 */
    public static final String SPEED_STYLE_FASTEST = "-fx-text-fill: #9FEEEE;";

    /** 高速+の文字色 */
    public static final String SPEED_STYLE_FAST_PLUS = "-fx-text-fill: #CFEEEE;";

    /** 制空値 */
    int airSuperiority;

    /** 触接開始率（百分率の整数部） */
    int touchPlaneStartPercent;

    /** 分岐点係数 */
    double branchCoefficient;

    /** 判定式(33) 装備索敵 */
    double itemView;

    /** 判定式(33) 艦娘索敵 */
    double shipView;

    /** 判定式(33) 司令部スコア */
    double levelScore;

    /** 判定式(33) 艦隊スコア */
    double fleetScore;

    /** レベル合計 */
    int lvSum;

    /** 火力合計 */
    int karyokuSum;

    /** 対空合計 */
    int taikuSum;

    /** 対潜合計 */
    int taisenSum;

    /** 索敵合計 */
    int sakutekiSum;

    /** TP（S勝利） */
    int transportPoint;

    /** TP の 7 割（小数切り捨て） */
    int transportPoint70;

    /** 速度ラベル */
    String speedLabel;

    /** 速度ラベルのスタイル */
    String speedStyle;

    /**
     * 艦隊の合計を計算します。
     *
     * @param ships 艦隊の艦（退避艦を含む）
     * @param branchCoefficient 分岐点係数
     * @return 合計
     */
    public static FleetStats of(List<Ship> ships, double branchCoefficient) {
        List<Ship> withoutEscape = ships.stream()
                .filter(ship -> !Ships.isEscape(ship))
                .collect(Collectors.toList());

        int airSuperiority = withoutEscape.stream()
                .mapToInt(Ships::airSuperiority)
                .sum();
        int touch = (int) Math.floor(Ships.touchPlaneStartProbability(withoutEscape) * 100);
        Ships.Decision33 decision33 = Ships.decision33(ships, branchCoefficient);
        int lvSum = withoutEscape.stream().mapToInt(Ship::getLv).sum();
        int karyokuSum = withoutEscape.stream().mapToInt(ship -> ship.getKaryoku().get(0)).sum();
        int taikuSum = withoutEscape.stream().mapToInt(ship -> ship.getTaiku().get(0)).sum();
        int taisenSum = withoutEscape.stream().mapToInt(ship -> ship.getTaisen().get(0)).sum();
        int sakutekiSum = withoutEscape.stream().mapToInt(ship -> ship.getSakuteki().get(0)).sum();
        int tp = withoutEscape.stream().mapToInt(Ships::transportPoint).sum();

        int speed = ships.stream().map(Ship::getSoku).mapToInt(Integer::intValue).min().orElse(0);
        String speedLabel;
        String speedStyle;
        if (speed >= 20) {
            speedLabel = "最速";
            speedStyle = SPEED_STYLE_FASTEST;
        } else if (speed >= 15) {
            speedLabel = "高速+";
            speedStyle = SPEED_STYLE_FAST_PLUS;
        } else if (speed >= 10) {
            speedLabel = "高速";
            speedStyle = "";
        } else if (speed >= 5) {
            speedLabel = "低速";
            speedStyle = "";
        } else {
            speedLabel = "(不明)";
            speedStyle = "";
        }

        return new FleetStats(airSuperiority, touch, branchCoefficient,
                decision33.getItemView(), decision33.getShipView(), decision33.getLevelScore(), decision33.getFleetScore(),
                lvSum, karyokuSum, taikuSum, taisenSum, sakutekiSum,
                tp, (int) (tp * 7 / 10), speedLabel, speedStyle);
    }

    /**
     * 判定式(33) の値を返します。
     *
     * @return 判定式(33)
     */
    public double decision33() {
        return (this.branchCoefficient * this.itemView) + this.shipView - this.levelScore + this.fleetScore;
    }

    /**
     * 制空値の表示文字列を返します。
     *
     * @return 制空値
     */
    public String airSuperiorityText() {
        return Integer.toString(this.airSuperiority);
    }

    /**
     * 触接開始率の表示文字列を返します。
     *
     * @return 触接開始率
     */
    public String touchPlaneStartText() {
        return this.touchPlaneStartPercent + "%";
    }

    /**
     * 判定式(33) の表示文字列を返します。
     *
     * @return 小数第3位未満切り捨ての文字列
     */
    public String decision33Text() {
        return BigDecimal.valueOf(this.decision33())
                .setScale(3, RoundingMode.FLOOR)
                .toPlainString();
    }

    /**
     * 判定式(33) の内訳を返します。
     *
     * @return ポップアップ用の文字列
     */
    public String decision33Detail() {
        return "判定式(33):" + this.decision33() + "(分岐点係数:" + this.branchCoefficient + ")\n"
                + "装備索敵:" + this.itemView + "\n"
                + "艦娘索敵:" + this.shipView + "\n"
                + "司令部スコア:" + this.levelScore + "\n"
                + "艦隊スコア:" + this.fleetScore;
    }

    /**
     * 分岐点係数ボタンの表示文字列を返します。
     *
     * @return 分岐点係数
     */
    public String branchCoefficientText() {
        return "分岐点係数:" + this.branchCoefficient;
    }

    /**
     * TP の表示文字列を返します。
     *
     * @return S勝利時/7割
     */
    public String transportPointText() {
        return this.transportPoint + "/" + this.transportPoint70;
    }
}
