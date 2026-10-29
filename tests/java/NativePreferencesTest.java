import java.awt.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import oracle.ide.panels.*;
import oracle.ide.util.Namespace;
import io.github.sqldevgrid.EditorPreferencesPanel;

public final class NativePreferencesTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Component find(Container container,String name){for(Component child:container.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container next){Component found=find(next,name);if(found!=null)return found;}}return null;}
    static void layout(Container container){container.doLayout();for(Component child:container.getComponents())if(child instanceof Container next)layout(next);}
    static void edit(EditorPreferencesPanel panel){
        ((JCheckBox)find(panel,"SoftWrap")).setSelected(false);
        ((JCheckBox)find(panel,"HorizontalRules")).setSelected(true);
        ((JSpinner)find(panel,"RuleThickness")).setValue(2.5);
        ((JComboBox<?>)find(panel,"RuleStyle")).setSelectedIndex(3);
        ((JTextField)find(panel,"RuleColor")).setText("#12abef");
    }
    public static void main(String[]args)throws Exception{
        try{
            Path path=Path.of(System.getenv("APPDATA"),"SQL Developer Result Grid Font","editor-settings.json");
            Files.createDirectories(path.getParent()); String original="{\"softWrap\":true,\"horizontalRules\":false,\"ruleThickness\":1,\"ruleColor\":\"#6F8296\",\"ruleStyle\":\"solid\"}";
            Files.writeString(path,original,StandardCharsets.UTF_8);
            SwingUtilities.invokeAndWait(()->{try{
                EditorPreferencesPanel panel=new EditorPreferencesPanel();
                TraversableContext context=new TraversableContext(new Namespace(),0);panel.onEntry(context);
                check(((JCheckBox)find(panel,"SoftWrap")).isSelected(),"Native page did not load existing option");
                edit(panel);panel.onExit(context);
                check(Files.readString(path).equals(original),"Navigating preferences saved changes before Apply");
                panel.onEntry(new TraversableContext(new Namespace(),0));
                check(!((JCheckBox)find(panel,"SoftWrap")).isSelected(),"Navigating to another page lost pending settings");
                panel.cancel(new ApplyEvent(context));panel.onEntry(context);
                check(((JCheckBox)find(panel,"SoftWrap")).isSelected()&&Files.readString(path).equals(original),"Cancel did not discard pending changes");
                edit(panel);panel.onExit(context);panel.apply(new ApplyEvent(context));
                String saved=Files.readString(path);
                check(saved.contains("\"softWrap\":false")&&saved.contains("\"horizontalRules\":true")&&saved.contains("2.5")&&saved.contains("#12ABEF")&&saved.contains("dashdot"),"Apply did not save every native setting");
                EditorPreferencesPanel reopened=new EditorPreferencesPanel();reopened.onEntry(new TraversableContext(new Namespace(),0));
                check(((Number)((JSpinner)find(reopened,"RuleThickness")).getValue()).doubleValue()==2.5,"Reopened native page lost settings");
                ((JTextField)find(reopened,"RuleColor")).setText("invalid");boolean rejected=false;
                try{reopened.onExit(new TraversableContext(new Namespace(),0));}catch(TraversalException error){rejected=true;}
                check(rejected&&Files.readString(path).equals(saved),"Invalid color changed saved settings");
                panel.setSize(650,470);layout(panel);
                check(find(panel,"SoftWrap").getWidth()>200 && find(panel,"RuleColor").getWidth()>100 && find(panel,"RuleColor").getHeight()>10,"Native preference controls were not laid out");
                java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(650,470,java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",Path.of(args[0],"native-preferences.png").toFile());
                // Oracle loads a native page in its bundle loader while the startup agent owns the shared settings class.
                try(var child=new java.net.URLClassLoader(new java.net.URL[]{EditorPreferencesPanel.class.getProtectionDomain().getCodeSource().getLocation()},NativePreferencesTest.class.getClassLoader()) {
                    protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                        if(name.startsWith("io.github.sqldevgrid.EditorPreferencesPanel")) {
                            Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
                        }
                        return super.loadClass(name,resolve);
                    }
                }) {
                    DefaultTraversablePanel isolated=(DefaultTraversablePanel)child.loadClass("io.github.sqldevgrid.EditorPreferencesPanel").getConstructor().newInstance();
                    check(isolated.getClass().getClassLoader()==child,"Native page did not use a separate bundle loader");
                    isolated.onEntry(new TraversableContext(new Namespace(),0));
                    check(((Number)((JSpinner)find(isolated,"RuleThickness")).getValue()).doubleValue()==2.5,"Separate bundle could not access shared settings");
                }
                String[] styles={"solid","dashed","dotted","dashdot","longdash","shortdash","dashdotdot","longdashdot","double"};
                JSpinner width=(JSpinner)find(panel,"RuleThickness");
                JComboBox<?> choices=(JComboBox<?>)find(panel,"RuleStyle");
                check(choices.getItemCount()==styles.length,"Missing rule styles");
                SpinnerNumberModel model=(SpinnerNumberModel)width.getModel();
                check(model.getMinimum().equals(1.0)&&model.getMaximum().equals(50.0),"Thickness range is not 1~50");
                for(int i=0;i<styles.length;i++) for(double value:new double[]{1,25,50}) {
                    TraversableContext options=new TraversableContext(new Namespace(),0);
                    width.setValue(value);choices.setSelectedIndex(i);
                    panel.onExit(options);panel.apply(new ApplyEvent(options));
                    String json=Files.readString(path);
                    check(json.contains("\"ruleThickness\":"+value)&&json.contains("\"ruleStyle\":\""+styles[i]+"\""),"Rule option not saved: "+styles[i]+" / "+value);
                    EditorPreferencesPanel loaded=new EditorPreferencesPanel();loaded.onEntry(new TraversableContext(new Namespace(),0));
                    check(((Number)((JSpinner)find(loaded,"RuleThickness")).getValue()).doubleValue()==value&&((JComboBox<?>)find(loaded,"RuleStyle")).getSelectedIndex()==i,"Rule option lost on reopen");
                }
                String valid=Files.readString(path);
                for(String invalid:new String[]{"0","0.5","51","NaN","Infinity"}) {
                    ((JSpinner.DefaultEditor)width.getEditor()).getTextField().setText(invalid);
                    boolean invalidWidth=false;
                    try{panel.onExit(new TraversableContext(new Namespace(),0));}catch(TraversalException expected){invalidWidth=true;}
                    check(invalidWidth&&Files.readString(path).equals(valid),"Invalid thickness accepted: "+invalid);
                }
                width.setValue(50.0);((JSpinner.DefaultEditor)width.getEditor()).getTextField().setValue(50.0);
                choices.setSelectedIndex(0);panel.setSize(650,470);layout(panel);
                Component preview=find(panel,"RulePreview");check(preview.getHeight()>=70,"50px preview has insufficient height");
                java.awt.image.BufferedImage thick=new java.awt.image.BufferedImage(650,470,java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D thickGraphics=thick.createGraphics();panel.printAll(thickGraphics);thickGraphics.dispose();
                javax.imageio.ImageIO.write(thick,"png",Path.of(args[0],"native-preferences-50px.png").toFile());
                System.out.println("PASS 9 rule styles: 1/25/50px Apply/reopen, invalid range rejection, 50px preview");
                oracle.ide.panels.Navigable existing=new oracle.ide.panels.Navigable("기존 항목",DefaultTraversablePanel.class);
                java.util.Collection<?> originalPages=java.util.List.of(existing);
                java.util.Collection<?> extended=io.github.sqldevgrid.NativePreferencesBridge.extend(originalPages,"/preferences/ceditor",oracle.ide.config.SettingsUIRegistry.class);
                check(extended.size()==3 && originalPages.size()==1 && extended.contains(existing),"Registry hook did not preserve existing pages and append native preference");
                oracle.ide.panels.Navigable added=(oracle.ide.panels.Navigable)extended.stream().filter(n->n!=existing).findFirst().orElseThrow();
                check(added.getShortLabel().equals("자동 줄바꿈 · 수평선"),"Native registry label differs");
                check(io.github.sqldevgrid.NativePreferencesBridge.extend(extended,"/preferences/ceditor",oracle.ide.config.SettingsUIRegistry.class)==extended,"Native registry page duplicated");
                check(io.github.sqldevgrid.NativePreferencesBridge.extend(originalPages,"/preferences/database",oracle.ide.config.SettingsUIRegistry.class)==originalPages,"Registry hook changed other preference sections");
                java.lang.reflect.Method instantiate=oracle.ide.panels.MetaTraversable.class.getDeclaredMethod("newTraversable");instantiate.setAccessible(true);
                Object traversable=instantiate.invoke(added);
                check(traversable instanceof DefaultTraversablePanel,"Native registry cannot create the actual preference panel");
                System.out.println("PASS native preference registry integration: existing pages preserved, correct section, no duplicates, real native panel instantiation");
                System.out.println("PASS native Oracle Preferences panel: load, navigation, Cancel, Apply, reopen, color validation, render");
            }catch(Exception error){throw new RuntimeException(error);}});
            System.exit(0);
        }catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
}
