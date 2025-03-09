package logbook.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import logbook.internal.Config;
import lombok.Data;

/**
 * 海域経験値
 */
@Data
public class AppSeaAreaExpCollection implements Serializable {

    private static final long serialVersionUID = -4153584079101950354L;

    private List<AppSeaAreaExp> list = new ArrayList<>();

    /**
     * 海域経験値のデフォルト値
     * @return 海域経験値のデフォルト値
     */
    public static AppSeaAreaExpCollection getDefault() {
        AppSeaAreaExpCollection collection = new AppSeaAreaExpCollection();

        collection.list.add(new AppSeaAreaExp("(1期)鎮守府正面海域", 30));
        collection.list.add(new AppSeaAreaExp("(1期)南西諸島沖", 50));
        collection.list.add(new AppSeaAreaExp("(1期)製油所地帯沿岸", 80));
        collection.list.add(new AppSeaAreaExp("(1期)南西諸島防衛線", 100));
        collection.list.add(new AppSeaAreaExp("(1期)鎮守府近海", 150));
        collection.list.add(new AppSeaAreaExp("(1期)カムラン半島", 120));
        collection.list.add(new AppSeaAreaExp("(1期)バシー島沖", 150));
        collection.list.add(new AppSeaAreaExp("(1期)東部オリョール海", 200));
        collection.list.add(new AppSeaAreaExp("(1期)沖ノ島海域", 300));
        collection.list.add(new AppSeaAreaExp("(1期)沖ノ島沖", 250));
        collection.list.add(new AppSeaAreaExp("(1期)モーレイ海", 310));
        collection.list.add(new AppSeaAreaExp("(1期)キス島沖", 320));
        collection.list.add(new AppSeaAreaExp("(1期)アルフォンシーノ方面", 330));
        collection.list.add(new AppSeaAreaExp("(1期)北方海域全域", 350));
        collection.list.add(new AppSeaAreaExp("(1期)北方AL海域", 400));
        collection.list.add(new AppSeaAreaExp("(1期)ジャム島攻略作戦", 310));
        collection.list.add(new AppSeaAreaExp("(1期)カレー洋制圧戦", 320));
        collection.list.add(new AppSeaAreaExp("(1期)リランカ島空襲", 330));
        collection.list.add(new AppSeaAreaExp("(1期)カスガダマ沖海戦", 340));
        collection.list.add(new AppSeaAreaExp("(1期)南方海域前面", 360));
        collection.list.add(new AppSeaAreaExp("(1期)珊瑚諸島沖", 380));
        collection.list.add(new AppSeaAreaExp("(1期)サブ島沖海域", 400));
        collection.list.add(new AppSeaAreaExp("(1期)サーモン海域", 420));
        collection.list.add(new AppSeaAreaExp("(1期)サーモン海域北方", 450));
        collection.list.add(new AppSeaAreaExp("(1期)中部海域哨戒線", 380));
        collection.list.add(new AppSeaAreaExp("(1期)MS諸島沖", 420));

        return collection;
    }

    /**
     * アプリケーションのデフォルト設定ディレクトリから<code>AppSeaAreaExpCollection</code>を取得します、
     * これは次の記述と同等です
     * <blockquote>
     *     <code>Config.getDefault().get(AppSeaAreaExpCollection.class, AppSeaAreaExpCollection::getDefault)</code>
     * </blockquote>
     *
     * @return <code>AppSeaAreaExpCollection</code>
     */
    public static AppSeaAreaExpCollection get() {
        return Config.getDefault().get(AppSeaAreaExpCollection.class, AppSeaAreaExpCollection::getDefault);
    }
}
