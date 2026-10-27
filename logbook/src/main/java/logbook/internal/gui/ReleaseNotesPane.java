package logbook.internal.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import logbook.internal.ReleaseNotesMarkdown;
import logbook.internal.ReleaseNotesMarkdown.Block;
import logbook.internal.ReleaseNotesMarkdown.BulletBlock;
import logbook.internal.ReleaseNotesMarkdown.CodeBlock;
import logbook.internal.ReleaseNotesMarkdown.Emph;
import logbook.internal.ReleaseNotesMarkdown.HeadingBlock;
import logbook.internal.ReleaseNotesMarkdown.Inline;
import logbook.internal.ReleaseNotesMarkdown.LineBreak;
import logbook.internal.ReleaseNotesMarkdown.ParagraphBlock;
import logbook.internal.ReleaseNotesMarkdown.Strong;
import logbook.internal.ReleaseNotesMarkdown.TextRun;
import lombok.extern.slf4j.Slf4j;

/**
 * 更新チェックダイアログのリリースノート表示。
 *
 * <p>commonmark で解析した Markdown を {@link TextFlow} / {@link Hyperlink} で描画する。
 * WebView は使わない。</p>
 */
@Slf4j
public class ReleaseNotesPane extends ScrollPane {

    /**
     * @param markdown リリースノート本文（null または空白のときは空メッセージ）
     */
    public ReleaseNotesPane(String markdown) {
        getStyleClass().add("release-notes");
        setFitToWidth(true);
        setPrefWidth(600);
        setPrefHeight(300);
        setContent(buildContent(markdown));
    }

    private Node buildContent(String markdown) {
        List<Block> blocks = ReleaseNotesMarkdown.parse(markdown);
        if (blocks.isEmpty()) {
            Label empty = new Label("リリースノートがありません。");
            empty.getStyleClass().add("release-notes-empty");
            empty.setWrapText(true);
            empty.setPadding(new Insets(10, 15, 10, 15));
            return empty;
        }

        VBox content = new VBox(6);
        content.getStyleClass().add("release-notes-content");
        content.setFillWidth(true);

        for (Block block : blocks) {
            content.getChildren().add(toNode(block));
        }
        return content;
    }

    private Node toNode(Block block) {
        return switch (block) {
            case HeadingBlock heading -> headingNode(heading);
            case ParagraphBlock paragraph -> wrappedFlow(inlinesToFlow(paragraph.inlines()));
            case BulletBlock bullet -> bulletNode(bullet);
            case CodeBlock code -> codeNode(code);
        };
    }

    private Node headingNode(HeadingBlock heading) {
        TextFlow flow = inlinesToFlow(heading.inlines());
        int level = Math.min(Math.max(heading.level(), 1), 3);
        flow.getStyleClass().add("release-notes-h" + level);
        return wrappedFlow(flow);
    }

    private Node bulletNode(BulletBlock bullet) {
        Label marker = new Label("•");
        marker.getStyleClass().add("release-notes-bullet-marker");
        TextFlow flow = inlinesToFlow(bullet.inlines());
        HBox.setHgrow(flow, Priority.ALWAYS);
        flow.maxWidthProperty().bind(widthProperty().subtract(48));
        HBox row = new HBox(6, marker, flow);
        row.getStyleClass().add("release-notes-item");
        return row;
    }

    private Node codeNode(CodeBlock code) {
        Label label = new Label(code.literal() == null ? "" : code.literal().stripTrailing());
        label.getStyleClass().add("release-notes-code");
        label.setWrapText(true);
        label.maxWidthProperty().bind(widthProperty().subtract(24));
        return label;
    }

    private TextFlow wrappedFlow(TextFlow flow) {
        flow.maxWidthProperty().bind(widthProperty().subtract(24));
        return flow;
    }

    private TextFlow inlinesToFlow(List<Inline> inlines) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("release-notes-flow");
        addInlines(flow, inlines, false, false);
        return flow;
    }

    private void addInlines(TextFlow flow, List<Inline> inlines, boolean strong, boolean emphasis) {
        for (Inline inline : inlines) {
            switch (inline) {
            case TextRun t -> flow.getChildren().add(textNode(t.text(), strong, emphasis));
            case Strong s -> addInlines(flow, s.children(), true, emphasis);
            case Emph e -> addInlines(flow, e.children(), strong, true);
            case LineBreak _ -> flow.getChildren().add(new Text("\n"));
            case ReleaseNotesMarkdown.Hyperlink h -> flow.getChildren().add(hyperlink(h));
            }
        }
    }

    private static Text textNode(String text, boolean strong, boolean emphasis) {
        Text node = new Text(text);
        if (strong) {
            node.getStyleClass().add("release-notes-strong");
        }
        if (emphasis) {
            node.getStyleClass().add("release-notes-emphasis");
        }
        return node;
    }

    private Hyperlink hyperlink(ReleaseNotesMarkdown.Hyperlink link) {
        String label = ReleaseNotesMarkdown.plainText(link.children());
        if (label.isEmpty()) {
            label = link.destination();
        }
        Hyperlink hyperlink = new Hyperlink(label);
        hyperlink.setFocusTraversable(false);
        hyperlink.setWrapText(true);
        String destination = link.destination();
        hyperlink.setOnAction(e -> openExternal(destination));
        return hyperlink;
    }

    private static void openExternal(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            log.warn("ブラウザを開くのに失敗しました: {}", url, e);
        }
    }
}
