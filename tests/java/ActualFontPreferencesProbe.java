import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Checks the installed Oracle-bundle hook without committing any user option. */
public final class ActualFontPreferencesProbe {
    public static void agentmain(String output, Instrumentation instrumentation) {
        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            try {
                Class<?> anchor = null;
                for (Class<?> type : instrumentation.getAllLoadedClasses())
                    if (type.getName().equals("oracle.ide.ceditor.options.FontSizeOptionsPanel")) anchor = type;
                if (anchor == null) throw new IllegalStateException("Oracle font preferences have not loaded yet");
                Class<?> panelType = anchor;
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        ClassLoader loader = panelType.getClassLoader();
                        Class<?> namespace = loader.loadClass("oracle.ide.util.Namespace");
                        Class<?> contextType = loader.loadClass("oracle.ide.panels.TraversableContext");
                        Class<?> storageType = loader.loadClass("oracle.javatools.data.PropertyStorage");
                        Class<?> hashType = loader.loadClass("oracle.javatools.data.HashStructure");
                        Object hash = hashType.getMethod("newInstance").invoke(null);
                        Object storage = Proxy.newProxyInstance(loader, new Class<?>[] {storageType},
                            (proxy, method, args) -> method.getName().equals("getProperties") ? hash : null);
                        Object context = contextType.getConstructor(namespace, int.class).newInstance(namespace.getConstructor().newInstance(), 0);
                        namespace.getMethod("put", String.class, Object.class).invoke(context, "generic-PropertyStorage", storage);
                        Class<?> optionsType = loader.loadClass("oracle.ide.ceditor.options.FontSizeOptions");
                        Object options = optionsType.getMethod("getInstance", storageType).invoke(null, storage);
                        optionsType.getMethod("setFontFamily", String.class).invoke(options, "Monospaced");
                        optionsType.getMethod("setFontSize", int.class).invoke(options, 40);
                        Class<?> settings = ClassLoader.getSystemClassLoader().loadClass("io.github.sqldevgrid.GridSettings");
                        Path file = (Path) settings.getMethod("path").invoke(null);
                        byte[] before = Files.readAllBytes(file);
                        Class<?> bridge = ClassLoader.getSystemClassLoader().loadClass("io.github.sqldevgrid.NativeFontPreferencesBridge");
                        Field baselines = bridge.getDeclaredField("sizes"); baselines.setAccessible(true);
                        Map<?, ?> tracked = (Map<?, ?>) baselines.get(null);
                        Object panel = panelType.getConstructor().newInstance();
                        panelType.getMethod("onEntry", contextType).invoke(panel, context);
                        if (!Integer.valueOf(40).equals(tracked.get(panel))) throw new AssertionError("Installed native onEntry hook did not reach startup agent");
                        Field sizes = panelType.getDeclaredField("comboBox_fontSize"); sizes.setAccessible(true);
                        ((JComboBox<?>) sizes.get(panel)).setSelectedItem(24);
                        panelType.getMethod("onExit", contextType).invoke(panel, context);
                        Class<?> eventType = loader.loadClass("oracle.ide.panels.ApplyEvent");
                        panelType.getMethod("cancel", eventType).invoke(panel, eventType.getConstructor(contextType).newInstance(context));
                        if (tracked.containsKey(panel) || !Arrays.equals(before, Files.readAllBytes(file)))
                            throw new AssertionError("Cancel hook or saved settings preservation failed");
                        String error = System.getProperty("sqldev.font50.nativeFontPreferencesError");
                        if (error != null) throw new AssertionError(error);
                        report.append("PASS installed Oracle font page: native bundle -> startup-agent bridge, original-size tracking, navigation and Cancel; user settings unchanged.\n");
                        report.append("PANEL_LOADER=").append(loader.getClass().getName()).append('\n');
                    } catch (Exception error) { throw new RuntimeException(error); }
                });
            } catch (Throwable error) {
                java.io.StringWriter trace = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(trace));
                report.append("FAILED\n").append(trace);
            }
            try { Files.writeString(Path.of(output), report, StandardCharsets.UTF_8); } catch (Exception ignored) { }
        }, "configurator-font-preferences-readonly-probe").start();
    }
}
