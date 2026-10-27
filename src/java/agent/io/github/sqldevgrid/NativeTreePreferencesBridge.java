package io.github.sqldevgrid;

import java.lang.reflect.*;
import java.util.*;

/** Runs on the actual ExtensionNavigable child enumeration used by the preferences tree. */
public final class NativeTreePreferencesBridge {
    public static Object extend(Object original, Object parent) {
        try {
            Method pageMethod=parent.getClass().getDeclaredMethod("getPage");pageMethod.setAccessible(true);
            Object page=pageMethod.invoke(parent);
            String path=(String)page.getClass().getMethod("getIdPath").invoke(page);
            String type=(String)page.getClass().getMethod("getTraversableClassAsString").invoke(page);
            Class<?> nav=Class.forName("oracle.ide.panels.Navigable",false,parent.getClass().getClassLoader());
            String label=(String)nav.getMethod("getShortLabel").invoke(parent);
            if(!"/preferences/ceditor".equals(path) && !"oracle.ide.ceditor.options.EditorOptionsPanel".equals(type)
                && !"코드 편집기".equals(label) && !"Code Editor".equals(label))return original;
            int count=Array.getLength(original);ArrayList<Object> existing=new ArrayList<>();
            for(int i=0;i<count;i++)existing.add(Array.get(original,i));
            Collection<?> combined=NativePreferencesBridge.extend(existing,"/preferences/ceditor",parent.getClass());
            Object result=Array.newInstance(original.getClass().getComponentType(),combined.size());
            int index=0;for(Object node:combined)Array.set(result,index++,node);
            System.setProperty("sqldev.font50.nativePreferencesTreeParent",path);
            System.setProperty("sqldev.font50.nativePreferencesTreeHook","true");
            return result;
        }catch(Throwable error){System.setProperty("sqldev.font50.nativePreferencesTreeError",error.toString());return original;}
    }
}
