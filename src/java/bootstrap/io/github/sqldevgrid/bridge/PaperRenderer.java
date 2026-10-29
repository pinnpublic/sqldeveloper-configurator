package io.github.sqldevgrid.bridge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.Objects;

/** Vector geometry is rasterized anew at each requested native DPI; images are never enlarged. */
public final class PaperRenderer {
    public static final int HIGHLIGHT_ALPHA = 128;
    private PaperRenderer() { }

    public static final class Highlight {
        public final double top, bottom;
        public final RGB color;
        public Highlight(double top, double bottom, RGB color) { this.top = top; this.bottom = bottom; this.color = color; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Highlight)) return false;
            Highlight h = (Highlight) other; return top == h.top && bottom == h.bottom && color.equals(h.color);
        }
        @Override public int hashCode() { return Objects.hash(top,bottom,color); }
    }

    public static final class Layout {
        public final int width, height, fontHeight, rowHeight, horizontalPixel;
        public final double originY;
        public final double firstTab, secondTab;
        public final Highlight highlight;
        public final double[] rules;
        public final int paperOffsetX, paperOffsetY;
        public Layout(int width, int height, int fontHeight, int rowHeight, int horizontalPixel,
                double originY, double[] rules) {
            this(width,height,fontHeight,rowHeight,horizontalPixel,originY,rules,
                fontHeight * 2.4 - horizontalPixel,rowHeight * 4.0 - horizontalPixel,null,0,0);
        }
        private Layout(int width, int height, int fontHeight, int rowHeight, int horizontalPixel,
                double originY, double[] rules, double firstTab, double secondTab, Highlight highlight,int offsetX,int offsetY) {
            this.width = Math.max(1, width); this.height = Math.max(1, height);
            this.fontHeight = Math.max(1, fontHeight); this.rowHeight = Math.max(1, rowHeight);
            this.horizontalPixel = horizontalPixel; this.originY = originY; this.rules = rules.clone();
            this.firstTab = firstTab; this.secondTab = secondTab; this.highlight = highlight;
            this.paperOffsetX=offsetX;this.paperOffsetY=offsetY;
        }
        public Layout withGeometry(double firstTab, double secondTab, Highlight highlight) {
            return new Layout(width,height,fontHeight,rowHeight,horizontalPixel,originY,rules,firstTab,secondTab,highlight,paperOffsetX,paperOffsetY);
        }
        public Layout forRuler(int width,int height,int x,int y) {
            double[] shifted=rules.clone();for(int i=0;i<shifted.length;i++)shifted[i]+=y;
            Highlight h=highlight==null?null:new Highlight(highlight.top+y,highlight.bottom+y,highlight.color);
            return new Layout(width,height,fontHeight,rowHeight,horizontalPixel,originY+y,shifted,firstTab+x,secondTab+x,h,x,y);
        }
        /** Geometry that can affect the background raster; scrolling rules are a separate layer. */
        public Layout backgroundKey(NotebookSettings settings) {
            return layerKey(settings,0);
        }
        /** Geometry that can affect the transparent rule raster, independent of the paper material. */
        public Layout rulesKey(NotebookSettings settings) {
            return layerKey(settings,1);
        }
        /** Geometry that can affect the combined paper raster used by native ruler overlays. */
        public Layout fullKey(NotebookSettings settings) {
            return layerKey(settings,2);
        }
        private Layout layerKey(NotebookSettings settings,int layer) {
            boolean rulesOnly=layer==1;
            // These paper styles have no vertical pattern. Their texture is fixed to the viewport.
            double origin=rulesOnly||settings.paper==0||settings.paper==1||settings.paper==7?0:originY;
            Highlight h=rulesOnly?null:highlight;
            // Keep a generous guard for stroke snapping around the highlight's bounds.
            if(h!=null&&(h.bottom < -fontHeight*2.0||h.top > height+fontHeight*2.0))h=null;
            return new Layout(width,height,fontHeight,rowHeight,horizontalPixel,origin,
                layer==0||!settings.lines?new double[0]:rules,
                rulesOnly?0:firstTab,rulesOnly?0:secondTab,h,
                rulesOnly?0:paperOffsetX,rulesOnly?0:paperOffsetY);
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Layout)) return false;
            Layout l = (Layout) other;
            return width == l.width && height == l.height && fontHeight == l.fontHeight
                && rowHeight == l.rowHeight && horizontalPixel == l.horizontalPixel
                && originY == l.originY && Arrays.equals(rules, l.rules)
                && firstTab == l.firstTab && secondTab == l.secondTab && Objects.equals(highlight,l.highlight)
                && paperOffsetX==l.paperOffsetX && paperOffsetY==l.paperOffsetY;
        }
        @Override public int hashCode() { return Arrays.hashCode(rules) * 31 + width + height; }
    }

    public static BufferedImage render(NotebookSettings s, Layout l, RGB background, int zoom) {
        return render(s, l, background, zoom, 2);
    }
    public static BufferedImage renderBackground(NotebookSettings s, Layout l, RGB background, int zoom) {
        return render(s, l, background, zoom, 0);
    }
    public static BufferedImage renderRules(NotebookSettings s, Layout l, RGB background, int zoom) {
        return render(s, l, background, zoom, 1);
    }
    private static BufferedImage render(NotebookSettings s, Layout l, RGB background, int zoom, int layer) {
        double z = zoom / 100.0;
        int width = Math.max(1, (int) Math.round(l.width * z));
        int height = Math.max(1, (int) Math.round(l.height * z));
        BufferedImage image = new BufferedImage(width, height,
            layer == 1 ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            if (layer != 1) PaperTextures.fill(g, s, background, zoom, width, height,l.paperOffsetX,l.paperOffsetY);
            g.scale(z, z);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            if (s.enabled) {
                double cell = l.rowHeight * s.scale / 100.0;
                double originY = l.originY;
                double originX = -l.horizontalPixel+l.paperOffsetX;
                double thin = Math.max(1, Math.round(l.fontHeight * 0.025 * z)) / z;
                g.setColor(blend(s.patternColor, background, s.patternOpacity));
                g.setStroke(new BasicStroke((float) thin));
                if (layer != 1) drawPaper(g, s.paper, l, cell, originX, originY, thin, z, s, background);
                if (layer != 1 && l.highlight != null) {
                    double stroke = Math.max(1, Math.round(l.fontHeight * s.width / 100.0 * z)) / z;
                    double top = s.lines?snap(l.highlight.top,stroke,z) + stroke / 2:l.highlight.top;
                    double bottom = s.lines?snap(l.highlight.bottom,stroke,z) + stroke / 2:l.highlight.bottom;
                    RGB tint = l.highlight.color;
                    g.setColor(new Color(tint.red,tint.green,tint.blue,(int)Math.round(s.highlightOpacity*255/100.0)));
                    g.fill(new Rectangle2D.Double(0,top,l.width,Math.max(0,bottom-top)));
                }
                if (s.lines && layer != 0) {
                    g.setColor(blend(s.lineColor, background, s.lineOpacity));
                    double stroke = Math.max(1, Math.round(l.fontHeight * s.width / 100.0 * z)) / z;
                    for (double y : l.rules) rule(g, s.lineStyle, l, y, stroke, z);
                }
            }
        } finally { g.dispose(); }
        return image;
    }

    private static void drawPaper(Graphics2D g, int paper, Layout l, double cell, double ox, double oy,
            double thin, double z, NotebookSettings s, RGB bg) {
        double firstX = modulo(ox, cell) - cell, firstY = modulo(oy, cell) - cell;
        switch (paper) {
        case 0: break;
        case 1: // Traditional notebook margin; the horizontal rules are independent settings.
            g.setColor(blend(new RGB(191, 119, 116), bg, s.patternOpacity));
            vertical(g, l.firstTab - 5, l.height, thin, z);
            break;
        case 2: grid(g, l, cell, ox, oy, thin, z, true, true); break;
        case 3: grid(g, l, cell / 2, ox, oy, thin, z, true, true); break;
        case 4:
            g.setColor(blend(s.patternColor, bg, Math.max(1, s.patternOpacity / 2)));
            grid(g, l, cell / 2, ox, oy, thin, z, true, true);
            g.setColor(blend(s.patternColor, bg, s.patternOpacity));
            grid(g, l, cell * 2.5, ox, oy, thin, z, true, true);
            break;
        case 5: case 6:
            double radius = Math.max(0.65 / z, l.fontHeight * 0.035);
            for (double y = firstY; y <= l.height; y += cell) {
                for (double x = firstX; x <= l.width; x += cell) {
                    if (paper == 5) g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
                    else {
                        double arm = l.fontHeight * 0.11;
                        g.draw(new Line2D.Double(x - arm, y, x + arm, y));
                        g.draw(new Line2D.Double(x, y - arm, x, y + arm));
                    }
                }
            }
            break;
        case 7: grid(g, l, cell, ox, oy, thin, z, true, false); break;
        case 8: // Three axes at 60 degrees.
            grid(g, l, cell, ox, oy, thin, z, false, true);
            diagonals(g, l, cell * 2 / Math.sqrt(3), ox, oy, Math.sqrt(3));
            break;
        case 9: diagonals(g, l, cell, ox, oy, 1); break;
        case 10: // Manuscript boxes with a small gutter between rows.
            for (double y = firstY; y <= l.height; y += cell) {
                for (double x = firstX; x <= l.width; x += cell) {
                    double inset = cell * 0.07;
                    g.draw(new Rectangle2D.Double(snap(x, thin, z), snap(y + inset, thin, z),
                        cell, cell - inset * 2));
                }
            }
            break;
        case 11:
            vertical(g, l.secondTab - 5, l.height, thin, z);
            // Repeating summary dividers stay attached to the document during scrolling.
            for (double y = modulo(oy, cell * 9) - cell * 9; y <= l.height; y += cell * 9)
                horizontal(g, y, l.width, thin, z);
            break;
        default: break;
        }
    }

    private static void diagonals(Graphics2D g, Layout l, double cell, double ox, double oy, double slope) {
        double positive = modulo(ox - oy / slope, cell);
        double negative = modulo(ox + oy / slope, cell);
        for (double x = positive - l.height / slope - cell; x < l.width + l.height / slope + cell; x += cell)
            g.draw(new Line2D.Double(x, 0, x + l.height / slope, l.height));
        for (double x = negative - l.height / slope - cell; x < l.width + l.height / slope + cell; x += cell)
            g.draw(new Line2D.Double(x, 0, x - l.height / slope, l.height));
    }
    private static void grid(Graphics2D g, Layout l, double cell, double ox, double oy,
            double thin, double z, boolean v, boolean h) {
        if (v) for (double x = modulo(ox, cell) - cell; x <= l.width; x += cell) vertical(g, x, l.height, thin, z);
        if (h) for (double y = modulo(oy, cell) - cell; y <= l.height; y += cell) horizontal(g, y, l.width, thin, z);
    }
    private static void rule(Graphics2D g, int style, Layout l, double y, double width, double z) {
        float unit = (float) Math.max(1.0 / z, l.fontHeight * 0.12);
        float[] dash = null;
        int cap = BasicStroke.CAP_BUTT;
        switch (style) {
        case 1: dash = new float[] { (float) width, unit * 2 }; cap = BasicStroke.CAP_ROUND; break;
        case 2: dash = new float[] { (float) width * 1.4f, unit * 3.5f }; cap = BasicStroke.CAP_ROUND; break;
        case 3: dash = new float[] { unit * 2, unit * 2 }; break;
        case 4: dash = new float[] { unit * 6, unit * 3 }; break;
        case 5: dash = new float[] { unit * 5, unit * 2, unit, unit * 2 }; break;
        case 6: dash = new float[] { unit * 5, unit * 2, unit, unit * 2, unit, unit * 2 }; break;
        default: break;
        }
        g.setStroke(new BasicStroke((float) width, cap, BasicStroke.JOIN_ROUND, 10, dash,
            dash == null ? 0 : (float) modulo(l.horizontalPixel, sum(dash))));
        if (style == 7 || style == 8) {
            double sep = Math.max(width * 2.2, l.fontHeight * 0.10);
            horizontal(g, y, l.width, width, z);
            horizontal(g, y + sep, l.width, width, z);
            if (style == 8) horizontal(g, y + sep * 2, l.width, width, z);
        } else if (style == 9) {
            double wavelength = l.fontHeight * 0.8, amplitude = l.fontHeight * 0.045;
            Path2D path = new Path2D.Double();
            double x = -modulo(l.horizontalPixel, wavelength) - wavelength;
            y += amplitude;
            path.moveTo(x, y);
            for (; x < l.width; x += wavelength) {
                path.curveTo(x + wavelength / 6, y - amplitude, x + wavelength / 3, y - amplitude,
                    x + wavelength / 2, y);
                path.curveTo(x + wavelength * 2 / 3, y + amplitude, x + wavelength * 5 / 6,
                    y + amplitude, x + wavelength, y);
            }
            g.draw(path);
        } else horizontal(g, y, l.width, width, z);
    }
    private static double sum(float[] values) { double n = 0; for (float value : values) n += value; return n; }
    private static double modulo(double n, double d) { return ((n % d) + d) % d; }
    private static double snap(double n, double stroke, double zoom) {
        double half = ((int) Math.round(stroke * zoom) % 2) * 0.5;
        return (Math.rint(n * zoom - half) + half) / zoom;
    }
    private static void horizontal(Graphics2D g, double y, double width, double stroke, double zoom) {
        double snapped = snap(y, stroke, zoom); g.draw(new Line2D.Double(0, snapped, width, snapped));
    }
    private static void vertical(Graphics2D g, double x, double height, double stroke, double zoom) {
        double snapped = snap(x, stroke, zoom); g.draw(new Line2D.Double(snapped, 0, snapped, height));
    }
    private static Color color(RGB c) { return new Color(c.red, c.green, c.blue); }
    private static Color blend(RGB ink, RGB paper, int opacity) {
        double a = opacity / 100.0;
        return new Color((int) Math.round(ink.red * a + paper.red * (1 - a)),
            (int) Math.round(ink.green * a + paper.green * (1 - a)),
            (int) Math.round(ink.blue * a + paper.blue * (1 - a)));
    }
}
