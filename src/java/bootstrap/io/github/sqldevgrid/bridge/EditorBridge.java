package io.github.sqldevgrid.bridge;

import java.awt.*;
import java.awt.geom.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

/** Bootstrap-visible, Oracle-independent display adapter. Never edits the Document. */
public final class EditorBridge {
    public record Options(boolean wrap, boolean rules, float thickness, int rgb, String style) { }
    private static volatile Options global = new Options(false, false, 1f, 0x6F8296, "solid");
    private static final ThreadLocal<Boolean> ORIGINAL = ThreadLocal.withInitial(() -> false);
    private static final Map<View, Layout> CACHE = new WeakHashMap<>();
    private static final Map<Document, Revision> REVISIONS = new WeakHashMap<>();
    private static final Map<Document, RuleCache> RULES = new WeakHashMap<>();
    private record RuleCache(long stamp, int[] offsets) { }
    private static final String OVERRIDE = "sqldev.font50.editorFeatures";
    private record Row(int start, int end, int line, int sourceX, int sourceY, int pixels) { }
    private record Layout(Revision revision, long stamp, int width, Font font, float nativeHeight,
                          int height, List<Row> rows, int[] first, int[] rules) { }
    private static final class Revision implements DocumentListener {
        long stamp;
        public void insertUpdate(DocumentEvent event) { stamp++; }
        public void removeUpdate(DocumentEvent event) { stamp++; }
        public void changedUpdate(DocumentEvent event) { stamp++; }
    }
    private static Revision revision(Document document) {
        synchronized (REVISIONS) {
            Revision revision = REVISIONS.get(document);
            if (revision == null) { revision = new Revision(); REVISIONS.put(document, revision); document.addDocumentListener(revision); }
            return revision;
        }
    }
    private static int[] markers(Document document) throws BadLocationException {
        Revision revision = revision(document);
        synchronized (RULES) {
            RuleCache cached = RULES.get(document);
            if (cached != null && cached.stamp == revision.stamp) return cached.offsets;
            int[] offsets = ruleLines(document.getText(0, document.getLength()));
            RULES.put(document, new RuleCache(revision.stamp, offsets)); return offsets;
        }
    }
    /** Suppress marker glyphs only; native layout and selection backgrounds still run. */
    public static void drawChars(Graphics graphics, char[] text, int offset, int length, int x, int y, View view, int sourceOffset) {
        if (options(view).rules) {
            try {
                Document document = view.getDocument();
                int line = callInt(document, "getLineFromOffset", sourceOffset);
                int start = callInt(document, "getLineStartOffset", line);
                if (Arrays.binarySearch(markers(document), start) >= 0) return;
            } catch (BadLocationException ignored) { }
        }
        graphics.drawChars(text, offset, length, x, y);
    }
    public static void configure(boolean wrap, boolean rules, float thickness, int rgb, String style) {
        global = new Options(wrap, rules, Math.max(.5f, Math.min(8f, thickness)), rgb & 0xFFFFFF, style);
        synchronized (CACHE) { CACHE.clear(); }
    }
    public static void configureEditor(JTextComponent editor, boolean wrap, boolean rules, float thickness, int rgb, String style) {
        editor.putClientProperty(OVERRIDE, new Options(wrap, rules, thickness, rgb, style));
        invalidate(editor);
    }
    public static void invalidate(JTextComponent editor) {
        synchronized (CACHE) { CACHE.entrySet().removeIf(entry -> entry.getKey().getContainer() == editor); }
        if (editor.getUI() != null) editor.getUI().getRootView(editor).preferenceChanged(null, true, true);
        editor.revalidate(); editor.repaint();
    }
    public static void drop(View view) { synchronized (CACHE) { CACHE.remove(view); } }
    private static Options options(View view) {
        if (view.getContainer() instanceof JTextComponent editor && editor.getClientProperty(OVERRIDE) instanceof Options options) return options;
        return global;
    }
    public static boolean active(View view, boolean paint) {
        if (ORIGINAL.get() || !(view.getContainer() instanceof JTextComponent editor) || width(editor) < 20) return false;
        Options options = options(view);
        return options.wrap || paint && options.rules;
    }
    private static int width(JTextComponent editor) {
        int width = editor.getParent() instanceof JViewport viewport ? viewport.getExtentSize().width : editor.getWidth();
        Insets insets = editor.getInsets();
        return Math.max(1, width - insets.left - insets.right - 2);
    }
    private interface Original<T> { T call() throws BadLocationException; }
    private static <T> T original(Original<T> call) throws BadLocationException {
        boolean previous = ORIGINAL.get(); ORIGINAL.set(true);
        try { return call.call(); } finally { ORIGINAL.set(previous); }
    }
    private static Rectangle nativeRect(View view, int offset, int width) throws BadLocationException {
        return original(() -> view.modelToView(offset, new Rectangle(0, 0, width, Integer.MAX_VALUE / 16), Position.Bias.Forward).getBounds());
    }
    private static int callInt(Object target, String name, int argument) {
        try { return ((Number)target.getClass().getMethod(name, int.class).invoke(target, argument)).intValue(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
    }
    private static boolean collapsed(JTextComponent editor, int line) {
        try { return (Boolean)editor.getClass().getMethod("isLineCollapsed", int.class).invoke(editor, line); }
        catch (ReflectiveOperationException error) { return false; }
    }
    private static Layout layout(View view) throws BadLocationException {
        JTextComponent editor = (JTextComponent)view.getContainer(); Document document = view.getDocument();
        Revision revision = revision(document);
        int width = width(editor);
        float nativeHeight = original(() -> view.getPreferredSpan(View.Y_AXIS));
        Layout cached; synchronized (CACHE) { cached = CACHE.get(view); }
        if (cached != null && cached.revision == revision && cached.stamp == revision.stamp && cached.width == width
                && cached.font.equals(editor.getFont()) && cached.nativeHeight == nativeHeight) return cached;
        String text = document.getText(0, document.getLength());
        int count;
        try { count = ((Number)document.getClass().getMethod("getLineCount").invoke(document)).intValue(); }
        catch (ReflectiveOperationException error) { count = document.getDefaultRootElement().getElementCount(); }
        Rectangle firstRect = nativeRect(view, 0, width);
        int height = Math.max(1, firstRect.height);
        List<Row> rows = new ArrayList<>(); int[] first = new int[count];
        int[] markers = markers(document);
        for (int line = 0; line < count; line++) {
            first[line] = Math.max(0, rows.size() - 1);
            if (collapsed(editor, line)) continue;
            int start = callInt(document, "getLineStartOffset", line);
            int end = Math.min(text.length(), callInt(document, "getLineEndOffset", line));
            while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) end--;
            first[line] = rows.size();
            Rectangle base = nativeRect(view, start, width);
            if (start == end) { rows.add(new Row(start, end, line, base.x, base.y, 0)); continue; }
            int cursor = start;
            while (cursor < end) {
                Rectangle begin = nativeRect(view, cursor, width);
                int low = cursor + 1, high = end, fitted = cursor;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    if (nativeRect(view, mid, width).x - begin.x <= width) { fitted = mid; low = mid + 1; }
                    else high = mid - 1;
                }
                fitted = Math.max(cursor + 1, fitted);
                if (fitted < end && Character.isLowSurrogate(text.charAt(fitted)) && fitted > cursor + 1) fitted--;
                int cut = fitted;
                if (cut < end) {
                    for (int p = cut; p > cursor + 1; p--) {
                        char previous = text.charAt(p - 1);
                        if (Character.isWhitespace(previous) || previous == ',' || previous == ';') { cut = p; break; }
                    }
                }
                Rectangle finish = nativeRect(view, cut, width);
                rows.add(new Row(cursor, cut, line, begin.x, base.y, Math.max(1, finish.x - begin.x)));
                cursor = cut;
            }
        }
        if (rows.isEmpty()) rows.add(new Row(0, 0, 0, 0, 0, 0));
        Layout result = new Layout(revision, revision.stamp, width, editor.getFont(), nativeHeight, height, rows, first, markers);
        synchronized (CACHE) { CACHE.put(view, result); }
        return result;
    }
    private static int index(Layout layout, int offset, Position.Bias bias) {
        List<Row> rows = layout.rows; int low = 0, high = rows.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (rows.get(mid).start <= offset) low = mid + 1; else high = mid - 1;
        }
        int row = Math.max(0, high);
        if (bias == Position.Bias.Backward && row > 0 && rows.get(row).start == offset && rows.get(row - 1).end == offset) row--;
        return row;
    }
    public static Shape model(View view, int offset, Shape allocation, Position.Bias bias) throws BadLocationException {
        Layout layout = layout(view); int index = index(layout, offset, bias); Row row = layout.rows.get(index);
        Rectangle original = nativeRect(view, offset, layout.width), bounds = allocation.getBounds();
        return new Rectangle(bounds.x + Math.max(0, original.x - row.sourceX), bounds.y + index * layout.height, 1, layout.height);
    }
    public static Shape range(View view, int p0, Position.Bias b0, int p1, Position.Bias b1, Shape allocation) throws BadLocationException {
        Layout layout = layout(view); Rectangle bounds = allocation.getBounds();
        int start = index(layout, Math.min(p0, p1), Position.Bias.Forward), end = index(layout, Math.max(p0, p1), Position.Bias.Backward);
        GeneralPath path = new GeneralPath();
        for (int row = start; row <= end; row++) {
            float left = row == start ? model(view, Math.min(p0, p1), allocation, b0).getBounds().x : bounds.x;
            float right = row == end ? model(view, Math.max(p0, p1), allocation, b1).getBounds().x : bounds.x + layout.width;
            path.append(new Rectangle2D.Float(left, bounds.y + row * layout.height, Math.max(1, right - left), layout.height), false);
        }
        return path;
    }
    public static int position(View view, float x, float y, Shape allocation, Position.Bias[] bias) {
        try {
            Layout layout = layout(view); Rectangle bounds = allocation.getBounds();
            int index = Math.max(0, Math.min(layout.rows.size() - 1, (int)((y - bounds.y) / layout.height)));
            Row row = layout.rows.get(index);
            float sourceX = row.sourceX + Math.max(0, x - bounds.x);
            int result = original(() -> view.viewToModel(sourceX, row.sourceY + layout.height / 2f,
                new Rectangle(0, 0, layout.width, Integer.MAX_VALUE / 16), bias));
            bias[0] = Position.Bias.Forward;
            return Math.max(row.start, Math.min(row.end, result));
        } catch (BadLocationException error) { bias[0] = Position.Bias.Forward; return 0; }
    }
    public static float span(View view, int axis, int kind) {
        try {
            if (axis == View.X_AXIS) return kind == 1 ? 0 : kind == 2 ? Integer.MAX_VALUE : width((JTextComponent)view.getContainer());
            Layout layout = layout(view);
            int nativeRows = layout.rows.stream().mapToInt(row -> row.sourceY / layout.height).max().orElse(0) + 1;
            return Math.max(layout.rows.size() * layout.height, layout.nativeHeight + (layout.rows.size() - nativeRows) * layout.height);
        } catch (BadLocationException error) { return 1; }
    }
    public static int rowForLine(View view, int line) {
        try { Layout layout = layout(view); return line < 0 ? line : line >= layout.first.length
            ? layout.rows.size() + line - layout.first.length : layout.first[line]; }
        catch (BadLocationException error) { return line; }
    }
    public static int lineForRow(View view, int row) {
        try { Layout layout = layout(view); return row < 0 ? row : row >= layout.rows.size()
            ? layout.first.length + row - layout.rows.size() : layout.rows.get(row).line; }
        catch (BadLocationException error) { return row; }
    }
    public static void paint(View view, Graphics graphics, Shape allocation) {
        Options options = options(view);
        try {
            if (!options.wrap) original(() -> { view.paint(graphics, allocation); return null; });
            else {
                Layout layout = layout(view); Rectangle bounds = allocation.getBounds(), clip = graphics.getClipBounds();
                int sourceWidth = Math.max(layout.width, (int)Math.ceil(original(() -> view.getPreferredSpan(View.X_AXIS))));
                int first = clip == null ? 0 : Math.max(0, (clip.y - bounds.y) / layout.height);
                int last = clip == null ? layout.rows.size() - 1 : Math.min(layout.rows.size() - 1, (clip.y + clip.height - bounds.y) / layout.height);
                for (int index = first; index <= last; index++) {
                    Row row = layout.rows.get(index); int y = bounds.y + index * layout.height;
                    Graphics copy = graphics.create();
                    try {
                        int extent = row.start == row.end ? layout.width : Math.min(layout.width, row.pixels + 1);
                        copy.clipRect(bounds.x, y, extent, layout.height);
                        copy.translate(bounds.x - row.sourceX, y - row.sourceY);
                        original(() -> { view.paint(copy, new Rectangle(0, 0, sourceWidth, Math.max(1, (int)layout.nativeHeight))); return null; });
                    } finally { copy.dispose(); }
                }
            }
            if (options.rules) paintRules(view, graphics, allocation, options);
        } catch (BadLocationException ignored) { }
    }
    private static void paintRules(View view, Graphics graphics, Shape allocation, Options options) throws BadLocationException {
        if (!(graphics instanceof Graphics2D)) return;
        JTextComponent editor = (JTextComponent)view.getContainer();
        String text = view.getDocument().getText(0, view.getDocument().getLength());
        int[] offsets = options.wrap ? layout(view).rules : markers(view.getDocument());
        Rectangle visible = editor.getVisibleRect(); Insets insets = editor.getInsets();
        int left = visible.x + insets.left, right = visible.x + visible.width - insets.right - 1;
        if (visible.width <= 1) { left = allocation.getBounds().x; right = left + width(editor); }
        Graphics2D copy = (Graphics2D)graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float scale = Math.max(1, options.thickness);
            float[] dash = switch (options.style) {
                case "dashed" -> new float[] {8 * scale, 5 * scale};
                case "dotted" -> new float[] {scale, 4 * scale};
                case "dashdot" -> new float[] {8 * scale, 4 * scale, scale, 4 * scale};
                default -> null;
            };
            copy.setStroke(new BasicStroke(options.thickness, "dotted".equals(options.style) ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, dash, 0));
            copy.setColor(new Color(options.rgb));
            for (int offset : offsets) {
                if (collapsed(editor, callInt(view.getDocument(), "getLineFromOffset", offset))) continue;
                Rectangle row = options.wrap ? model(view, offset, allocation, Position.Bias.Forward).getBounds()
                    : original(() -> view.modelToView(offset, allocation, Position.Bias.Forward).getBounds());
                int y = row.y + row.height / 2;
                if (copy.getClipBounds() == null || copy.getClipBounds().intersects(left, y - 8, Math.max(1, right - left), 16)) copy.draw(new Line2D.Float(left, y, right, y));
            }
        } finally { copy.dispose(); }
    }
    /** Recognizes standalone SQL comment markers, excluding quoted strings/identifiers. */
    public static int[] ruleLines(String text) {
        ArrayList<Integer> offsets = new ArrayList<>();
        int state = 0; char qEnd = 0; int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == lineStart && state == 0) {
                int end = text.indexOf('\n', i); if (end < 0) end = text.length();
                if (text.substring(i, end).trim().equals("---")) offsets.add(i);
            }
            if (i == text.length()) break;
            char c = text.charAt(i), next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (c == '\n') { lineStart = i + 1; if (state == 4) state = 0; continue; }
            if (state == 4) continue;
            if (state == 3) { if (c == '*' && next == '/') { state = 0; i++; } continue; }
            if (state == 5) { if (c == qEnd && next == '\'') { state = 0; i++; } continue; }
            if (state == 1 || state == 2) {
                char quote = state == 1 ? '\'' : '"';
                if (c == quote) { if (next == quote) i++; else state = 0; } continue;
            }
            if ((c == 'q' || c == 'Q') && next == '\'' && i + 2 < text.length()) {
                char open = text.charAt(i + 2); qEnd = switch (open) { case '[' -> ']'; case '{' -> '}'; case '(' -> ')'; case '<' -> '>'; default -> open; }; state = 5; i += 2;
            } else if (c == '\'') state = 1;
            else if (c == '"') state = 2;
            else if (c == '/' && next == '*') { state = 3; i++; }
            else if (c == '-' && next == '-') { state = 4; i++; }
        }
        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }
}
