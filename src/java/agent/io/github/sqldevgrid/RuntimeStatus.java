package io.github.sqldevgrid;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Field;
import javax.swing.JComboBox;

/** Local health report only. Does not contain SQL, values or connection details. */
final class RuntimeStatus {
    private static long previous;
    private static int menuMax = -1;
    static void write(int grids, int font, int height, String error) {
        long now = System.currentTimeMillis();
        if (now - previous < 1800) return;
        previous = now;
        try {
            if (menuMax <= 0) {
                Class<?> type = BootstrapAgent.editorPanelClass();
                Object panel = type.getConstructor().newInstance();
                Field field = type.getDeclaredField("comboBox_fontSize"); field.setAccessible(true);
                JComboBox<?> sizes = (JComboBox<?>) field.get(panel);
                menuMax = 0;
                for (int i = 0; i < sizes.getItemCount(); i++) menuMax = Math.max(menuMax, ((Number) sizes.getItemAt(i)).intValue());
            }
        } catch (Exception ex) { menuMax = 0; }
        try {
            Path folder = GridSettings.path().getParent(); Files.createDirectories(folder);
            String json = "{\"version\":\"0.14.0\",\"pid\":" + ProcessHandle.current().pid() + ",\"timestamp\":" + now
                + ",\"editorMenuMax\":" + menuMax + ",\"grids\":" + grids + ",\"fontSize\":" + font
                + ",\"rowHeight\":" + height + ",\"error\":" + Configure.quote(error) + "}";
            Path temp = folder.resolve("runtime-state.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, folder.resolve("runtime-state.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { }
    }
}
