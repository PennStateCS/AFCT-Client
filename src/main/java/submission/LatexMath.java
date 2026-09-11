package submission;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * Typesets the LaTeX in rich descriptions with JLaTeXMath and hands the results
 * to HTMLEditorKit as images: the renderer emits {@code <img>} tags with
 * synthetic URLs, and the pane's document resolves them through the
 * {@code "imageCache"} property this class fills. One instance per rendered
 * description; the cache travels with the HTML it belongs to.
 *
 * JLaTeXMath covers most of what KaTeX accepts, not all of it. A formula it
 * cannot parse falls back to the LaTeX shown as code, which is exactly what the
 * plain projection showed — degraded, never blank.
 */
class LatexMath implements RichTextHtml.MathHtml {

    private static final float INLINE_SIZE = 15f;
    private static final float BLOCK_SIZE = 18f;

    private final Dictionary<URL, Image> images = new Hashtable<>();
    private int counter = 0;

    /** The image cache to install as the pane document's "imageCache" property. */
    Dictionary<URL, Image> imageCache() {
        return images;
    }

    @Override
    public String inline(String latex) {
        return imgOrFallback(latex, TeXConstants.STYLE_TEXT, INLINE_SIZE,
                () -> RichTextHtml.MATH_AS_CODE.inline(latex));
    }

    @Override
    public String block(String latex) {
        String img = imgOrFallback(latex, TeXConstants.STYLE_DISPLAY, BLOCK_SIZE, null);
        return img != null ? "<p align=\"center\">" + img + "</p>"
                : RichTextHtml.MATH_AS_CODE.block(latex);
    }

    /**
     * An {@code <img>} tag whose URL resolves through the cache, or the fallback
     * when the LaTeX does not parse (null fallback returns null instead).
     */
    private String imgOrFallback(String latex, int style, float size,
                                 java.util.function.Supplier<String> fallback) {
        try {
            TeXIcon icon = new TeXFormula(latex).createTeXIcon(style, size);
            BufferedImage image = new BufferedImage(
                    Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            icon.setForeground(Color.BLACK);
            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();

            // A synthetic but well-formed URL: HTMLEditorKit looks it up in the
            // document's imageCache before ever trying to open it.
            URL key = new URL("file:/afct-latex/" + (counter++) + ".png");
            images.put(key, image);
            return "<img src=\"" + key + "\" width=\"" + image.getWidth()
                    + "\" height=\"" + image.getHeight() + "\">";
        } catch (MalformedURLException impossible) {
            throw new IllegalStateException(impossible);
        } catch (Exception unparseable) {
            return fallback != null ? fallback.get() : null;
        }
    }
}
