package io.github.sqldevgrid;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public final class GridLayoutTest {
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new Object[][] {{"한글 Agjpqy 0123456789", 12345}, {"Selected", 5}}, new Object[] {"Text", "Number"});
            Font originalFont = table.getFont(); int originalHeight = table.getRowHeight();
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
            table.getColumnModel().getColumn(0).setCellRenderer(renderer);
            JTable numbers = new JTable(new Object[][] {{1}, {2}}, new Object[] {"#"});
            int originalNumberHeight = numbers.getRowHeight();
            JScrollPane scroll = new JScrollPane(table); scroll.setRowHeaderView(numbers);
            for (int size : new int[] {24, 34, 50}) {
                ResultGridWatcher.Style style = new ResultGridWatcher.Style(table);
                GridSettings settings = new GridSettings(true, Font.MONOSPACED, size, 6, true);
                style.apply(table, settings);
                int height = table.getRowHeight();
                for (boolean focused : new boolean[] {false, true}) {
                    Component component = table.getCellRenderer(0, 0).getTableCellRendererComponent(table, table.getValueAt(0, 0), true, focused, 0, 0);
                    require(height - table.getRowMargin() >= component.getPreferredSize().height, "Renderer clipped at " + size);
                    require(height - table.getRowMargin() - component.getPreferredSize().height >= 12,
                        "Missing top/bottom space at " + size);
                    require(table.getColumnModel().getColumn(0).getCellRenderer() == renderer,
                        "Custom native renderer was replaced");
                    require(table.getColumnModel().getColumn(1).getCellRenderer() == null,
                        "Default renderer lookup was replaced");
                }
                require(numbers.getRowHeight() == height, "Row numbers out of alignment");
                require(table.getTableHeader().getPreferredSize().height >= height, "Header clipped");
                for (int i = 0; i < 20; i++) style.apply(table, settings);
                require(table.getRowHeight() == height, "Padding grows on every scan");
                if (size == 50 && args.length > 0) {
                    table.getColumnModel().getColumn(0).setPreferredWidth(900);
                    table.getColumnModel().getColumn(1).setPreferredWidth(350);
                    table.setSize(1250, height * 2);
                    BufferedImage preview = new BufferedImage(1250, height * 2, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = preview.createGraphics(); table.doLayout(); table.printAll(graphics); graphics.dispose();
                    try { ImageIO.write(preview, "png", new File(args[0])); } catch (Exception ex) { throw new RuntimeException(ex); }
                }
                table.setRowHeight(0, 10); style.apply(table, settings);
                require(table.getRowHeight(0) >= height, "Stale per-row height not cleared");
                style.restore(table);
                require(table.getFont().equals(originalFont) && table.getRowHeight() == originalHeight, "Original style not restored");
                require(table.getColumnModel().getColumn(0).getCellRenderer() == renderer, "Original renderer not restored");
                require(table.getColumnModel().getColumn(1).getCellRenderer() == null, "Default renderer not restored");
                require(numbers.getRowHeight() == originalNumberHeight, "Row number height not restored");
                System.out.println("PASS " + size + "pt: row height=" + height + ", padding, selected/focused cells, row numbers, restoration");
            }
            ResultGridWatcher.Style style = new ResultGridWatcher.Style(table);
            GridSettings follow = new GridSettings(true, "", 0, 6, false);
            style.apply(table, follow); table.setFont(originalFont.deriveFont(50f)); style.apply(table, follow);
            require(table.getFont().getSize() == 50, "Follow editor did not follow font changes");
            require(new GridSettings(true, "", 200, 6, false).font(originalFont).getSize() == 50, "Result grid size exceeded 50pt");
            System.out.println("PASS follow-editor size updates");
            for (int padding : new int[] {-20, 0, 30}) {
                String json = "{\"paddingPerSide\":" + padding + "}";
                int parsed = Integer.parseInt(GridSettings.value(json, "paddingPerSide", "999"));
                require(parsed == padding, "Signed padding JSON was not read: " + padding);
                GridSettings settings = new GridSettings(true, Font.MONOSPACED, 6, parsed, false);
                require(settings.padding == padding, "Padding was clamped unexpectedly: " + padding);
                ResultGridWatcher.Style boundary = new ResultGridWatcher.Style(table);
                boundary.apply(table, settings);
                require(table.getRowHeight() >= 1, "Negative padding produced an invalid row height");
                boundary.restore(table);
                System.out.println("PASS padding " + padding + "px: signed JSON, rendering and valid row height");
            }
        });
    }
}
