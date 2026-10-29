package io.github.sqldevgrid;

import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import java.net.URLClassLoader;

/** Starts independently of SQL Developer's lazy extension registry. */
public final class BootstrapAgent {
    static volatile Instrumentation instrumentation;
    public static void premain(String arguments, Instrumentation value) {
        instrumentation = value;
        try {
            Path folder = GridSettings.path().getParent(); Files.createDirectories(folder);
            byte[] helper;
            try (java.io.InputStream input = BootstrapAgent.class.getResourceAsStream("/editor-bridge.jar")) {
                if (input == null) throw new IllegalStateException("Missing editor bridge"); helper = input.readAllBytes();
            }
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(helper)).substring(0, 16);
            Path jar = folder.resolve("editor-bridge-" + hash + ".jar");
            if (!Files.exists(jar)) Files.write(jar, helper);
            value.appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(jar.toFile()));
            value.redefineModule(Object.class.getModule(), java.util.Set.of(),
                java.util.Map.of("jdk.internal.org.objectweb.asm", java.util.Set.of(BootstrapAgent.class.getModule())), java.util.Map.of(), java.util.Set.of(), java.util.Map.of());
            value.addTransformer((java.lang.instrument.ClassFileTransformer)Class.forName("io.github.sqldevgrid.EditorTransformer").getConstructor().newInstance());
        } catch (Exception error) {
            System.setProperty("sqldev.font50.bootstrapError", error.toString());
            try { Files.writeString(GridSettings.path().resolveSibling("editor-bootstrap-error.txt"), error.toString(), StandardCharsets.UTF_8); } catch (Exception ignored) { }
        }
        try {
            Path folder = GridSettings.path().getParent(); Files.createDirectories(folder);
            Files.writeString(folder.resolve("agent-started.json"), "{\"version\":\"0.12.0\",\"pid\":" + ProcessHandle.current().pid() + ",\"timestamp\":" + System.currentTimeMillis() + "}", StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
        start();
        String testClasses = System.getProperty("sqldev.font50.selftest.classes");
        if (testClasses != null) {
            Thread test = new Thread(() -> runOracleTest(testClasses), "font50-oracle-selftest"); test.setDaemon(true); test.start();
        }
    }
    public static void start() {
        synchronized (System.getProperties()) {
            if (Boolean.getBoolean("sqldev.font50.watcherStarted")) return;
            System.setProperty("sqldev.font50.watcherStarted", "true");
        }
        SwingUtilities.invokeLater(() -> { ImmediateGridStyler.start(); EditorFeatureService.start(); });
    }
    static Class<?> editorPanelClass() throws ClassNotFoundException {
        if (instrumentation != null) {
            for (Class<?> type : instrumentation.getAllLoadedClasses())
                if (type.getName().equals("oracle.ide.ceditor.options.FontSizeOptionsPanel")) return type;
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                if (type.getName().equals("oracle.ide.ceditor.CodeEditorAddin"))
                    return Class.forName("oracle.ide.ceditor.options.FontSizeOptionsPanel", true, type.getClassLoader());
            }
        }
        return Class.forName("oracle.ide.ceditor.options.FontSizeOptionsPanel");
    }
    public static void styleTableForTest(JTable table, int size, int padding) {
        new ResultGridWatcher.Style(table).apply(table, new GridSettings(true, "JetBrainsMonoHangul NL", size, padding, false));
    }
    private static void runOracleTest(String classes) {
        Path result = GridSettings.path().getParent().resolve("oracle-test-result.txt");
        Throwable last = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                Thread.sleep(2000);
                ClassLoader loader = null;
                for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                    if (type.getName().equals("oracle.dbtools.raptor.config.DBConfig") || type.getName().equals("oracle.dbtools.raptor.plsql.BackgroundParser")) { loader = type.getClassLoader(); break; }
                }
                if (loader == null) continue;
                try (URLClassLoader tests = new URLClassLoader(new java.net.URL[] {Path.of(classes).toUri().toURL()}, loader)) {
                    Class<?> fixture = Class.forName("io.github.sqldevgrid.OracleGridLayoutTest", true, tests);
                    fixture.getMethod("main", String[].class).invoke(null, (Object)new String[] {Path.of(classes).resolve("oracle-first-paint").toString(), "production"});
                }
                Files.writeString(result, "PASS production first-paint hook on actual Oracle ResultSetTable inside SQL Developer; attach, row-height reset and model replacement corrected synchronously, without timer waits or manual styling", StandardCharsets.UTF_8);
                return;
            } catch (Throwable error) {
                last = error;
                try {
                    java.io.StringWriter text = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(text));
                    Files.writeString(result, "RETRY " + attempt + " " + text, StandardCharsets.UTF_8);
                } catch (Exception ignored) { }
            }
        }
        try {
            java.io.StringWriter text = new java.io.StringWriter();
            if (last != null) last.printStackTrace(new java.io.PrintWriter(text));
            Files.writeString(result, "FAILED " + text, StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }
}
