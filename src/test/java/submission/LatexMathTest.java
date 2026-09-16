package submission;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.net.URL;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JLaTeXMath bridge: parseable LaTeX becomes an image the pane can resolve,
 * and unparseable LaTeX degrades to the code fallback rather than blank.
 * Rendering runs headless (pure Java2D), so these execute in CI.
 */
class LatexMathTest {

    @Test
    void inlineFormulaBecomesACachedImage() {
        LatexMath math = new LatexMath();
        String html = math.inline("\\Sigma^*");
        assertTrue(html.startsWith("<img src=\"file:/afct-latex/"), html);

        Enumeration<URL> keys = math.imageCache().keys();
        assertTrue(keys.hasMoreElements(), "The image must be in the cache the pane installs");
        Image image = math.imageCache().get(keys.nextElement());
        assertTrue(image.getWidth(null) > 0 && image.getHeight(null) > 0);
        assertTrue(html.contains("width=\"" + image.getWidth(null) + "\""),
                "The tag pre-sizes the image so the layout does not jump");
    }

    @Test
    void blockFormulaIsCenteredDisplayMath() {
        LatexMath math = new LatexMath();
        String html = math.block("\\frac{a}{b}");
        assertTrue(html.startsWith("<p align=\"center\"><img "), html);
    }

    @Test
    void eachFormulaGetsItsOwnCacheEntry() {
        LatexMath math = new LatexMath();
        math.inline("a");
        math.inline("b");
        int n = 0;
        for (Enumeration<URL> k = math.imageCache().keys(); k.hasMoreElements(); k.nextElement()) n++;
        assertEquals(2, n);
    }

    @Test
    void unparseableLatexFallsBackToCodeNotBlank() {
        LatexMath math = new LatexMath();
        assertEquals("<code>$\\notarealcommand{x$</code>",
                math.inline("\\notarealcommand{x"));
        assertEquals("<p><code>$$\\notarealcommand{x$$</code></p>",
                math.block("\\notarealcommand{x"));
    }
}
