package io.github.sqldevgrid;

import java.awt.*;
import java.util.*;
import java.lang.ref.WeakReference;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

final class ResultGridWatcher {
    // Values do not retain the JTable keys; closed result tabs can be collected.
    private final Map<JTable, Style> styles = new WeakHashMap<>();
    private String settingsKey = "";
    private final javax.swing.Timer timer = new javax.swing.Timer(500, e -> scan());
    private int grids, fontSize, rowHeight;
    void start() { scan(); timer.start(); }
    private void scan() {
        try {
            grids = 0; fontSize = 0; rowHeight = 0;
            GridSettings settings = GridSettings.load();
            if (!settings.key().equals(settingsKey)) {
                for (Map.Entry<JTable, Style> entry : styles.entrySet()) entry.getValue().restore(entry.getKey());
                styles.clear(); settingsKey = settings.key();
            }
            if (settings.enabled) for (Window window : Window.getWindows()) if (window.isShowing()) visit(window, settings);
            RuntimeStatus.write(grids, fontSize, rowHeight, "");
        } catch (RuntimeException ex) {
            RuntimeStatus.write(grids, fontSize, rowHeight, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
    private void visit(Component component, GridSettings settings) {
        if (component instanceof JTable table && isResult(table)) {
            Style style = styles.computeIfAbsent(table, Style::new);
            style.apply(table, settings);
            grids++; fontSize = Math.max(fontSize, table.getFont().getSize()); rowHeight = Math.max(rowHeight, table.getRowHeight());
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) visit(child, settings);
    }
    private boolean isResult(JTable table) {
        if (table.getParent() instanceof JViewport viewport && viewport.getParent() instanceof JScrollPane scroll
                && scroll.getRowHeader() == viewport) return false;
        for (Class<?> type = table.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("oracle.dbtools.raptor.controls.grid.ResultSetTable")) return true;
        }
        boolean worksheet = false, result = false;
        for (Component c = table; c != null; c = c.getParent()) {
            String name = c.getClass().getName().toLowerCase(Locale.ROOT);
            worksheet |= name.startsWith("oracle.dbtools.worksheet.");
            result |= name.contains("result");
        }
        return worksheet && result;
    }

    static final class PaddingBorder extends CompoundBorder {
        PaddingBorder(Border original, int padding) { super(original, BorderFactory.createEmptyBorder(padding, 6, padding, 6)); }
    }
    static final class PaddedRenderer implements TableCellRenderer {
        final TableCellRenderer delegate;
        final int padding;
        PaddedRenderer(TableCellRenderer delegate, int padding) { this.delegate = delegate; this.padding = padding; }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            Component component = delegate.getTableCellRendererComponent(table, value, selected, focused, row, column);
            component.setFont(table.getFont());
            if (component instanceof JComponent jc) {
                Border base = jc.getBorder();
                while (base instanceof PaddingBorder p) base = p.getOutsideBorder();
                jc.setBorder(new PaddingBorder(base, padding));
            }
            return component;
        }
    }
    static final class Style {
        final Font originalFont, headerFont;
        final int originalHeight;
        final Dimension headerSize;
        WeakReference<JTable> rowNumbers = new WeakReference<>(null);
        int numberHeight;
        Font lastAppliedFont, inheritedFont;
        final Map<TableColumn, TableCellRenderer> renderers = new WeakHashMap<>();
        final Map<TableColumn, PaddedRenderer> wrappers = new WeakHashMap<>();
        Style(JTable table) {
            originalFont = table.getFont(); originalHeight = table.getRowHeight();
            inheritedFont = originalFont;
            JTableHeader header = table.getTableHeader();
            headerFont = header == null ? null : header.getFont();
            headerSize = header == null || header.getPreferredSize() == null ? null : new Dimension(header.getPreferredSize());
        }
        void apply(JTable table, GridSettings settings) {
            if (lastAppliedFont != null && !lastAppliedFont.equals(table.getFont())) inheritedFont = table.getFont();
            Font font = settings.font(inheritedFont);
            if (!font.equals(table.getFont())) table.setFont(font);
            lastAppliedFont = font;
            int height = table.getFontMetrics(font).getHeight() + settings.padding * 2 + table.getRowMargin() + 2;
            // Renderers are shared mutable "rubber stamps". Never invoke a live
            // renderer to measure height: this method can run while another cell
            // is being painted and would replace that cell's text/selection state.
            JLabel measure = new JLabel("Agjpqy 한글");
            measure.setFont(font);
            height = Math.max(height, measure.getPreferredSize().height + settings.padding * 2 + table.getRowMargin() + 4);
            int row = 0;
            if (table.getRowCount() > 0) {
                int visible = table.rowAtPoint(table.getVisibleRect().getLocation());
                row = Math.max(0, visible);
            }
            for (int col = 0; col < table.getColumnCount(); col++) {
                TableColumn column = table.getColumnModel().getColumn(col);
                // Recover any wrappers left by earlier releases, then let Oracle
                // own renderer lookup, cell contents, selection and focus borders.
                if (column.getCellRenderer() instanceof PaddedRenderer) {
                    TableCellRenderer original = renderers.containsKey(column) ? renderers.get(column) : column.getCellRenderer();
                    while (original instanceof PaddedRenderer wrapped) original = wrapped.delegate;
                    if (original == table.getDefaultRenderer(table.getColumnClass(col))) original = null;
                    column.setCellRenderer(original);
                }
            }
            renderers.clear(); wrappers.clear();
            height = Math.max(1, height);
            boolean changed = table.getRowHeight() != height;
            // JTable's global setter also clears stale per-row overrides.
            if (!changed && table.getRowCount() > 0) changed = table.getRowHeight(row) < height;
            if (changed) table.setRowHeight(height);
            // Row numbers have their own table and must scroll at the same row pitch.
            for (Component c = table.getParent(); c != null; c = c.getParent()) {
                if (c instanceof JScrollPane scroll) {
                    if (scroll.getRowHeader() != null && scroll.getRowHeader().getView() instanceof JTable numbers) {
                        if (rowNumbers.get() != numbers) { rowNumbers = new WeakReference<>(numbers); numberHeight = numbers.getRowHeight(); }
                        if (numbers.getRowHeight() != height) numbers.setRowHeight(height);
                    }
                    break;
                }
            }
            JTableHeader header = table.getTableHeader();
            if (settings.header && header != null) {
                header.setFont(font);
                int headerHeight = Math.max(height, header.getFontMetrics(font).getHeight() + settings.padding * 2 + 4);
                Dimension preferred = header.getPreferredSize();
                if (preferred.height != headerHeight) header.setPreferredSize(new Dimension(preferred.width, headerHeight));
            }
            if (changed) { table.revalidate(); table.repaint(); }
        }
        void restore(JTable table) {
            for (Map.Entry<TableColumn, TableCellRenderer> entry : renderers.entrySet()) {
                TableColumn column = entry.getKey();
                if (column.getCellRenderer() == wrappers.get(column)) column.setCellRenderer(entry.getValue());
            }
            table.setFont(originalFont); table.setRowHeight(originalHeight);
            if (rowNumbers.get() != null) rowNumbers.get().setRowHeight(numberHeight);
            JTableHeader header = table.getTableHeader();
            if (header != null) { if (headerFont != null) header.setFont(headerFont); header.setPreferredSize(headerSize); }
            table.revalidate(); table.repaint();
        }
    }
}
