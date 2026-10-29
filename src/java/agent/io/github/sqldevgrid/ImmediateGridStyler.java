package io.github.sqldevgrid;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.lang.ref.WeakReference;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

/** Applies on component attachment and model/font changes, before the next paint. */
public final class ImmediateGridStyler {
    private static final String BINDING = "sqldev.font50.immediateBinding";
    private static ImmediateGridStyler instance;
    private final Map<JTable, ResultGridWatcher.Style> styles = new WeakHashMap<>();
    private GridSettings settings = GridSettings.load();
    private boolean updating;
    private WeakReference<JTable> testScope;
    private final javax.swing.Timer timer = new javax.swing.Timer(1000, event -> settingsAndHealth());

    public static synchronized void start() {
        if (instance != null) return;
        instance = new ImmediateGridStyler();
        Toolkit.getDefaultToolkit().addAWTEventListener(instance::event,
            AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.HIERARCHY_EVENT_MASK);
        // Existing windows matter only when attaching to an already running JVM.
        for (Window window : Window.getWindows()) instance.visit(window);
        instance.timer.start();
    }
    public static void reloadNow() {
        if (instance == null) return;
        if (SwingUtilities.isEventDispatchThread()) instance.settingsAndHealth();
        else SwingUtilities.invokeLater(() -> instance.settingsAndHealth());
    }
    private static boolean target(JTable table) {
        if (table.getParent() instanceof JViewport viewport && viewport.getParent() instanceof JScrollPane scroll
                && scroll.getRowHeader() == viewport) return false;
        for (Class<?> type = table.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("oracle.dbtools.raptor.controls.grid.ResultSetTable")) return true;
        return false;
    }
    private void event(AWTEvent event) {
        Component component = null;
        if (event instanceof ContainerEvent container && event.getID() == ContainerEvent.COMPONENT_ADDED)
            component = container.getContainer();
        else if (event instanceof HierarchyEvent hierarchy &&
                (hierarchy.getChangeFlags() & (HierarchyEvent.PARENT_CHANGED | HierarchyEvent.SHOWING_CHANGED)) != 0)
            component = hierarchy.getComponent();
        if (component == null) return;
        // Renderer attachment is part of painting, not a structural grid change.
        // Restyling here would mutate a shared renderer before its paint completes.
        for (Component parent = component; parent != null; parent = parent.getParent())
            if (parent instanceof CellRendererPane) return;
        if (testScope != null) {
            JTable table = testScope.get();
            if (table != null && (component == table || component instanceof Container parent && SwingUtilities.isDescendingFrom(table, parent))) visit(table);
            return;
        }
        final Component changed = component;
        if (SwingUtilities.isEventDispatchThread()) visit(changed);
        else SwingUtilities.invokeLater(() -> visit(changed));
    }
    public static AutoCloseable watchForTest(JTable table, int size, int padding) {
        ImmediateGridStyler scoped = new ImmediateGridStyler();
        scoped.settings = new GridSettings(true, "JetBrainsMonoHangul NL", size, padding, false);
        scoped.testScope = new WeakReference<>(table);
        AWTEventListener listener = scoped::event;
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.HIERARCHY_EVENT_MASK);
        return () -> {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
            scoped.updating = true;
            if (table.getClientProperty(BINDING) instanceof ImmediateGridStyler.Binding binding) binding.detach();
            table.putClientProperty(BINDING, null);
            ResultGridWatcher.Style style = scoped.styles.remove(table); if (style != null) style.restore(table);
        };
    }
    private void visit(Component component) {
        if (updating) return;
        for (Component parent = component; parent != null; parent = parent.getParent())
            if (parent instanceof CellRendererPane) return;
        try {
            if (component instanceof JTable table && target(table)) bindAndApply(table);
            if (component instanceof Container container)
                for (Component child : container.getComponents()) visit(child);
            // A row header or another child can be attached after its result table.
            for (Component parent = component.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof JTable table && target(table)) { bindAndApply(table); break; }
            }
        } catch (RuntimeException ignored) {
            // Constructor-time hierarchy events can arrive before the model is ready.
        }
    }
    private void bindAndApply(JTable table) {
        if (table.getFont() == null || table.getUI() == null || table.getModel() == null || table.getColumnModel() == null) return;
        if (!(table.getClientProperty(BINDING) instanceof Binding)) {
            Binding binding = new Binding(table);
            table.putClientProperty(BINDING, binding);
            table.addPropertyChangeListener(binding);
            binding.models();
        }
        apply(table);
    }
    private void apply(JTable table) {
        if (updating) return;
        updating = true;
        try {
            if (settings.enabled) styles.computeIfAbsent(table, ResultGridWatcher.Style::new).apply(table, settings);
            else {
                ResultGridWatcher.Style style = styles.remove(table);
                if (style != null) style.restore(table);
            }
        } catch (RuntimeException ignored) {
            // A following model/column/hierarchy event handles partially built grids.
        } finally { updating = false; }
    }
    private void settingsAndHealth() {
        GridSettings next = GridSettings.load();
        if (!next.key().equals(settings.key())) {
            settings = next;
            updating = true;
            try { for (Map.Entry<JTable, ResultGridWatcher.Style> entry : styles.entrySet()) entry.getValue().restore(entry.getKey()); }
            finally { updating = false; }
            ArrayList<JTable> tables = new ArrayList<>(styles.keySet()); styles.clear();
            for (JTable table : tables) if (table != null) apply(table);
        }
        // This timer reports health and explicit setting changes; it does not resize new grids.
        int count = 0, font = 0, height = 0;
        for (JTable table : styles.keySet()) if (table != null && table.isShowing()) {
            count++; font = Math.max(font, table.getFont().getSize()); height = Math.max(height, table.getRowHeight());
        }
        RuntimeStatus.write(count, font, height, "");
    }
    private final class Binding implements PropertyChangeListener, TableModelListener, TableColumnModelListener {
        private final WeakReference<JTable> reference;
        private TableModel model;
        private TableColumnModel columns;
        Binding(JTable table) { reference = new WeakReference<>(table); }
        void detach() {
            JTable table = reference.get(); if (table != null) table.removePropertyChangeListener(this);
            if (model != null) model.removeTableModelListener(this);
            if (columns != null) columns.removeColumnModelListener(this);
        }
        void models() {
            JTable table = reference.get(); if (table == null) return;
            if (model != table.getModel()) {
                if (model != null) model.removeTableModelListener(this);
                model = table.getModel(); if (model != null) model.addTableModelListener(this);
            }
            if (columns != table.getColumnModel()) {
                if (columns != null) columns.removeColumnModelListener(this);
                columns = table.getColumnModel(); if (columns != null) columns.addColumnModelListener(this);
            }
        }
        void changed() {
            JTable table = reference.get(); if (table == null || updating) return;
            if (SwingUtilities.isEventDispatchThread()) apply(table);
            else SwingUtilities.invokeLater(() -> apply(table));
        }
        public void propertyChange(PropertyChangeEvent event) {
            String name = event.getPropertyName();
            if ("model".equals(name) || "columnModel".equals(name)) { models(); changed(); }
            else if ("font".equals(name) || "rowHeight".equals(name) || "rowMargin".equals(name)) changed();
        }
        public void tableChanged(TableModelEvent event) { changed(); }
        public void columnAdded(TableColumnModelEvent event) { changed(); }
        public void columnRemoved(TableColumnModelEvent event) { changed(); }
        public void columnMoved(TableColumnModelEvent event) { }
        public void columnMarginChanged(ChangeEvent event) { }
        public void columnSelectionChanged(ListSelectionEvent event) { }
    }
}
