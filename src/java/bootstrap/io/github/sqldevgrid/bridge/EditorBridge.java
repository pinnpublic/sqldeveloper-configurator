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
    private record Piece(int start,int end,int sourceX,int pixels) { }
    private record Layout(Revision revision, long stamp, int width, Font font, float nativeHeight,
                          int nativeRowHeight, int height, float preferredHeight, List<Row> rows, int[] first, int[] rules,
                          Map<String,List<Piece>> geometry) { }
    private static final ClassValue<Map<String,Method>> METHODS=new ClassValue<>() {
        protected Map<String,Method> computeValue(Class<?> type){return new java.util.concurrent.ConcurrentHashMap<>();}
    };
    private static Method intMethod(Class<?> type,String name){return METHODS.get(type).computeIfAbsent(name,key->{try{return type.getMethod(key,int.class);}catch(NoSuchMethodException error){throw new IllegalStateException(error);}});}
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
        global = new Options(wrap, rules, ruleThickness(thickness), rgb & 0xFFFFFF, style);
        synchronized (CACHE) { CACHE.clear(); }
    }
    public static void configureNotebook(String json) {
        NotebookBridge.configure(json);
        synchronized (CACHE) { CACHE.clear(); }
    }
    private static boolean arranged(Options options) { return options.wrap || NotebookBridge.spaced(); }
    public static void configureEditor(JTextComponent editor, boolean wrap, boolean rules, float thickness, int rgb, String style) {
        editor.putClientProperty(OVERRIDE, new Options(wrap, rules, ruleThickness(thickness), rgb, style));
        invalidate(editor);
    }
    private static float ruleThickness(float thickness) {
        return Float.isFinite(thickness) ? Math.max(1f, Math.min(50f, thickness)) : 1f;
    }
    public static void invalidate(JTextComponent editor) {
        synchronized (CACHE) { CACHE.entrySet().removeIf(entry -> entry.getKey().getContainer() == editor); }
        documentChanged(editor);
    }
    /** Keep unchanged line geometry across edits; the revision stamp invalidates row offsets. */
    public static void documentChanged(JTextComponent editor) {
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
        return arranged(options) || paint && (options.rules || NotebookBridge.enabled());
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
        try { return ((Number)intMethod(target.getClass(), name).invoke(target, argument)).intValue(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
    }
    private static boolean collapsed(JTextComponent editor, int line) {
        try { return (Boolean)intMethod(editor.getClass(), "isLineCollapsed").invoke(editor, line); }
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
        int height = Math.max(1, firstRect.height + NotebookBridge.extraSpacing(firstRect.height));
        List<Row> rows = new ArrayList<>(); int[] first = new int[count];
        Map<String,List<Piece>> geometry=new HashMap<>();
        Map<String,List<Piece>> previousGeometry=cached!=null&&cached.width==width&&cached.font.equals(editor.getFont())&&cached.nativeRowHeight==firstRect.height?cached.geometry:Map.of();
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
            String lineText=text.substring(start,end);
            List<Piece> pieces=geometry.get(lineText);
            if(pieces==null)pieces=previousGeometry.get(lineText);
            if(pieces!=null){
                geometry.put(lineText,pieces);
                for(Piece piece:pieces)rows.add(new Row(start+piece.start,start+piece.end,line,piece.sourceX,base.y,piece.pixels));
                continue;
            }
            pieces=new ArrayList<>();
            int cursor = start;
            while (cursor < end) {
                Rectangle begin = nativeRect(view, cursor, width);
                if (!options(view).wrap) {
                    Rectangle finish = nativeRect(view, end, width);
                    rows.add(new Row(cursor, end, line, begin.x, base.y, Math.max(1, finish.x - begin.x)));
                    break;
                }
                int low = cursor + 1, high = end, fitted = cursor;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    if (nativeRect(view, mid, width).x - begin.x <= width) { fitted = mid; low = mid + 1; }
                    else high = mid - 1;
                }
                fitted = Math.max(cursor + Character.charCount(Character.codePointAt(text,cursor)), fitted);
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
                pieces.add(new Piece(cursor-start,cut-start,begin.x,Math.max(1,finish.x-begin.x)));
                cursor = cut;
            }
            geometry.put(lineText,List.copyOf(pieces));
        }
        if (rows.isEmpty()) rows.add(new Row(0, 0, 0, 0, 0, 0));
        int nativeRows=rows.stream().mapToInt(row->row.sourceY / firstRect.height).max().orElse(0)+1;
        float preferredHeight=Math.max(rows.size()*height,nativeHeight/firstRect.height*height+(rows.size()-nativeRows)*height);
        Layout result = new Layout(revision, revision.stamp, width, editor.getFont(), nativeHeight, firstRect.height, height, preferredHeight, rows, first, markers,geometry);
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
        if(!options(view).wrap){
            Rectangle nativeRow=original(()->view.modelToView(offset,allocation,bias).getBounds()),bounds=allocation.getBounds();
            int nativeHeight=Math.max(1,nativeRow.height),height=NotebookBridge.rowHeight(nativeHeight);
            return new Rectangle(nativeRow.x,bounds.y+(nativeRow.y-bounds.y)/nativeHeight*height,nativeRow.width,height);
        }
        Layout layout = layout(view); int index = index(layout, offset, bias); Row row = layout.rows.get(index);
        Rectangle original = nativeRect(view, offset, layout.width), bounds = allocation.getBounds();
        return new Rectangle(bounds.x + Math.max(0, original.x - row.sourceX), bounds.y + index * layout.height, 1, layout.height);
    }
    public static Shape range(View view, int p0, Position.Bias b0, int p1, Position.Bias b1, Shape allocation) throws BadLocationException {
        if(!options(view).wrap){
            Rectangle start=model(view,Math.min(p0,p1),allocation,b0).getBounds(),end=model(view,Math.max(p0,p1),allocation,b1).getBounds(),bounds=allocation.getBounds();
            GeneralPath path=new GeneralPath();
            for(int y=start.y;y<=end.y;y+=start.height){int left=y==start.y?start.x:bounds.x,right=y==end.y?end.x:bounds.x+bounds.width;path.append(new Rectangle(left,y,Math.max(1,right-left),start.height),false);}
            return path;
        }
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
            if(!options(view).wrap){
                Rectangle first=original(()->view.modelToView(0,allocation,Position.Bias.Forward).getBounds()),bounds=allocation.getBounds();
                int height=NotebookBridge.rowHeight(first.height),row=Math.max(0,(int)((y-bounds.y)/height));
                return original(()->view.viewToModel(x,bounds.y+row*first.height+first.height/2f,allocation,bias));
            }
            Layout layout = layout(view); Rectangle bounds = allocation.getBounds();
            int index = Math.max(0, Math.min(layout.rows.size() - 1, (int)((y - bounds.y) / layout.height)));
            Row row = layout.rows.get(index);
            float sourceX = row.sourceX + Math.max(0, x - bounds.x);
            int result = original(() -> view.viewToModel(sourceX, row.sourceY + layout.nativeRowHeight / 2f,
                new Rectangle(0, 0, layout.width, Integer.MAX_VALUE / 16), bias));
            bias[0] = Position.Bias.Forward;
            int end=row.end;
            // Native setCaretPosition uses Forward bias. A wrapped row's end is also
            // the next row's start; returning it would skip a row or trap Up there.
            if(result>=end&&index+1<layout.rows.size()&&layout.rows.get(index+1).start==end&&end>row.start){
                String tail=view.getDocument().getText(Math.max(row.start,end-2),Math.min(2,end-row.start));
                end-=Character.charCount(Character.codePointBefore(tail,tail.length()));
            }
            return Math.max(row.start, Math.min(end, result));
        } catch (BadLocationException error) { bias[0] = Position.Bias.Forward; return 0; }
    }
    public static float span(View view, int axis, int kind) {
        try {
            if (axis == View.X_AXIS) {
                if (!options(view).wrap) return original(() -> kind == 1 ? view.getMinimumSpan(axis) : kind == 2 ? view.getMaximumSpan(axis) : view.getPreferredSpan(axis));
                return kind == 1 ? 0 : kind == 2 ? Integer.MAX_VALUE : width((JTextComponent)view.getContainer());
            }
            if(!options(view).wrap){
                int nativeHeight=Math.max(1,nativeRect(view,0,width((JTextComponent)view.getContainer())).height);
                return original(()->view.getPreferredSpan(axis))/nativeHeight*NotebookBridge.rowHeight(nativeHeight);
            }
            return layout(view).preferredHeight;
        } catch (BadLocationException error) { return 1; }
    }
    public static int rowForLine(View view, int line) {
        if(!options(view).wrap)try{return original(()->callInt(view,"getRowForLine",line));}catch(BadLocationException ignored){return line;}
        try { Layout layout = layout(view); return line < 0 ? line : line >= layout.first.length
            ? layout.rows.size() + line - layout.first.length : layout.first[line]; }
        catch (BadLocationException error) { return line; }
    }
    public static int lineForRow(View view, int row) {
        if(!options(view).wrap)try{return original(()->callInt(view,"getLineForRow",row));}catch(BadLocationException ignored){return row;}
        try { Layout layout = layout(view); return row < 0 ? row : row >= layout.rows.size()
            ? layout.first.length + row - layout.rows.size() : layout.rows.get(row).line; }
        catch (BadLocationException error) { return row; }
    }
    public static void paint(View view, Graphics graphics, Shape allocation) {
        Options options = options(view);
        try {
            if (!arranged(options)) original(() -> { view.paint(graphics, allocation); return null; });
            else if(!options.wrap){
                Rectangle bounds=allocation.getBounds(),clip=graphics.getClipBounds();
                int nativeHeight=Math.max(1,nativeRect(view,0,width((JTextComponent)view.getContainer())).height),height=NotebookBridge.rowHeight(nativeHeight);
                int first=clip==null?0:Math.max(0,(clip.y-bounds.y)/height),last=clip==null?(bounds.height-1)/height:(clip.y+clip.height-bounds.y)/height;
                for(int row=first;row<=last;row++){
                    int y=bounds.y+row*height;Graphics copy=graphics.create();
                    try{copy.clipRect(bounds.x,y,bounds.width,nativeHeight);copy.translate(0,row*(height-nativeHeight));original(()->{view.paint(copy,allocation);return null;});}finally{copy.dispose();}
                }
            } else {
                Layout layout = layout(view); Rectangle bounds = allocation.getBounds(), clip = graphics.getClipBounds();
                int sourceWidth = Math.max(layout.width, (int)Math.ceil(original(() -> view.getPreferredSpan(View.X_AXIS))));
                int first = clip == null ? 0 : Math.max(0, (clip.y - bounds.y) / layout.height);
                int last = clip == null ? layout.rows.size() - 1 : Math.min(layout.rows.size() - 1, (clip.y + clip.height - bounds.y) / layout.height);
                for (int index = first; index <= last; index++) {
                    Row row = layout.rows.get(index); int y = bounds.y + index * layout.height;
                    Graphics copy = graphics.create();
                    try {
                        int extent = options.wrap ? (row.start == row.end ? layout.width : Math.min(layout.width, row.pixels + 1)) : sourceWidth;
                        copy.clipRect(bounds.x, y, extent, layout.nativeRowHeight);
                        copy.translate(bounds.x - row.sourceX, y - row.sourceY);
                        original(() -> { view.paint(copy, new Rectangle(0, 0, sourceWidth, Math.max(1, (int)layout.nativeHeight))); return null; });
                    } finally { copy.dispose(); }
                }
            }
            if (options.rules) paintRules(view, graphics, allocation, options);
            if (view.getContainer() instanceof JTextComponent editor) NotebookBridge.paint(graphics, editor, true);
        } catch (BadLocationException ignored) { }
    }
    private static void paintRules(View view, Graphics graphics, Shape allocation, Options options) throws BadLocationException {
        if (!(graphics instanceof Graphics2D)) return;
        JTextComponent editor = (JTextComponent)view.getContainer();
        int[] offsets = options.wrap ? layout(view).rules : markers(view.getDocument());
        if(offsets.length==0)return;
        Rectangle visible = editor.getVisibleRect(); Insets insets = editor.getInsets();
        int left = visible.x + insets.left, right = visible.x + visible.width - insets.right - 1;
        if (visible.width <= 1) { left = allocation.getBounds().x; right = left + width(editor); }
        Rectangle clip=graphics.getClipBounds();
        int top=clip==null?visible.y:clip.y,bottom=clip==null?visible.y+visible.height:clip.y+clip.height;
        Position.Bias[] bias={Position.Bias.Forward};
        int from=position(view,allocation.getBounds().x,Math.max(0,top-51),allocation,bias);
        int to=position(view,allocation.getBounds().x+allocation.getBounds().width,bottom+51,allocation,bias);
        int begin=Arrays.binarySearch(offsets,from);if(begin<0)begin=-begin-1;
        Graphics2D copy = (Graphics2D)graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float scale = Math.max(1, options.thickness);
            float[] dash = switch (options.style) {
                case "dashed" -> new float[] {8 * scale, 5 * scale};
                case "dotted" -> new float[] {scale, 4 * scale};
                case "dashdot" -> new float[] {8 * scale, 4 * scale, scale, 4 * scale};
                case "longdash" -> new float[] {14 * scale, 5 * scale};
                case "shortdash" -> new float[] {3 * scale, 3 * scale};
                case "dashdotdot" -> new float[] {8 * scale, 4 * scale, scale, 4 * scale, scale, 4 * scale};
                case "longdashdot" -> new float[] {14 * scale, 4 * scale, scale, 4 * scale};
                default -> null;
            };
            boolean doubled = "double".equals(options.style);
            copy.setStroke(new BasicStroke(doubled ? options.thickness / 3 : options.thickness, "dotted".equals(options.style) ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, dash, 0));
            copy.setColor(new Color(options.rgb));
            for (int marker=begin;marker<offsets.length&&offsets[marker]<=to;marker++) {
                int offset=offsets[marker];
                if (collapsed(editor, callInt(view.getDocument(), "getLineFromOffset", offset))) continue;
                Rectangle row = arranged(options) ? model(view, offset, allocation, Position.Bias.Forward).getBounds()
                    : original(() -> view.modelToView(offset, allocation, Position.Bias.Forward).getBounds());
                int y = row.y + row.height / 2;
                int radius = (int)Math.ceil(options.thickness / 2) + 1;
                if (copy.getClipBounds() == null || copy.getClipBounds().intersects(left, y - radius, Math.max(1, right - left), radius * 2)) {
                    if (doubled) {
                        copy.draw(new Line2D.Float(left, y - options.thickness / 3, right, y - options.thickness / 3));
                        copy.draw(new Line2D.Float(left, y + options.thickness / 3, right, y + options.thickness / 3));
                    } else copy.draw(new Line2D.Float(left, y, right, y));
                }
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
