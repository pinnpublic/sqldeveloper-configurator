package io.github.sqldevgrid;

import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

public final class GridSettings {
    final boolean enabled;
    final String family;
    final int size, padding;
    final boolean header;
    GridSettings(boolean enabled, String family, int size, int padding, boolean header) {
        this.enabled = enabled; this.family = family; this.size = size == 0 ? 0 : Math.max(6, Math.min(50, size));
        this.padding = Math.max(-20, Math.min(30, padding)); this.header = header;
    }
    public static Path path() {
        String appdata = System.getenv("APPDATA");
        Path root = appdata == null ? Path.of(System.getProperty("user.home"), "AppData", "Roaming") : Path.of(appdata);
        return root.resolve("SQL Developer Result Grid Font").resolve("settings.json");
    }
    public static String value(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|true|false|-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }
    static GridSettings load() {
        try {
            if (!Files.isRegularFile(path())) return new GridSettings(true, "", 0, 6, false);
            String json = Files.readString(path(), StandardCharsets.UTF_8);
            String family = value(json, "fontFamily", "\"\"");
            family = family.substring(1, family.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            int size = Integer.parseInt(value(json, "fontSize", "0"));
            int totalPadding = Integer.parseInt(value(json, "verticalPadding", "12"));
            int perSide = Integer.parseInt(value(json, "paddingPerSide", Integer.toString((totalPadding + 1) / 2)));
            return new GridSettings(Boolean.parseBoolean(value(json, "enabled", "true")), family,
                size == 0 ? 0 : Math.max(6, Math.min(50, size)), perSide, Boolean.parseBoolean(value(json, "applyToHeader", "false")));
        } catch (Exception ex) {
            return new GridSettings(true, "", 0, 6, false);
        }
    }
    Font font(Font original) {
        return new Font(family.isBlank() ? original.getFamily() : family, original.getStyle(),
            size == 0 ? Math.max(6, Math.min(50, original.getSize())) : size);
    }
    String key() { return enabled + "|" + family + "|" + size + "|" + padding + "|" + header; }
}
