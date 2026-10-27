package logbook.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

/**
 * リリースノート Markdown の解析。
 *
 * <p>JavaFX に依存せず、commonmark の AST をブロック／インラインへ変換する。
 * 裸の {@code https://} URL はオートリンク化する（commonmark-ext-autolink は使わない）。</p>
 */
public final class ReleaseNotesMarkdown {

    private static final Parser PARSER = Parser.builder().build();

    /** 本文中の裸 URL。末尾の句読点は後で削る。 */
    private static final Pattern BARE_URL = Pattern.compile("https?://[^\\s\\]]+");

    private static final String TRAILING_PUNCT = ".,;:!?）)";

    private ReleaseNotesMarkdown() {
    }

    /**
     * Markdown をブロック列へ変換する。
     *
     * @param markdown リリースノート本文（null 可）
     * @return ブロック列。空または空白のみのときは空リスト
     */
    public static List<Block> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        BlockCollector collector = new BlockCollector();
        PARSER.parse(markdown).accept(collector);
        return List.copyOf(collector.blocks);
    }

    /**
     * 解析結果のテキストダンプ。JavaFX を起動せずに検証するために使う。
     *
     * @param markdown リリースノート本文（null 可）
     * @return 1 行 1 ブロック（およびその中の LINK）のダンプ。空のときは {@code EMPTY}
     */
    static String dump(String markdown) {
        List<Block> blocks = parse(markdown);
        if (blocks.isEmpty()) {
            return "EMPTY";
        }
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            List<String> links = new ArrayList<>();
            sb.append(blockPrefix(block)).append(flatten(block.inlines(), links)).append('\n');
            for (String href : links) {
                sb.append("LINK: ").append(href).append('\n');
            }
        }
        return sb.toString();
    }

    private static String blockPrefix(Block block) {
        return switch (block) {
            case HeadingBlock h -> "H" + h.level() + ": ";
            case ParagraphBlock _ -> "P: ";
            case BulletBlock _ -> "LI: ";
            case CodeBlock _ -> "CODE: ";
        };
    }

    /**
     * インラインをプレーンテキストへ平坦化する。
     *
     * @param inlines インライン列
     * @return 結合した文字列
     */
    public static String plainText(List<Inline> inlines) {
        return flatten(inlines, new ArrayList<>());
    }

    static String flatten(List<Inline> inlines, List<String> links) {
        StringBuilder sb = new StringBuilder();
        appendFlattened(inlines, sb, links);
        return sb.toString();
    }

    private static void appendFlattened(List<Inline> inlines, StringBuilder sb, List<String> links) {
        for (Inline inline : inlines) {
            switch (inline) {
            case TextRun t -> sb.append(t.text());
            case Strong s -> appendFlattened(s.children(), sb, links);
            case Emph e -> appendFlattened(e.children(), sb, links);
            case LineBreak _ -> sb.append('\n');
            case Hyperlink h -> {
                appendFlattened(h.children(), sb, links);
                links.add(h.destination());
            }
            }
        }
    }

    public sealed interface Block permits HeadingBlock, ParagraphBlock, BulletBlock, CodeBlock {
        List<Inline> inlines();
    }

    public record HeadingBlock(int level, List<Inline> inlines) implements Block {
    }

    public record ParagraphBlock(List<Inline> inlines) implements Block {
    }

    public record BulletBlock(List<Inline> inlines) implements Block {
    }

    public record CodeBlock(String literal) implements Block {
        @Override
        public List<Inline> inlines() {
            return List.of(new TextRun(literal == null ? "" : literal.stripTrailing()));
        }
    }

    public sealed interface Inline permits TextRun, Strong, Emph, Hyperlink, LineBreak {
    }

    public record TextRun(String text) implements Inline {
    }

    public record Strong(List<Inline> children) implements Inline {
    }

    public record Emph(List<Inline> children) implements Inline {
    }

    public record Hyperlink(String destination, List<Inline> children) implements Inline {
    }

    public record LineBreak() implements Inline {
    }

    private static final class BlockCollector extends AbstractVisitor {

        private final List<Block> blocks = new ArrayList<>();

        @Override
        public void visit(Heading heading) {
            this.blocks.add(new HeadingBlock(heading.getLevel(), inlines(heading)));
        }

        @Override
        public void visit(Paragraph paragraph) {
            if (isInsideListItem(paragraph)) {
                return;
            }
            this.blocks.add(new ParagraphBlock(inlines(paragraph)));
        }

        @Override
        public void visit(BulletList bulletList) {
            visitListItems(bulletList);
        }

        @Override
        public void visit(OrderedList orderedList) {
            visitListItems(orderedList);
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            this.blocks.add(new CodeBlock(fencedCodeBlock.getLiteral()));
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            this.blocks.add(new CodeBlock(indentedCodeBlock.getLiteral()));
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            visitChildren(blockQuote);
        }

        private void visitListItems(Node list) {
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (item instanceof ListItem listItem) {
                    boolean emitted = false;
                    for (Node child = listItem.getFirstChild(); child != null; child = child.getNext()) {
                        if (child instanceof Paragraph paragraph) {
                            this.blocks.add(new BulletBlock(inlines(paragraph)));
                            emitted = true;
                        } else if (child instanceof BulletList || child instanceof OrderedList) {
                            child.accept(this);
                            emitted = true;
                        } else if (child instanceof Heading heading) {
                            this.blocks.add(new HeadingBlock(heading.getLevel(), inlines(heading)));
                            emitted = true;
                        } else if (child instanceof FencedCodeBlock code) {
                            this.blocks.add(new CodeBlock(code.getLiteral()));
                            emitted = true;
                        } else {
                            child.accept(this);
                            emitted = true;
                        }
                    }
                    if (!emitted) {
                        this.blocks.add(new BulletBlock(List.of()));
                    }
                }
            }
        }

        private static boolean isInsideListItem(Node node) {
            for (Node p = node.getParent(); p != null; p = p.getParent()) {
                if (p instanceof ListItem) {
                    return true;
                }
            }
            return false;
        }
    }

    static List<Inline> inlines(Node parent) {
        List<Inline> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text text) {
                result.addAll(splitAutolinks(text.getLiteral()));
            } else if (child instanceof StrongEmphasis strong) {
                result.add(new Strong(inlines(strong)));
            } else if (child instanceof Emphasis emphasis) {
                result.add(new Emph(inlines(emphasis)));
            } else if (child instanceof Link link) {
                result.add(new Hyperlink(link.getDestination(), inlines(link)));
            } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                result.add(new LineBreak());
            } else if (child instanceof Code code) {
                result.add(new TextRun(code.getLiteral()));
            } else if (child instanceof Image image) {
                String alt = flatten(inlines(image), new ArrayList<>());
                result.add(new TextRun(alt.isEmpty() ? image.getDestination() : alt));
            } else {
                result.addAll(inlines(child));
            }
        }
        return List.copyOf(result);
    }

    static List<Inline> splitAutolinks(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Matcher matcher = BARE_URL.matcher(text);
        if (!matcher.find()) {
            return List.of(new TextRun(text));
        }
        List<Inline> result = new ArrayList<>();
        int last = 0;
        matcher.reset();
        while (matcher.find()) {
            if (matcher.start() > last) {
                result.add(new TextRun(text.substring(last, matcher.start())));
            }
            String raw = matcher.group();
            String url = trimTrailingPunct(raw);
            result.add(new Hyperlink(url, List.of(new TextRun(url))));
            if (url.length() < raw.length()) {
                result.add(new TextRun(raw.substring(url.length())));
            }
            last = matcher.end();
        }
        if (last < text.length()) {
            result.add(new TextRun(text.substring(last)));
        }
        return List.copyOf(result);
    }

    private static String trimTrailingPunct(String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCT.indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return end == url.length() ? url : url.substring(0, end);
    }
}
