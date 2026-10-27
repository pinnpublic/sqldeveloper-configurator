package io.github.sqldevgrid;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Adds a real Oracle Navigable when SQL Developer requests the editor's preference pages. */
public final class NativePreferencesBridge {
    public static final String LABEL = "자동 줄바꿈 · 수평선";
    private static final Map<ClassLoader,Object> NODES = new WeakHashMap<>();
    public static Collection<?> extend(Collection<?> original, String parent, Class<?> registry) {
        if (!"/preferences/ceditor".equals(parent)) return original;
        try {
            ClassLoader loader=registry.getClassLoader();
            Class<?> navigable=Class.forName("oracle.ide.panels.Navigable",true,loader);
            for(Object node:original) if(LABEL.equals(navigable.getMethod("getShortLabel").invoke(node))) return original;
            Object node;
            synchronized(NODES) {
                node=NODES.get(loader);
                if(node==null) {
                    Path agent=Path.of(NativePreferencesBridge.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    Path jar=agent.resolveSibling("result-grid-font-extension.jar");
                    URLClassLoader panels=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},loader);
                    Class<?> panel=Class.forName("io.github.sqldevgrid.EditorPreferencesPanel",true,panels);
                    node=navigable.getConstructor(String.class,Class.class).newInstance(LABEL,panel);
                    NODES.put(loader,node);
                }
            }
            ArrayList<Object> result=new ArrayList<>(original);result.add(node);
            System.setProperty("sqldev.font50.nativePreferencesHook","true");
            return Collections.unmodifiableCollection(result);
        } catch(Throwable error) {
            System.setProperty("sqldev.font50.nativePreferencesError",error.toString());
            try{Files.writeString(GridSettings.path().resolveSibling("native-preferences-error.txt"),error.toString(),StandardCharsets.UTF_8);}catch(Exception ignored){}
            return original;
        }
    }
}
