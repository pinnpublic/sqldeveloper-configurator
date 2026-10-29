package io.github.sqldevgrid;

import java.awt.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;
import javax.swing.table.*;
import oracle.ide.ceditor.options.*;
import oracle.ide.panels.*;
import oracle.ide.util.Namespace;
import oracle.javatools.editor.EditorProperties;

/** Runs Oracle's instrumented font page against isolated preferences and memory rows. */
public final class NativeFontPreferencesTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void choose(DefaultTraversablePanel panel, int size) throws Exception {
        Field field = panel.getClass().getDeclaredField("comboBox_fontSize");
        field.setAccessible(true);
        ((JComboBox<?>) field.get(panel)).setSelectedItem(size);
    }
    static int savedSize() { return GridSettings.load().size; }
    static TraversableContext context(int size) {
        TraversableContext context = new TraversableContext(new Namespace(), 0);
        oracle.javatools.data.HashStructure values = oracle.javatools.data.HashStructure.newInstance();
        context.put("generic-PropertyStorage", (oracle.javatools.data.PropertyStorage) () -> values);
        FontSizeOptions options = FontSizeOptions.getInstance(context.getPropertyStorage());
        options.setFontFamily("Monospaced"); options.setFontSize(size);
        return context;
    }
    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Path file = GridSettings.path(); Files.createDirectories(file.getParent());
                    String original = "{\"enabled\":true,\"fontFamily\":\"Monospaced\",\"fontSize\":36,\"paddingPerSide\":8,\"applyToHeader\":true,\"extra\":\"한글\"}";
                    Files.writeString(file, original, StandardCharsets.UTF_8);
                    ImmediateGridStyler.start(); ImmediateGridStyler.reloadNow();
                    // Bind a plain JTable to exercise production refresh without initializing a database module.
                    JTable table = new JTable(new Object[][] {{"dog", "강아지"}, {"cat", "고양이"}}, new Object[] {"Name", "Text"});
                    JTable numbers = new JTable(2, 1); JScrollPane scroll = new JScrollPane(table); scroll.setRowHeaderView(numbers);
                    Method bind = ImmediateGridStyler.class.getDeclaredMethod("bindAndApply", JTable.class); bind.setAccessible(true);
                    Field instance = ImmediateGridStyler.class.getDeclaredField("instance"); instance.setAccessible(true);
                    bind.invoke(instance.get(null), table);
                    TableCellRenderer renderer = table.getCellRenderer(0, 0);
                    table.changeSelection(1, 1, false, false);
                    check(table.getFont().getSize() == 36, "Initial separate result size lost");

                    FontSizeOptionsPanel panel = new FontSizeOptionsPanel();
                    TraversableContext context = context(40); panel.onEntry(context);
                    choose(panel, 24); panel.onExit(context); panel.onEntry(context);
                    check(Files.readString(file).equals(original) && table.getFont().getSize() == 36,
                        "Navigation committed pending font size");
                    panel.cancel(new ApplyEvent(context));
                    check(Files.readString(file).equals(original), "Cancel changed result settings");

                    context = context(40); panel.onEntry(context); panel.onExit(context); panel.apply(new ApplyEvent(context));
                    check(savedSize() == 36, "Unchanged editor size overwrote independent result size");
                    for (int size : new int[] {24, 50, 7, 34}) {
                        choose(panel, size); panel.onExit(context); panel.onEntry(context); panel.apply(new ApplyEvent(context));
                        check(savedSize() == size && table.getFont().getSize() == size,
                            "Native Apply failed to refresh saved/existing result font at " + size + ": saved=" + savedSize() + ", table=" + table.getFont().getSize() + ", error="
                            + System.getProperty("sqldev.font50.nativeFontPreferencesError"));
                        check(EditorProperties.getProperties().getBaseFont().getSize() == size, "Editor base font differs");
                        check(table.getRowHeight() == numbers.getRowHeight(), "Row number alignment lost");
                        Component cell = renderer.getTableCellRendererComponent(table, "dog", false, false, 0, 0);
                        check(table.getRowHeight() - table.getRowMargin() >= cell.getPreferredSize().height + 16,
                            "Vertical clipping/padding regression at " + size);
                        check(table.getCellRenderer(0, 0) == renderer && table.getSelectedRow() == 1 && table.getSelectedColumn() == 1,
                            "Native renderer or selection changed");
                        check("dog".equals(table.getValueAt(0, 0)) && "cat".equals(table.getValueAt(1, 0)), "Model values changed");
                        check(Files.readString(file).equals(original.replace("\"fontSize\":36", "\"fontSize\": " + size)),
                            "Non-size settings or UTF-8 content changed");
                        JTable fresh = new JTable(1, 1); bind.invoke(instance.get(null), fresh);
                        check(fresh.getFont().getSize() == size, "New result table did not inherit committed size");
                    }
                    // Static startup initialization must preserve a deliberately separate result size.
                    FontSizeOptions startup = new FontSizeOptions(); startup.setFontSize(40);
                    FontSizeOptionsPanel.applyOptions(startup);
                    check(savedSize() == 34, "Startup options reset separate result size");
                    // SQL Developer's real font page is loaded by an Oracle bundle,
                    // while the bridge and result styler are owned by the startup agent.
                    try (var bundle = new java.net.URLClassLoader(new java.net.URL[0], FontSizeOptionsPanel.class.getClassLoader()) {
                        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                            if (!name.startsWith("oracle.ide.ceditor.options.FontSizeOptionsPanel")) return super.loadClass(name, resolve);
                            Class<?> type = findLoadedClass(name);
                            if (type == null) {
                                try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                                    byte[] code = input.readAllBytes();
                                    type = defineClass(name, code, 0, code.length);
                                } catch (java.io.IOException error) { throw new ClassNotFoundException(name, error); }
                            }
                            if (resolve) resolveClass(type);
                            return type;
                        }
                    }) {
                        DefaultTraversablePanel isolated = (DefaultTraversablePanel) bundle.loadClass("oracle.ide.ceditor.options.FontSizeOptionsPanel").getConstructor().newInstance();
                        TraversableContext isolatedContext = context(40); isolated.onEntry(isolatedContext);
                        choose(isolated, 50); isolated.onExit(isolatedContext); ((ApplyListener) isolated).apply(new ApplyEvent(isolatedContext));
                        check(savedSize() == 50 && table.getFont().getSize() == 50, "Oracle bundle could not call agent bridge");
                    }
                    Files.writeString(file, "{\"paddingPerSide\":-20,\"enabled\":false}", StandardCharsets.UTF_8);
                    GridSettings.saveSize(50);
                    check(savedSize() == 50 && !GridSettings.load().enabled && GridSettings.load().padding == -20,
                        "Missing size insertion changed disabled/padding settings");
                    Files.delete(file); GridSettings.saveSize(24);
                    check(savedSize() == 24, "First native change failed without existing settings");
                    System.out.println("PASS native font page: navigation, Cancel, unchanged Apply, 24/50/7/34pt committed Apply, immediate existing/new grids, height, selection, persistence, startup and UTF-8 preservation");
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
}
