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
                Object added=null,paperNode=null;int count=0,paperCount=0;for(Object child:(Object[])nav.getMethod("getChildren").invoke(root)){
                    String label=(String)nav.getMethod("getShortLabel").invoke(child);report.append("ACTUAL EDITOR CHILD ").append(label).append('\n');if(LABEL.equals(label)){added=child;count++;}
                    if("노트 배경".equals(label)){paperNode=child;paperCount++;}
                }
                if(count!=1)throw new IllegalStateException("Actual tree child enumeration has "+count+" matching entries; error="+System.getProperty("sqldev.font50.nativePreferencesTreeError"));
                if(paperCount!=1)throw new IllegalStateException("Actual editor tree has "+paperCount+" notebook entries");
                Class<?> namespace=loader.loadClass("oracle.ide.util.Namespace"),nc=loader.loadClass("oracle.ide.panels.NavigableContext");
                Constructor<?> constructor=nc.getDeclaredConstructor(namespace,boolean.class);constructor.setAccessible(true);
                Object context=constructor.newInstance(namespace.getConstructor().newInstance(),false);
                DefaultMutableTreeNode tree=(DefaultMutableTreeNode)nav.getMethod("createTreeNode",nc).invoke(root,context);
                Method expand=nav.getDeclaredMethod("lazyLoadChildNavigables",DefaultMutableTreeNode.class,nc);expand.setAccessible(true);expand.invoke(root,tree,context);
                int nodes=0;for(int i=0;i<tree.getChildCount();i++)if(LABEL.equals(tree.getChildAt(i).toString()))nodes++;
                if(nodes!=1)throw new IllegalStateException("Oracle's real lazy tree builder did not include the preference: "+nodes);
                int paperNodes=0;for(int i=0;i<tree.getChildCount();i++)if("노트 배경".equals(tree.getChildAt(i).toString()))paperNodes++;
                if(paperNodes!=1)throw new IllegalStateException("Native lazy builder omitted notebook page");
                Class<?> meta=loader.loadClass("oracle.ide.panels.MetaTraversable");Method instantiate=meta.getDeclaredMethod("newTraversable");instantiate.setAccessible(true);Object panel=instantiate.invoke(added);
                if(!panel.getClass().getName().equals("io.github.sqldevgrid.EditorPreferencesPanel"))throw new IllegalStateException("Incorrect preference panel");
                Class<?> tc=loader.loadClass("oracle.ide.panels.TraversableContext");Object values=tc.getConstructor(namespace,int.class).newInstance(namespace.getConstructor().newInstance(),0);
                panel.getClass().getMethod("onEntry",tc).invoke(panel,values);
                JSpinner thickness=(JSpinner)find((Container)panel,"RuleThickness");
                JComboBox<?> styles=(JComboBox<?>)find((Container)panel,"RuleStyle");
                SpinnerNumberModel range=(SpinnerNumberModel)thickness.getModel();
                if(!range.getMinimum().equals(1.0)||!range.getMaximum().equals(50.0)||styles.getItemCount()!=9)
                    throw new IllegalStateException("Installed rule options are not 1~50px / 9 styles");
                report.append("PASS INSTALLED RULE OPTIONS: 1~50px, 9 styles, loaded thickness=").append(thickness.getValue()).append(", style=").append(styles.getSelectedItem()).append('\n');
                JComponent component=(JComponent)panel;
                component.setSize(650,470);layout(component);
                java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(650,470,java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=image.createGraphics();component.printAll(graphics);graphics.dispose();
                javax.imageio.ImageIO.write(image,"png",Path.of(output).resolveSibling("installed-preferences.png").toFile());
                Object notebook=instantiate.invoke(paperNode);
                notebook.getClass().getMethod("onEntry",tc).invoke(notebook,values);
                JComponent paperComponent=(JComponent)notebook;paperComponent.setSize(720,640);layout(paperComponent);
                if(((JComboBox<?>)find(paperComponent,"PaperStyle")).getItemCount()!=12||((JComboBox<?>)find(paperComponent,"PaperLineStyle")).getItemCount()!=10||((JComboBox<?>)find(paperComponent,"PaperTexture")).getItemCount()!=36)
                    throw new IllegalStateException("Installed notebook options missing");
                java.awt.image.BufferedImage pi=new java.awt.image.BufferedImage(720,640,java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D pg=pi.createGraphics();paperComponent.printAll(pg);pg.dispose();
                javax.imageio.ImageIO.write(pi,"png",Path.of(output).resolveSibling("installed-notebook-preferences.png").toFile());
                Class<?> paperBridge=Class.forName("io.github.sqldevgrid.bridge.NotebookBridge",true,null);
                boolean expectedEnabled=((JCheckBox)find(paperComponent,"PaperEnabled")).isSelected();
                if((Boolean)paperBridge.getMethod("enabled").invoke(null)!=expectedEnabled)throw new IllegalStateException("Notebook startup bridge differs from saved preference");
                Class<?> editorType=loader.loadClass("oracle.javatools.editor.BasicEditorPane");JComponent editor=(JComponent)editorType.getConstructor().newInstance();
                editorType.getMethod("setBaseFont",String.class,int.class).invoke(editor,"Monospaced",24);
                editorType.getMethod("setText",String.class).invoke(editor,"-- 메모리 검증용 SQL — 실행하지 않음\nselect '노트 배경', 123 from dual;\n---\nselect '줄바꿈과 라인 표시' from dual;\n");
                JScrollPane memoryScroll=new JScrollPane(editor);memoryScroll.setSize(900,500);layout(memoryScroll);editor.setSize(875,600);
                java.awt.image.BufferedImage ei=new java.awt.image.BufferedImage(875,500,java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D eg=ei.createGraphics();editor.printAll(eg);eg.dispose();
                javax.imageio.ImageIO.write(ei,"png",Path.of(output).resolveSibling("installed-notebook-editor.png").toFile());
                javax.swing.text.JTextComponent textEditor=(javax.swing.text.JTextComponent)editor;
                textEditor.setCaretPosition(3);textEditor.getCaret().setMagicCaretPosition(null);
                Rectangle before=textEditor.modelToView(3);
                Object kit=editorType.getMethod("getEditorKit").invoke(editor);
                javax.swing.Action down=(javax.swing.Action)kit.getClass().getMethod("findAction",String.class).invoke(kit,"caret-down");
                javax.swing.Action up=(javax.swing.Action)kit.getClass().getMethod("findAction",String.class).invoke(kit,"caret-up");
                down.actionPerformed(new java.awt.event.ActionEvent(editor,0,""));Rectangle after=textEditor.modelToView(textEditor.getCaretPosition());
                if(after.y!=before.y+before.height)throw new IllegalStateException("Installed native Down action did not move one visual row: "+before+" -> "+after);
                up.actionPerformed(new java.awt.event.ActionEvent(editor,0,""));
                if(textEditor.modelToView(textEditor.getCaretPosition()).y!=before.y)throw new IllegalStateException("Installed native Up action did not return");
                report.append("PASS INSTALLED KEYBOARD: native Down/Up actions in detached actual Oracle editor, using saved notebook settings; no keyboard/mouse input to user documents.\n");
                report.append("PASS INSTALLED NOTEBOOK: actual child and native lazy tree, real panel option loading, 12 backgrounds / 10 lines / 36 textures, enabled startup bridge, detached actual Oracle editor rendering without DB or user-document changes.\n");
                report.append("PASS ACTUAL ORACLE LEFT TREE: IdeSettings root, actual child enumeration, native lazy tree builder, one preference entry, real preference panel and option loading.\n");
                report.append("PARENT=").append(System.getProperty("sqldev.font50.nativePreferencesTreeParent")).append(" TREE_HOOK=").append(System.getProperty("sqldev.font50.nativePreferencesTreeHook")).append('\n');
                for(Window w:Window.getWindows())if(w.isShowing())inspect(w,report);
            }catch(Throwable error){throw new RuntimeException(error);}});
        }catch(Throwable error){java.io.StringWriter text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));report.append("FAILED\n").append(text);}
        try{Files.writeString(Path.of(output),report,StandardCharsets.UTF_8);}catch(Exception ignored){}
    },"font50-actual-left-tree-verification").start();}
    static Component find(Container parent,String name){for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container next){Component found=find(next,name);if(found!=null)return found;}}return null;}
    static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container next)layout(next);}
    static void inspect(Component c,StringBuilder report){if(c instanceof JTree t)inspectNode(t.getModel(),t.getModel().getRoot(),report);if(c instanceof Container p)for(Component child:p.getComponents())inspect(child,report);}
    static void inspectNode(TreeModel model,Object n,StringBuilder report){if(n.toString().equals("코드 편집기")){report.append("EXISTING DIALOG CODE EDITOR CHILDREN\n");for(int i=0;i<model.getChildCount(n);i++)report.append("VISIBLE MODEL ").append(model.getChild(n,i)).append('\n');}for(int i=0;i<model.getChildCount(n);i++)inspectNode(model,model.getChild(n,i),report);}
}
