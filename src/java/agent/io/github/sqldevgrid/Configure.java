package io.github.sqldevgrid;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;

/** Standalone settings dialog; no Oracle classes or database connection needed. */
public final class Configure {
    static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GridSettings current = GridSettings.load();
            JCheckBox enabled = new JCheckBox("Enable result grid font and padding", current.enabled);
            JComboBox<String> family = new JComboBox<>(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            family.insertItemAt("(Follow SQL editor)", 0);
            family.setSelectedItem(current.family.isBlank() ? "(Follow SQL editor)" : current.family);
            JCheckBox follow = new JCheckBox("Follow SQL editor size", current.size == 0);
            JSpinner size = new JSpinner(new SpinnerNumberModel(current.size == 0 ? 18 : current.size, 6, 50, 1));
            JSpinner padding = new JSpinner(new SpinnerNumberModel(current.padding, -20, 30, 1));
            JCheckBox header = new JCheckBox("Apply font to column headers", current.header);
            size.setEnabled(!follow.isSelected());
            follow.addActionListener(e -> size.setEnabled(!follow.isSelected()));
            JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
            form.add(enabled); form.add(new JLabel(""));
            form.add(new JLabel("Font (installed on this PC)")); form.add(family);
            form.add(follow); form.add(new JLabel(""));
            form.add(new JLabel("Font size (6-50 pt)")); form.add(size);
            form.add(new JLabel("Top AND bottom padding (pixels)")); form.add(padding);
            form.add(header); form.add(new JLabel(""));
            if (JOptionPane.showConfirmDialog(null, form, "SQL Developer Font 50", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            try {
                size.commitEdit(); padding.commitEdit();
                int chosenSize = ((Number) size.getValue()).intValue(), chosenPadding = ((Number) padding.getValue()).intValue();
                if (chosenSize < 6 || chosenSize > 50 || chosenPadding < -20 || chosenPadding > 30) throw new IllegalArgumentException("Size: 6-50, padding: -20 to 30");
                Path path = GridSettings.path(); Files.createDirectories(path.getParent());
                String chosenFamily = family.getSelectedIndex() == 0 ? "" : String.valueOf(family.getSelectedItem());
                String json = "{\n  \"enabled\": " + enabled.isSelected() + ",\n  \"fontFamily\": " + quote(chosenFamily)
                    + ",\n  \"fontSize\": " + (follow.isSelected() ? 0 : chosenSize)
                    + ",\n  \"paddingPerSide\": " + chosenPadding + ",\n  \"applyToHeader\": " + header.isSelected() + "\n}\n";
                if (Files.exists(path)) Files.copy(path, path.resolveSibling("settings.before-font50.json"), StandardCopyOption.REPLACE_EXISTING);
                Path temporary = Files.createTempFile(path.getParent(), "font50-", ".tmp");
                Files.writeString(temporary, json, StandardCharsets.UTF_8);
                try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
                JOptionPane.showMessageDialog(null, "Saved. Open result grids update automatically.");
            } catch (Exception ex) { JOptionPane.showMessageDialog(null, ex.toString(), "Could not save", JOptionPane.ERROR_MESSAGE); }
        });
    }
}
