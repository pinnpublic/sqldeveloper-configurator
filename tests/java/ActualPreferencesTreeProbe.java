import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
public final class ActualPreferencesTreeProbe {
    static final String LABEL="자동 줄바꿈 · 수평선";
    public static void agentmain(String output,Instrumentation instrumentation){new Thread(()->{
        StringBuilder report=new StringBuilder();try{
            Class<?> anchor=null;for(Class<?> type:instrumentation.getAllLoadedClasses())if(type.getName().equals("oracle.ide.ceditor.options.FontSizeOptionsPanel"))anchor=type;
            if(anchor==null)throw new IllegalStateException("Oracle editor not loaded yet");final ClassLoader loader=anchor.getClassLoader();
            SwingUtilities.invokeAndWait(()->{try{
                Class<?> nav=loader.loadClass("oracle.ide.panels.Navigable");Class<?> settings=loader.loadClass("oracle.ide.config.IdeSettings");
                Object root=null;for(Object entry:(Object[])settings.getMethod("getNavigables").invoke(null)){
                    String label=(String)nav.getMethod("getShortLabel").invoke(entry);if(label.equals("코드 편집기")||label.equals("Code Editor"))root=entry;
                }
                if(root==null)throw new IllegalStateException("Actual code editor preference root not found");
                Object added=null;int count=0;for(Object child:(Object[])nav.getMethod("getChildren").invoke(root)){
                    String label=(String)nav.getMethod("getShortLabel").invoke(child);report.append("ACTUAL EDITOR CHILD ").append(label).append('\n');if(LABEL.equals(label)){added=child;count++;}
                }
                if(count!=1)throw new IllegalStateException("Actual tree child enumeration has "+count+" matching entries; error="+System.getProperty("sqldev.font50.nativePreferencesTreeError"));
                Class<?> namespace=loader.loadClass("oracle.ide.util.Namespace"),nc=loader.loadClass("oracle.ide.panels.NavigableContext");
                Constructor<?> constructor=nc.getDeclaredConstructor(namespace,boolean.class);constructor.setAccessible(true);
                Object context=constructor.newInstance(namespace.getConstructor().newInstance(),false);
                DefaultMutableTreeNode tree=(DefaultMutableTreeNode)nav.getMethod("createTreeNode",nc).invoke(root,context);
                Method expand=nav.getDeclaredMethod("lazyLoadChildNavigables",DefaultMutableTreeNode.class,nc);expand.setAccessible(true);expand.invoke(root,tree,context);
                int nodes=0;for(int i=0;i<tree.getChildCount();i++)if(LABEL.equals(tree.getChildAt(i).toString()))nodes++;
                if(nodes!=1)throw new IllegalStateException("Oracle's real lazy tree builder did not include the preference: "+nodes);
                Class<?> meta=loader.loadClass("oracle.ide.panels.MetaTraversable");Method instantiate=meta.getDeclaredMethod("newTraversable");instantiate.setAccessible(true);Object panel=instantiate.invoke(added);
                if(!panel.getClass().getName().equals("io.github.sqldevgrid.EditorPreferencesPanel"))throw new IllegalStateException("Incorrect preference panel");
                Class<?> tc=loader.loadClass("oracle.ide.panels.TraversableContext");Object values=tc.getConstructor(namespace,int.class).newInstance(namespace.getConstructor().newInstance(),0);
                panel.getClass().getMethod("onEntry",tc).invoke(panel,values);
                report.append("PASS ACTUAL ORACLE LEFT TREE: IdeSettings root, actual child enumeration, native lazy tree builder, one preference entry, real preference panel and option loading.\n");
                report.append("PARENT=").append(System.getProperty("sqldev.font50.nativePreferencesTreeParent")).append(" TREE_HOOK=").append(System.getProperty("sqldev.font50.nativePreferencesTreeHook")).append('\n');
                for(Window w:Window.getWindows())if(w.isShowing())inspect(w,report);
            }catch(Throwable error){throw new RuntimeException(error);}});
        }catch(Throwable error){java.io.StringWriter text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));report.append("FAILED\n").append(text);}
        try{Files.writeString(Path.of(output),report,StandardCharsets.UTF_8);}catch(Exception ignored){}
    },"font50-actual-left-tree-verification").start();}
    static void inspect(Component c,StringBuilder report){if(c instanceof JTree t)inspectNode(t.getModel(),t.getModel().getRoot(),report);if(c instanceof Container p)for(Component child:p.getComponents())inspect(child,report);}
    static void inspectNode(TreeModel model,Object n,StringBuilder report){if(n.toString().equals("코드 편집기")){report.append("EXISTING DIALOG CODE EDITOR CHILDREN\n");for(int i=0;i<model.getChildCount(n);i++)report.append("VISIBLE MODEL ").append(model.getChild(n,i)).append('\n');}for(int i=0;i<model.getChildCount(n);i++)inspectNode(model,model.getChild(n,i),report);}
}
