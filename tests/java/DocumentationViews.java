import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import oracle.javatools.editor.BasicEditorPane;
import oracle.ide.panels.*;
import oracle.ide.util.Namespace;
import io.github.sqldevgrid.EditorPreferencesPanel;
import io.github.sqldevgrid.bridge.EditorBridge;

/** Renders real product components with documented sample input; never opens a window or a database. */
public final class DocumentationViews {
    static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container next)layout(next);}
    static void save(JComponent component,int width,int height,Path path)throws Exception{
        component.setSize(width,height);layout(component);
        BufferedImage image=new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.scale(2,2);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);g.fillRect(0,0,width,height);component.printAll(g);g.dispose();ImageIO.write(image,"png",path.toFile());
    }
    public static void main(String[]args)throws Exception{
        try{
            Path directory=Path.of(args[0]);Files.createDirectories(directory);
            Path settings=Path.of(System.getenv("APPDATA"),"SQL Developer Result Grid Font","editor-settings.json");
            Files.createDirectories(settings.getParent());Files.writeString(settings,"{\"softWrap\":true,\"horizontalRules\":true,\"ruleThickness\":2,\"ruleColor\":\"#6F8296\",\"ruleStyle\":\"solid\"}");
            SwingUtilities.invokeAndWait(()->{try{
                EditorPreferencesPanel preferences=new EditorPreferencesPanel();preferences.onEntry(new TraversableContext(new Namespace(),0));
                save(preferences,650,405,directory.resolve("preferences.png"));
                BasicEditorPane editor=new BasicEditorPane();editor.setBaseFont("JetBrainsMonoHangul NL",28);
                JScrollPane scroll=new JScrollPane(editor);scroll.setSize(900,450);scroll.doLayout();
                scroll.getViewport().setExtentSize(new Dimension(875,420));editor.setSize(875,420);editor.getUI().getRootView(editor).setSize(875,420);
                editor.setText("select id, name, created_at, status, description, updated_at from sample_table where status = 'ACTIVE' order by created_at desc;\n\n---\n\nselect '안녕하세요. --- 입니다' as message\nfrom dual;\n");
                EditorBridge.configureEditor(editor,true,true,2,0x6F8296,"solid");
                editor.getCaret().setVisible(false);save(editor,875,420,directory.resolve("editor-wrap.png"));
                JPanel rules=new JPanel(new GridLayout(4,1,0,6));rules.setBackground(Color.WHITE);
                String[] names={"실선 / Solid","파선 / Dashed","점선 / Dotted","일점쇄선 / Dash-dot"};String[] codes={"solid","dashed","dotted","dashdot"};
                for(int i=0;i<codes.length;i++){
                    JPanel line=new JPanel(new BorderLayout(10,0));line.setBackground(Color.WHITE);line.setBorder(BorderFactory.createEmptyBorder(5,12,5,12));
                    JLabel label=new JLabel(names[i]);label.setFont(new Font("Malgun Gothic",Font.PLAIN,14));label.setPreferredSize(new Dimension(145,40));line.add(label,BorderLayout.WEST);
                    BasicEditorPane marker=new BasicEditorPane();marker.setBaseFont("Monospaced",24);marker.setText("---");marker.getCaret().setVisible(false);
                    EditorBridge.configureEditor(marker,false,true,2,0x167764,codes[i]);line.add(marker,BorderLayout.CENTER);rules.add(line);
                }
                save(rules,875,250,directory.resolve("rule-styles.png"));editor.dispose();
                System.out.println("PASS documented views: native preference panel, Oracle editor, standalone rules; demo input only");
            }catch(Exception error){throw new RuntimeException(error);}});
            System.exit(0);
        }catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
}
