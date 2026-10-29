package io.github.sqldevgrid;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

/** Locates native editor instances and reloads display-only options. */
public final class EditorFeatureService {
    private static final String KEY = "sqldev.font50.editorFeatureBinding";
    private static volatile String previous = "";
    private static Class<?> bridge;
    public static void start() {
        try { bridge = Class.forName("io.github.sqldevgrid.bridge.EditorBridge", true, null); }
        catch (ClassNotFoundException error) { return; }
        reload();
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof ContainerEvent container && event.getID() == ContainerEvent.COMPONENT_ADDED)
                visit(container.getChild());
        }, AWTEvent.CONTAINER_EVENT_MASK);
        for (Window window : Window.getWindows()) visit(window);
        // Poll files away from the shared Swing event thread (also used by result grids).
        var poller=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task->{Thread t=new Thread(task,"sqldev-editor-settings");t.setDaemon(true);return t;});
        poller.scheduleWithFixedDelay(EditorFeatureService::reload,1,1,java.util.concurrent.TimeUnit.SECONDS);
    }
    private static Path path() { return GridSettings.path().getParent().resolve("editor-settings.json"); }
    public static void reloadNow() { if (bridge != null) reload(); }
    private static void reload() {
        try {
            String text = Files.isRegularFile(path()) ? Files.readString(path(), StandardCharsets.UTF_8) : "{}";
            Path notebook=path().resolveSibling("notebook-settings.json");
            String paper=Files.isRegularFile(notebook)?Files.readString(notebook,StandardCharsets.UTF_8):"{}";
            String combined=text+"\n"+paper;
            if (combined.equals(previous)) return;
            if(!SwingUtilities.isEventDispatchThread()){
                SwingUtilities.invokeLater(()->apply(text,paper,combined));return;
            }
            apply(text,paper,combined);
        } catch (Exception error) { report(error); }
    }
    private static void apply(String text,String paper,String combined) {
        if(combined.equals(previous))return;
        try {
            boolean wrap = Boolean.parseBoolean(GridSettings.value(text, "softWrap", "false"));
            boolean rules = Boolean.parseBoolean(GridSettings.value(text, "horizontalRules", "false"));
            float thickness = Float.parseFloat(GridSettings.value(text, "ruleThickness", "1"));
            String color = GridSettings.value(text, "ruleColor", "\"#6F8296\"").replace("\"", "");
            String style = GridSettings.value(text, "ruleStyle", "\"solid\"").replace("\"", "");
            int rgb = Integer.parseInt(color.substring(1), 16);
            bridge.getMethod("configure", boolean.class, boolean.class, float.class, int.class, String.class).invoke(null, wrap, rules, thickness, rgb, style);
            bridge.getMethod("configureNotebook",String.class).invoke(null,paper);
            previous = combined;
            for (Window window : Window.getWindows()) refresh(window);
        } catch (Exception error) { report(error); }
    }
    private static void report(Exception error){try { Files.writeString(path().resolveSibling("editor-feature-error.txt"), error.toString(), StandardCharsets.UTF_8); } catch (Exception ignored) { }}
    private static void refresh(Component component) {
        if (component instanceof JTextComponent editor && nativeEditor(editor)) invalidate(editor);
        if (component instanceof Container container) for (Component child : container.getComponents()) refresh(child);
    }
    private static boolean nativeEditor(Component component) {
        for (Class<?> type = component.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("oracle.javatools.editor.BasicEditorPane")) return true;
        return false;
    }
    private static void visit(Component component) {
        if (component instanceof JTextComponent editor && nativeEditor(editor) && editor.getClientProperty(KEY) == null) {
            editor.putClientProperty(KEY, Boolean.TRUE);
            DocumentListener changes = new DocumentListener() {
                public void insertUpdate(DocumentEvent event) { changed(editor); }
                public void removeUpdate(DocumentEvent event) { changed(editor); }
                public void changedUpdate(DocumentEvent event) { invalidate(editor); }
            };
            editor.getDocument().addDocumentListener(changes);
            editor.addPropertyChangeListener(event -> {
                String name=event.getPropertyName();
                if ("document".equals(name)) {
                    if (event.getOldValue() instanceof Document old) old.removeDocumentListener(changes);
                    if (event.getNewValue() instanceof Document next) next.addDocumentListener(changes);
                }
                // Selection/highlight/caret and our raster client properties do not change layout.
                if(name!=null&&Set.of("document","font","UI","tab-size","line-height","sqldev.font50.editorFeatures").contains(name))invalidate(editor);
            });
            editor.addComponentListener(new ComponentAdapter() { public void componentResized(ComponentEvent event) { invalidate(editor); } });
            invalidate(editor);
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) visit(child);
    }
    private static void invalidate(JTextComponent editor) {
        try { bridge.getMethod("invalidate", JTextComponent.class).invoke(null, editor); }
        catch (ReflectiveOperationException ignored) { }
    }
    private static void changed(JTextComponent editor) {
        try { bridge.getMethod("documentChanged", JTextComponent.class).invoke(null, editor); }
        catch (ReflectiveOperationException ignored) { }
    }
}
