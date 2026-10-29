package io.github.sqldevgrid.bridge;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.text.*;
import javax.swing.JComponent;

/** Paper is painted by the native UI behind native highlights and glyphs. */
public final class NotebookBridge {
    private static volatile NotebookSettings settings = NotebookSettings.parse("{}");
    private static final String KEY = "sqldev.paper.raster";
    private static final ClassValue<java.lang.reflect.Method> STYLE_NAME=new ClassValue<>() {
        protected java.lang.reflect.Method computeValue(Class<?> type){try{return type.getMethod("getName");}catch(NoSuchMethodException error){throw new IllegalStateException(error);}}
    };
    private record Raster(NotebookSettings settings, PaperRenderer.Layout layout, RGB background, int zoom,
                          BufferedImage paper, BufferedImage rules) { }
    public static void configure(String json) { settings = NotebookSettings.parse(json); }
    public static boolean spaced() { return settings.enabled && settings.spacing > 0; }
    public static int extraSpacing(int nativeHeight) { return settings.enabled ? Math.round(nativeHeight * settings.spacing / 100f) : 0; }
    public static int rowHeight(int nativeHeight) { return nativeHeight + extraSpacing(nativeHeight); }
    public static boolean enabled() { return settings.enabled; }
    public static void paintGutter(Graphics graphics,JComponent gutter,JTextComponent editor) {
        NotebookSettings s=settings;
        if(!s.enabled||editor==null||!(graphics instanceof Graphics2D g))return;
        try {
            Rectangle area=gutter.getVisibleRect(),body=editor.getVisibleRect();
            if(area.width<=0||area.height<=0)return;
            Rectangle first=editor.modelToView2D(0).getBounds();int fontHeight=editor.getFontMetrics(editor.getFont()).getHeight();
            int rowHeight=Math.max(1,first.height);double shift=fontHeight+fontHeight*s.gap/100.0+Math.max(1,fontHeight*s.width/100.0)/2;
            double origin=first.y+shift-body.y,top=origin+Math.floor(-origin/rowHeight)*rowHeight;
            ArrayList<Double> rules=new ArrayList<>();for(double y=top;y<area.height+rowHeight;y+=rowHeight)rules.add(y);
            PaperRenderer.Layout layout=new PaperRenderer.Layout(body.width,body.height,fontHeight,rowHeight,body.x,origin,rules.stream().mapToDouble(Double::doubleValue).toArray())
                .forRuler(area.width,area.height,area.width,body.y-area.y);
            Color nativeColor=editor.getBackground();boolean dark=nativeColor.getRed()*.2126+nativeColor.getGreen()*.7152+nativeColor.getBlue()*.0722<128;
            RGB background=s.theme?new RGB(nativeColor.getRed(),nativeColor.getGreen(),nativeColor.getBlue()):s.paperColor(dark);
            int zoom=Math.max(100,(int)Math.round(Math.abs(g.getTransform().getScaleX())*100));
            Raster cached=(Raster)gutter.getClientProperty(KEY);
            if(cached==null||!s.equals(cached.settings)||!layout.equals(cached.layout)||!background.equals(cached.background)||zoom!=cached.zoom){
                cached=new Raster(s,layout,background,zoom,PaperRenderer.render(s,layout,background,zoom),null);gutter.putClientProperty(KEY,cached);
            }
            g.drawImage(cached.paper,area.x,area.y,area.width,area.height,null);
        }catch(BadLocationException ignored) { }
    }
    public static void paint(Graphics graphics, JTextComponent editor, boolean lines) {
        NotebookSettings s = settings;
        if (!s.enabled || !(graphics instanceof Graphics2D)) return;
        try {
            Rectangle area = editor.getVisibleRect();
            if (area.width <= 0 || area.height <= 0) return;
            Rectangle first = editor.modelToView2D(0).getBounds();
            int fontHeight = editor.getFontMetrics(editor.getFont()).getHeight();
            int rowHeight = Math.max(1, first.height);
            double shift = fontHeight + fontHeight * s.gap / 100.0 + Math.max(1, fontHeight * s.width / 100.0) / 2;
            double origin = first.y + shift - area.y;
            double top = origin + Math.floor(-origin / rowHeight) * rowHeight;
            ArrayList<Double> rules = new ArrayList<>();
            for (double y = top; y < area.height + rowHeight; y += rowHeight) rules.add(y);
            double[] ys = rules.stream().mapToDouble(Double::doubleValue).toArray();
            Object tab = editor.getDocument().getProperty(PlainDocument.tabSizeAttribute);
            try { tab=editor.getClass().getMethod("getProperty",String.class).invoke(editor,"tab-size"); } catch(ReflectiveOperationException ignored) { }
            int tabs = tab instanceof Number n ? Math.max(1, n.intValue()) : 8;
            double tabWidth = tabs * editor.getFontMetrics(editor.getFont()).charWidth(' ');
            PaperRenderer.Layout layout = new PaperRenderer.Layout(area.width, area.height, fontHeight, rowHeight, area.x, origin, ys)
                .withGeometry(first.x + tabWidth - area.x, first.x + tabWidth * 2 - area.x, null);
            Color nativeColor = editor.getBackground();
            boolean dark = nativeColor.getRed() * .2126 + nativeColor.getGreen() * .7152 + nativeColor.getBlue() * .0722 < 128;
            RGB background = s.theme ? new RGB(nativeColor.getRed(), nativeColor.getGreen(), nativeColor.getBlue()) : s.paperColor(dark);
            Graphics2D g = (Graphics2D)graphics;
            int zoom = Math.max(100, (int)Math.round(Math.abs(g.getTransform().getScaleX()) * 100));
            Raster cached = (Raster)editor.getClientProperty(KEY);
            if (cached == null || !s.equals(cached.settings) || !layout.equals(cached.layout) || !background.equals(cached.background) || zoom != cached.zoom) {
                cached = new Raster(s, layout, background, zoom, PaperRenderer.renderBackground(s, layout, background, zoom),
                    s.lines ? PaperRenderer.renderRules(s, layout, background, zoom) : null);
                editor.putClientProperty(KEY, cached);
            }
            BufferedImage image = lines ? cached.rules : cached.paper;
            if (image != null) g.drawImage(image, area.x, area.y, area.width, area.height, null);
        } catch (BadLocationException ignored) { }
    }
    /** Only native highlight fills are adjusted; selected text retains its native background. */
    public static void fillHighlight(Graphics graphics, int x, int y, int width, int height, View view,Object style) {
        NotebookSettings s = settings;
        boolean caret=false;
        if(s.enabled&&style!=null)try{caret="caret-line-highlight".equals(STYLE_NAME.get(style.getClass()).invoke(style));}catch(ReflectiveOperationException ignored) { }
        if (caret && graphics instanceof Graphics2D) {
            Graphics2D copy = (Graphics2D)graphics.create();
            try { copy.setComposite(AlphaComposite.SrcOver.derive(s.highlightOpacity / 100f)); copy.fillRect(x,y,width,height); }
            finally { copy.dispose(); }
        } else graphics.fillRect(x,y,width,height);
    }
}
