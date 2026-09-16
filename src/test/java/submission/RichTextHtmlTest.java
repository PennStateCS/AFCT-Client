package submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The TipTap-envelope-to-Swing-HTML rules: the bounded vocabulary maps, student
 * text is escaped, unknown nodes degrade to their words, and anything that is
 * not a version-1 envelope returns null so the caller falls back to plain text.
 */
class RichTextHtmlTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String content) {
        try {
            return MAPPER.readTree("{\"version\":1,\"document\":{\"type\":\"doc\",\"content\":[" + content + "]}}");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String render(String content) {
        return RichTextHtml.render(json(content), RichTextHtml.MATH_AS_CODE);
    }

    private static String text(String t) {
        return "{\"type\":\"text\",\"text\":\"" + t + "\"}";
    }

    // ── envelope gate ────────────────────────────────────────────────────────

    @Test
    void nonEnvelopesReturnNullSoTheCallerFallsBack() throws Exception {
        assertNull(RichTextHtml.render(null, RichTextHtml.MATH_AS_CODE));
        assertNull(RichTextHtml.render(MAPPER.readTree("null"), RichTextHtml.MATH_AS_CODE));
        assertNull(RichTextHtml.render(MAPPER.readTree("{\"version\":2,\"document\":{\"type\":\"doc\"}}"),
                RichTextHtml.MATH_AS_CODE), "A future format version must fall back, not half-render");
        assertNull(RichTextHtml.render(MAPPER.readTree("{\"version\":1,\"document\":{\"type\":\"weird\"}}"),
                RichTextHtml.MATH_AS_CODE));
    }

    // ── blocks ───────────────────────────────────────────────────────────────

    @Test
    void paragraphsHeadingsAndAlignment() {
        assertEquals("<p>hi</p>", render("{\"type\":\"paragraph\",\"content\":[" + text("hi") + "]}"));
        assertEquals("<h3>t</h3>", render(
                "{\"type\":\"heading\",\"attrs\":{\"level\":3},\"content\":[" + text("t") + "]}"));
        assertEquals("<p align=\"center\">c</p>", render(
                "{\"type\":\"paragraph\",\"attrs\":{\"textAlign\":\"center\"},\"content\":[" + text("c") + "]}"));
        // Levels outside the editor's 2-4 clamp to h2 rather than escaping the outline.
        assertEquals("<h2>x</h2>", render(
                "{\"type\":\"heading\",\"attrs\":{\"level\":1},\"content\":[" + text("x") + "]}"));
    }

    @Test
    void listsUnwrapTheParagraphInsideEachItem() {
        String html = render("{\"type\":\"bulletList\",\"content\":["
                + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[" + text("one") + "]}]},"
                + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[" + text("two") + "]}]}]}");
        assertEquals("<ul><li>one</li><li>two</li></ul>", html,
                "The wrapper <p> would double-space every bullet in HTMLEditorKit");
    }

    @Test
    void blockquoteRuleBreakAndCodeBlock() {
        assertEquals("<blockquote><p>q</p></blockquote>", render(
                "{\"type\":\"blockquote\",\"content\":[{\"type\":\"paragraph\",\"content\":[" + text("q") + "]}]}"));
        assertEquals("<hr>", render("{\"type\":\"horizontalRule\"}"));
        assertEquals("<p>a<br>b</p>", render(
                "{\"type\":\"paragraph\",\"content\":[" + text("a") + ",{\"type\":\"hardBreak\"}," + text("b") + "]}"));
        assertEquals("<pre>if (x &lt; y)</pre>", render(
                "{\"type\":\"codeBlock\",\"content\":[" + text("if (x < y)") + "]}"));
    }

    // ── marks ────────────────────────────────────────────────────────────────

    @Test
    void marksNestAndEscapeTheText() {
        String html = render("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"a<b\","
                + "\"marks\":[{\"type\":\"bold\"},{\"type\":\"italic\"}]}]}");
        assertEquals("<p><b><i>a&lt;b</i></b></p>", html);
    }

    @Test
    void linksKeepSafeHrefsAndDropTheRest() {
        String linked = render("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"docs\","
                + "\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":\"https://example.edu/x\"}}]}]}");
        assertEquals("<p><a href=\"https://example.edu/x\">docs</a></p>", linked);

        // The server refuses these at save time; a value that never went through
        // it must render as plain text, not as a live link.
        String hostile = render("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\","
                + "\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":\"javascript:alert(1)\"}}]}]}");
        assertEquals("<p>x</p>", hostile);
    }

    // ── math (as code, until a typesetter lands) ─────────────────────────────

    @Test
    void mathRendersAsEscapedCode() {
        assertEquals("<p>see <code>$\\Sigma^*$</code></p>", render(
                "{\"type\":\"paragraph\",\"content\":[" + text("see ")
                        + ",{\"type\":\"inlineMath\",\"attrs\":{\"latex\":\"\\\\Sigma^*\"}}]}"));
        assertEquals("<p><code>$$a &lt; b$$</code></p>", render(
                "{\"type\":\"blockMath\",\"attrs\":{\"latex\":\"a < b\"}}"));
    }

    // ── forward compatibility ────────────────────────────────────────────────

    @Test
    void unknownNodesKeepTheirWords() {
        String html = render("{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"brandNewNode\",\"content\":[" + text("kept") + "]}]}");
        assertEquals("<p>kept</p>", html,
                "A node type from a newer server loses its formatting, never its words");
    }

    @Test
    void unknownMarksLeaveTheTextUnwrapped() {
        String html = render("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"t\","
                + "\"marks\":[{\"type\":\"sparkles\"}]}]}");
        assertEquals("<p>t</p>", html);
    }

    @Test
    void aHostileDeepDocumentIsRefusedNotRendered() throws Exception {
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            open.append("{\"type\":\"blockquote\",\"content\":[");
            close.append("]}");
        }
        JsonNode deep = MAPPER.readTree("{\"version\":1,\"document\":{\"type\":\"doc\",\"content\":["
                + open + text("x") + close + "]}}");
        assertNull(RichTextHtml.render(deep, RichTextHtml.MATH_AS_CODE),
                "Past the structural bounds the whole description falls back");
    }
}
