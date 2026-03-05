package logbook.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import logbook.bean.AppQuestCondition;
import logbook.bean.AppQuestConditionRecord;
import logbook.plugin.PluginServices;
import lombok.extern.slf4j.Slf4j;

/**
 * 任務条件 JSON の読み込み。リソースのオープン・クローズと読み込みの責務を持つ。
 * AppQuestCondition.loadFromResource は当クラスに委譲し、テストでは load(InputStream) / readRecord(InputStream) を利用する。
 */
@Slf4j
public final class AppQuestConditionLoader {

    private AppQuestConditionLoader() {
    }

    /**
     * 任務番号からリソースを開き、読み込んで AppQuestCondition を返す。
     * リソースが無い場合・読み込みに失敗した場合は null を返す。
     */
    public static AppQuestCondition loadFromResource(int questNo) {
        try (InputStream is = PluginServices.getQuestResourceAsStream(questNo)) {
            if (is == null) {
                return null;
            }
            return load(is);
        } catch (Exception e) {
            log.error("任務設定ファイルが読み込めませんでした。", e);
            return null;
        }
    }

    /**
     * 任務条件 JSON を AppQuestConditionRecord にデシリアライズする。
     * テストでの項目検証用。
     */
    public static AppQuestConditionRecord readRecord(InputStream is) throws IOException {
        return JsonMappers.READER_WITH_COMMENTS
                .forType(AppQuestConditionRecord.class)
                .readValue(is);
    }

    /**
     * 指定パスの任務条件 JSON を AppQuestConditionRecord にデシリアライズする。
     * オープン・クローズは Jackson が実施する。
     */
    public static AppQuestConditionRecord readRecord(Path path) throws IOException {
        return JsonMappers.READER_WITH_COMMENTS
                .forType(AppQuestConditionRecord.class)
                .readValue(path);
    }

    /**
     * 任務条件 JSON を読み込み、AppQuestCondition に変換して返す。
     */
    public static AppQuestCondition load(InputStream is) throws IOException {
        return AppQuestCondition.from(readRecord(is));
    }

    /**
     * 指定パスの任務条件 JSON を読み込み、AppQuestCondition に変換して返す。
     * オープン・クローズは Jackson が実施する。
     */
    public static AppQuestCondition load(Path path) throws IOException {
        return AppQuestCondition.from(readRecord(path));
    }
}
