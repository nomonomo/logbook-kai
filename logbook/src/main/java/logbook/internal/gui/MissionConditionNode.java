package logbook.internal.gui;

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * 遠征条件確認用のツリーノード（表示テキスト・アイコンを Property で保持）。
 */
public class MissionConditionNode {

    /**
     * アイコン種別
     */
    public enum IconState {
        /** なし */
        NONE,
        /** 成功（緑） */
        SUCCESS,
        /** 失敗（赤） */
        FAILURE,
        /** 成功だが大成功未達（オレンジ） */
        SUCCESS_ORANGE,
        /** 不明 */
        UNKNOWN,
        /** 情報 */
        INFO,
        /** 交戦型アイコン */
        DAMAGE_TYPE
    }

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    private final ObjectProperty<IconState> icon = new SimpleObjectProperty<>(this, "icon", IconState.NONE);

    private final ObjectProperty<Integer> damageType = new SimpleObjectProperty<>(this, "damageType", null);

    /**
     * @return 表示テキスト
     */
    public StringProperty textProperty() {
        return this.text;
    }

    public String getText() {
        return this.text.get();
    }

    public void setText(String value) {
        this.text.set(value);
    }

    /**
     * @return アイコン状態
     */
    public ObjectProperty<IconState> iconProperty() {
        return this.icon;
    }

    public IconState getIcon() {
        return this.icon.get();
    }

    public void setIcon(IconState value) {
        this.icon.set(value);
    }

    /**
     * @return 交戦型（{@link IconState#DAMAGE_TYPE} 時）
     */
    public ObjectProperty<Integer> damageTypeProperty() {
        return this.damageType;
    }

    public Integer getDamageType() {
        return this.damageType.get();
    }

    public void setDamageType(Integer value) {
        this.damageType.set(value);
    }

    /**
     * 成否 Boolean からアイコンを設定する。
     *
     * @param result 成否（null は不明）
     */
    public void setIconFromResult(Boolean result) {
        if (result == null) {
            this.setIcon(IconState.UNKNOWN);
        } else if (result) {
            this.setIcon(IconState.SUCCESS);
        } else {
            this.setIcon(IconState.FAILURE);
        }
    }

    /**
     * 現在のアイコン状態から表示用 Node を生成する。
     *
     * @return graphic（不要なら null）
     */
    public Node createGraphic() {
        IconState state = this.getIcon();
        if (state == null || state == IconState.NONE) {
            return null;
        }
        if (state == IconState.DAMAGE_TYPE) {
            // 交戦型はセル側で文言の後ろに別表示する
            return null;
        }
        FontAwesome.Glyph glyph;
        Color color;
        switch (state) {
        case SUCCESS:
            glyph = FontAwesome.Glyph.CHECK;
            color = Color.GREEN;
            break;
        case FAILURE:
            glyph = FontAwesome.Glyph.EXCLAMATION;
            color = Color.RED;
            break;
        case SUCCESS_ORANGE:
            glyph = FontAwesome.Glyph.CHECK;
            color = Color.ORANGE;
            break;
        case INFO:
            glyph = FontAwesome.Glyph.INFO;
            color = null;
            break;
        case UNKNOWN:
        default:
            glyph = FontAwesome.Glyph.QUESTION;
            color = Color.GRAY;
            break;
        }
        GlyphFont fontAwesome = GlyphFontRegistry.font("FontAwesome");
        StackPane pane = new StackPane();
        pane.setPrefWidth(18);
        Glyph g = fontAwesome.create(glyph);
        if (color != null) {
            g.color(color);
        }
        pane.getChildren().add(g);
        return pane;
    }

    @Override
    public String toString() {
        return this.getText();
    }
}
