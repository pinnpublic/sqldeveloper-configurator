package io.github.sqldevgrid;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Adds a real Oracle Navigable when SQL Developer requests the editor's preference pages. */
public final class NativePreferencesBridge {
    public static final String LABEL = "자동 줄바꿈 · 수평선";
    public static final String PAPER_LABEL = "노트 배경";
    private static final Map<ClassLoader,List<Object>> NODES = new WeakHashMap<>();
    public static Collection<?> extend(Collection<?> original, String parent, Class<?> registry) {
        if (!"/preferences/ceditor".equals(parent)) return original;
        try {
            ClassLoader loader=registry.getClassLoader();
            Class<?> navigable=Class.forName("oracle.ide.panels.Navigable",true,loader);
            Set<String> labels=new HashSet<>();
            for(Object node:original)labels.add((String)navigable.getMethod("getShortLabel").invoke(node));
            if(labels.contains(LABEL)&&labels.contains(PAPER_LABEL))return original;
            List<Object> nodes;
            synchronized(NODES) {
                nodes=NODES.get(loader);
                if(nodes==null) {
                    Path agent=Path.of(NativePreferencesBridge.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    Path jar=agent.resolveSibling("result-grid-font-extension.jar");
                    URLClassLoader panels=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},loader);
                    nodes=new ArrayList<>();
                    for(String[] entry:new String[][]{{LABEL,"EditorPreferencesPanel"},{PAPER_LABEL,"NotebookPreferencesPanel"}}) {
                        Class<?> panel=Class.forName("io.github.sqldevgrid."+entry[1],true,panels);
                        nodes.add(navigable.getConstructor(String.class,Class.class).newInstance(entry[0],panel));
                    }
                    NODES.put(loader,nodes);
                }
            }
            ArrayList<Object> result=new ArrayList<>(original);
            for(Object node:nodes)if(!labels.contains((String)navigable.getMethod("getShortLabel").invoke(node)))result.add(node);
            System.setProperty("sqldev.font50.nativePreferencesHook","true");
            return Collections.unmodifiableCollection(result);
        } catch(Throwable error) {
            System.setProperty("sqldev.font50.nativePreferencesError",error.toString());
            try{Files.writeString(GridSettings.path().resolveSibling("native-preferences-error.txt"),error.toString(),StandardCharsets.UTF_8);}catch(Exception ignored){}
            return original;
        }
    }
}
