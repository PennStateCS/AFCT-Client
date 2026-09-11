package submission;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Renders a rich description (the server's validated TipTap envelope, version 1)
 * to the HTML 3.2 dialect Swing's HTMLEditorKit actually displays. The server's
 * schema bounds the vocabulary — paragraphs, headings h2-h4, lists, blockquote,
 * rule, break, code block, bold/italic/underline/code/link, left/center/right —
 * and each maps 1:1 here. Anything a NEWER server invents degrades to its text
 * content rather than failing the whole description.
 *
 * Math nodes carry raw LaTeX in an attribute; how they render is a strategy so
 * the plain build (LaTeX shown as code, exactly what students see today) and a
 * typeset build can coexist.
 */
public final class RichTextHtml {

    /** How a math node becomes HTML. Input is raw LaTeX; output must be HTML-safe. */
    public interface MathHtml {
        String inline(String latex);

        String block(String latex);
    }

    /** LaTeX shown as code: no dependency, and no worse than the plain projection. */
    public static final MathHtml MATH_AS_CODE = new MathHtml() {
        @Override
        public String inline(String latex) {
            return "<code>$" + DetailsHtml.escapeHtml(latex) + "$</code>";
        }

        @Override
        public String block(String latex) {
            return "<p><code>$$" + DetailsHtml.escapeHtml(latex) + "$$</code></p>";
        }
    };

    // A hair above the server's save-time bounds (depth 20, 5000 nodes): a
    // legitimate document never hits these, a hostile payload stops here.
    private static final int MAX_DEPTH = 25;
    private static final int MAX_NODES = 6000;

    private RichTextHtml() {}

    /**
     * The description as HTMLEditorKit-ready body markup, or null when the value
     * is not a version-1 envelope — the caller then falls back to the plain-text
     * projection the server always sends alongside.
     */
    public static String render(JsonNode envelope, MathHtml math) {
        if (envelope == null || envelope.isNull()) return null;
        if (envelope.path("version").asInt(-1) != 1) return null;
        JsonNode document = envelope.path("document");
        if (!"doc".equals(document.path("type").asText())) return null;

        StringBuilder out = new StringBuilder();
        int budget = renderChildren(document, out, math, 0, MAX_NODES);
        if (budget < 0) return null; // over the structural bounds: treat as unrenderable
        return out.toString();
    }

    /** Renders a node's children; returns the remaining node budget, or -1 on overflow. */
    private static int renderChildren(JsonNode node, StringBuilder out, MathHtml math,
                                      int depth, int budget) {
        JsonNode content = node.path("content");
        if (!content.isArray()) return budget;
        for (JsonNode child : content) {
            budget = renderNode(child, out, math, depth + 1, budget);
            if (budget < 0) return -1;
        }
        return budget;
    }

    private static int renderNode(JsonNode node, StringBuilder out, MathHtml math,
                                  int depth, int budget) {
        if (depth > MAX_DEPTH || --budget < 0) return -1;
        String type = node.path("type").asText("");
        switch (type) {
            case "paragraph" -> {
                out.append("<p").append(alignAttr(node)).append('>');
                budget = renderChildren(node, out, math, depth, budget);
                out.append("</p>");
            }
            case "heading" -> {
                int level = node.path("attrs").path("level").asInt(2);
                if (level < 2 || level > 4) level = 2;
                out.append("<h").append(level).append(alignAttr(node)).append('>');
                budget = renderChildren(node, out, math, depth, budget);
                out.append("</h").append(level).append('>');
            }
            case "bulletList" -> {
                out.append("<ul>");
                budget = renderChildren(node, out, math, depth, budget);
                out.append("</ul>");
            }
            case "orderedList" -> {
                out.append("<ol>");
                budget = renderChildren(node, out, math, depth, budget);
                out.append("</ol>");
            }
            case "listItem" -> {
                out.append("<li>");
                budget = renderListItemContent(node, out, math, depth, budget);
                out.append("</li>");
            }
            case "blockquote" -> {
                out.append("<blockquote>");
                budget = renderChildren(node, out, math, depth, budget);
                out.append("</blockquote>");
            }
            case "horizontalRule" -> out.append("<hr>");
            case "hardBreak" -> out.append("<br>");
            case "codeBlock" -> {
                out.append("<pre>");
                budget = renderChildren(node, out, math, depth, budget);
                out.append("</pre>");
            }
            case "text" -> out.append(markedText(node));
            case "inlineMath" -> out.append(math.inline(latexOf(node)));
            case "blockMath" -> out.append(math.block(latexOf(node)));
            default ->
                // A node type this build has never heard of: keep its text so the
                // student loses formatting, not words.
                budget = renderChildren(node, out, math, depth, budget);
        }
        return budget;
    }

    /**
     * TipTap wraps a list item's text in a paragraph; rendering that <p> inside
     * <li> makes HTMLEditorKit double-space every bullet, so a single paragraph
     * child is unwrapped to its inline content.
     */
    private static int renderListItemContent(JsonNode item, StringBuilder out, MathHtml math,
                                             int depth, int budget) {
        JsonNode content = item.path("content");
        if (content.isArray() && content.size() == 1
                && "paragraph".equals(content.get(0).path("type").asText())) {
            return renderChildren(content.get(0), out, math, depth + 1, budget - 1);
        }
        return renderChildren(item, out, math, depth, budget);
    }

    private static String latexOf(JsonNode node) {
        return node.path("attrs").path("latex").asText("");
    }

    private static String alignAttr(JsonNode node) {
        String align = node.path("attrs").path("textAlign").asText("");
        // The legacy align attribute, which HTMLEditorKit honors more reliably
        // than CSS text-align. Left is the default and stays unmarked.
        return "center".equals(align) || "right".equals(align)
                ? " align=\"" + align + "\"" : "";
    }

    private static String markedText(JsonNode node) {
        String text = DetailsHtml.escapeHtml(node.path("text").asText(""));
        JsonNode marks = node.path("marks");
        if (!marks.isArray()) return text;
        // Wrap inside-out so the first mark ends up outermost; order is cosmetic.
        for (int i = marks.size() - 1; i >= 0; i--) {
            JsonNode mark = marks.get(i);
            switch (mark.path("type").asText("")) {
                case "bold" -> text = "<b>" + text + "</b>";
                case "italic" -> text = "<i>" + text + "</i>";
                case "underline" -> text = "<u>" + text + "</u>";
                case "code" -> text = "<code>" + text + "</code>";
                case "link" -> {
                    String href = mark.path("attrs").path("href").asText("");
                    // The server validates hrefs at save time; this is the belt for a
                    // value that never went through it. Anything else renders as text.
                    if (href.startsWith("https://") || href.startsWith("http://")
                            || href.startsWith("mailto:")) {
                        text = "<a href=\"" + DetailsHtml.escapeHtml(href) + "\">" + text + "</a>";
                    }
                }
                default -> { /* unknown mark: keep the text unwrapped */ }
            }
        }
        return text;
    }
}
