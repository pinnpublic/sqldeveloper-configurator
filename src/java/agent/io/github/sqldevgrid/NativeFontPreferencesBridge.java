package io.github.sqldevgrid;

import java.util.Map;
import java.util.WeakHashMap;

/** Only committed changes from Oracle's font page update the saved result size. */
public final class NativeFontPreferencesBridge {
    private static final Map<Object, Integer> sizes = new WeakHashMap<>();

    private static int size(Object panel, Object context) throws ReflectiveOperationException {
        ClassLoader loader = panel.getClass().getClassLoader();
        Class<?> options = loader.loadClass("oracle.ide.ceditor.options.FontSizeOptions");
        Class<?> storage = loader.loadClass("oracle.javatools.data.PropertyStorage");
        Object values = context.getClass().getMethod("getPropertyStorage").invoke(context);
        Object font = options.getMethod("getInstance", storage).invoke(null, values);
        return ((Number) options.getMethod("getFontSize").invoke(font)).intValue();
    }

    public static void entered(Object panel, Object context) {
        try {
            // Revisiting the page reads staged options, so keep the first value
            // until Apply/Cancel rather than treating navigation as a commit.
            if (!sizes.containsKey(panel)) sizes.put(panel, size(panel, context));
        } catch (ReflectiveOperationException | RuntimeException error) { report(error); }
    }

    public static void applied(Object panel, Object event) {
        try {
            Object context = event.getClass().getMethod("getSource").invoke(event);
            int next = size(panel, context);
            Integer previous = sizes.get(panel);
            if (previous != null && previous != next) {
                GridSettings.saveSize(next);
                ImmediateGridStyler.reloadNow();
            }
            sizes.put(panel, next);
            System.clearProperty("sqldev.font50.nativeFontPreferencesError");
        } catch (Exception error) { report(error); }
    }

    public static void canceled(Object panel) { sizes.remove(panel); }

    private static void report(Exception error) {
        System.setProperty("sqldev.font50.nativeFontPreferencesError", error.toString());
    }
}
