import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.*;
import oracle.javatools.editor.BasicEditorPane;
import oracle.ide.panels.*;
import oracle.ide.util.Namespace;
import io.github.sqldevgrid.NotebookPreferencesPanel;
import io.github.sqldevgrid.bridge.*;
import oracle.javatools.editor.gutter.LineGutterPlugin;

/** Exercises real Oracle editor geometry and native preferences using memory-only SQL. */
public final class NotebookFeatureTests {
    static final class Gutter extends LineGutterPlugin {
        int rowAt(int y){return getRowFromCoordinate(y);} void metrics(){updateMetrics();}
        public Point getMousePosition(){return null;}public Point getMousePosition(boolean children){return null;}
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Component find(Container p,String name){for(Component c:p.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container q){Component r=find(q,name);if(r!=null)return r;}}return null;}
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container q)layout(q);}
    static BufferedImage paint(BasicEditorPane e,int w,int h){
        e.setSize(w,h);e.getUI().getRootView(e).setSize(w,h);
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();e.paint(g);g.dispose();return image;
    }
    static void paper(BasicEditorPane e,String json){EditorBridge.configureNotebook(json);EditorBridge.invalidate(e);}
    static String options(String extra){return "{\"enabled\":true,\"theme\":false,\"paperColor\":\"#FAF7ED\",\"lineColor\":\"#7094AD\""+extra+"}";}
    public static void main(String[] args)throws Exception{
        try{SwingUtilities.invokeAndWait(()->{try{
            Path out=Path.of(args[0]);Files.createDirectories(out);
            BasicEditorPane editor=new BasicEditorPane();editor.setBaseFont("Monospaced",24);editor.setBackground(Color.WHITE);editor.setForeground(Color.BLACK);
            JScrollPane scroll=new JScrollPane(editor);scroll.setSize(640,480);scroll.doLayout();scroll.getViewport().setExtentSize(new Dimension(610,440));
            String sql="select '한글 값', very_long_column_name, another_long_column_name, '012345678901234567890123456789012345678901234567890123456789' from dual;\n---\n-- 다음 줄\nselect 2 from dual;\n";
            editor.setText(sql);String source=editor.getText();editor.setSize(610,3000);
            for(boolean wrap:new boolean[]{false,true})for(int size:new int[]{7,24,50})for(int spacing:new int[]{0,30,200}){
                editor.setBaseFont("Monospaced",size);EditorBridge.configureEditor(editor,wrap,true,2,0xBB2244,"solid");
                paper(editor,options(",\"spacing\":"+spacing));paint(editor,610,2200);
                Rectangle start=editor.modelToView(0),next=editor.modelToView(sql.indexOf('\n')+1);
                check(next.y>start.y,"Rows did not advance");
                if(!wrap)check(editor.modelToView(100).y==start.y,"Paper forced wrapping when disabled");
                for(int offset:new int[]{0,7,20,50,100,sql.indexOf('\n')+1,sql.length()-2}){
                    Rectangle r=editor.modelToView(offset);
                    int actual=editor.viewToModel(new Point(r.x,r.y+Math.min(2,r.height/2)));
                    check(actual==offset,"Caret round trip: "+offset+" -> "+actual+" wrap="+wrap+" font="+size+" spacing="+spacing);
                }
                editor.select(7,20);check(editor.getSelectedText().equals(source.substring(7,20)),"Selection text changed");editor.select(0,0);
                check(editor.getText().equals(source),"Notebook modified SQL");
            }
            System.out.println("PASS paper geometry: wrap on/off, 7/24/50pt, 0/30/200% spacing, caret/click round trips, selection, unchanged SQL");
            editor.setBaseFont("Monospaced",24);EditorBridge.configureEditor(editor,true,true,2,0xBB2244,"solid");
            Set<Integer> patternHashes=new HashSet<>(),lineHashes=new HashSet<>(),textureHashes=new HashSet<>();
            BufferedImage sheet=new BufferedImage(1200,1040,BufferedImage.TYPE_INT_RGB);Graphics2D sg=sheet.createGraphics();sg.setColor(Color.WHITE);sg.fillRect(0,0,1200,1040);
            for(int i=0;i<NotebookSettings.PAPER_NAMES.length;i++){
                String json=options(",\"paper\":"+i+",\"spacing\":30,\"lines\":false");paper(editor,json);
                BufferedImage image=paint(editor,600,260);int hash=Arrays.hashCode(image.getRGB(0,0,600,260,null,0,600));patternHashes.add(hash);
                sg.drawImage(image,(i%3)*400,(i/3)*260,400,260,null);sg.setColor(Color.DARK_GRAY);sg.drawString(NotebookSettings.PAPER_NAMES[i],(i%3)*400+5,(i/3)*260+250);
            }
            sg.dispose();ImageIO.write(sheet,"png",out.resolve("notebook-patterns.png").toFile());check(patternHashes.size()==12,"Missing distinct paper patterns: "+patternHashes.size());
            for(int i=0;i<NotebookSettings.LINE_NAMES.length;i++){
                paper(editor,options(",\"lineStyle\":"+i+",\"lineOpacity\":100,\"width\":8"));BufferedImage image=paint(editor,600,260);
                lineHashes.add(Arrays.hashCode(image.getRGB(0,0,600,260,null,0,600)));
            }
            check(lineHashes.size()==10,"Missing distinct line styles: "+lineHashes.size());
            for(int i=0;i<NotebookSettings.TEXTURE_NAMES.length;i++){
                paper(editor,options(",\"texture\":"+i+",\"textureOpacity\":75"));BufferedImage image=paint(editor,600,260);
                textureHashes.add(Arrays.hashCode(image.getRGB(0,0,600,260,null,0,600)));
                if(i==17)ImageIO.write(image,"png",out.resolve("notebook-xuan.png").toFile());
            }
            check(textureHashes.size()==NotebookSettings.TEXTURE_NAMES.length,"Missing distinct textures: "+textureHashes.size());
            System.out.println("PASS 12 paper patterns, 10 line styles, all "+textureHashes.size()+" texture options render distinctly");
            EditorBridge.configureEditor(editor,false,false,1,0,"solid");
            oracle.javatools.editor.plugins.LineHighlightPlugin highlight=new oracle.javatools.editor.plugins.LineHighlightPlugin();highlight.install(editor);
            editor.setCaretPosition(sql.indexOf("-- 다음"));
            int[] highlightPixels=new int[3];int ix=0;
            for(int opacity:new int[]{0,50,100}){
                paper(editor,options(",\"paper\":0,\"lines\":false,\"spacing\":0,\"highlightOpacity\":"+opacity));
                BufferedImage sample=paint(editor,600,350);Rectangle caret=editor.modelToView(editor.getCaretPosition());highlightPixels[ix++]=sample.getRGB(550,caret.y+2);
            }
            check(highlightPixels[0]!=highlightPixels[1]&&highlightPixels[1]!=highlightPixels[2],"Native current-line opacity not applied");
            editor.select(7,21);Rectangle selected=editor.modelToView(10);int[] selectionPixels=new int[2];ix=0;
            for(int opacity:new int[]{0,100}){paper(editor,options(",\"spacing\":0,\"highlightOpacity\":"+opacity));selectionPixels[ix++]=paint(editor,600,350).getRGB(selected.x+2,selected.y+2);}
            check(selectionPixels[0]==selectionPixels[1],"Current-line opacity altered native selection background");
            highlight.deinstall(editor);editor.select(0,0);EditorBridge.configureEditor(editor,true,true,2,0xBB2244,"solid");
            System.out.println("PASS actual native current-line highlight at 0/50/100%, native selection background preserved");
            paper(editor,options(",\"paper\":2,\"spacing\":90"));
            Gutter gutter=new Gutter();gutter.install(editor);gutter.setShowLineNumbers(LineGutterPlugin.SHOW_LINE_NUMBERS_ALWAYS);gutter.metrics();gutter.getPreferredSize();gutter.setSize(65,1200);
            Rectangle second=editor.modelToView(sql.indexOf('\n')+1);int expectedRow=editor.getRowForLine(1)+1;
            check(gutter.rowAt(second.y+2)==expectedRow,"Gutter click coordinates do not follow notebook row spacing: y="+second.y+" actual="+gutter.rowAt(second.y+2)+" expected="+expectedRow+" preferred="+editor.getPreferredSize());
            BufferedImage gi=new BufferedImage(65,350,BufferedImage.TYPE_INT_RGB);Graphics2D gg=gi.createGraphics();gg.setClip(0,0,65,350);gutter.paint(gg);gg.dispose();ImageIO.write(gi,"png",out.resolve("notebook-gutter.png").toFile());
            scroll.setRowHeaderView(gutter);scroll.setSize(720,420);layout(scroll);editor.setSize(editor.getPreferredSize());gutter.setSize(65,editor.getHeight());
            scroll.getViewport().setViewPosition(new Point(0,60));scroll.getRowHeader().setViewPosition(new Point(0,60));
            BufferedImage scrolled=new BufferedImage(720,420,BufferedImage.TYPE_INT_RGB);Graphics2D ss=scrolled.createGraphics();ss.setClip(0,0,720,420);scroll.printAll(ss);ss.dispose();ImageIO.write(scrolled,"png",out.resolve("notebook-scrolled.png").toFile());
            check(editor.getText().equals(source),"Gutter or scroll changed SQL");
            scroll.setRowHeaderView(null);gutter.deinstall(editor);scroll.getViewport().setViewPosition(new Point());
            System.out.println("PASS native gutter spacing/click mapping and scrolled paper with line numbers");
            editor.setBackground(new Color(0x202124));editor.setForeground(new Color(0xE8EAED));
            paper(editor,options(",\"darkPaperColor\":\"#292A2D\",\"texture\":6"));ImageIO.write(paint(editor,600,350),"png",out.resolve("notebook-dark.png").toFile());
            editor.setBackground(Color.WHITE);editor.setForeground(Color.BLACK);paper(editor,options(",\"paper\":2"));
            editor.select(7,21);ImageIO.write(paint(editor,600,350),"png",out.resolve("notebook-selection.png").toFile());
            check(editor.getSelectedText().equals(source.substring(7,21)),"Selected SQL changed");
            editor.select(0,0);paper(editor,"{\"enabled\":false}");EditorBridge.configureEditor(editor,false,false,1,0,"solid");
            check(editor.modelToView(100).y==editor.modelToView(0).y,"Disable did not restore native geometry");
            check(editor.getText().equals(source),"Toggle modified SQL");
            Path settings=Path.of(System.getenv("APPDATA"),"SQL Developer Result Grid Font","notebook-settings.json");
            Files.createDirectories(settings.getParent());String original=options(",\"spacing\":30");Files.writeString(settings,original);
            NotebookPreferencesPanel panel=new NotebookPreferencesPanel();TraversableContext context=new TraversableContext(new Namespace(),0);panel.onEntry(context);
            ((JSpinner)find(panel,"PaperSpacing")).setValue(90);((JComboBox<?>)find(panel,"PaperStyle")).setSelectedIndex(5);panel.onExit(context);
            check(Files.readString(settings).equals(original),"Navigation saved paper options");panel.onEntry(new TraversableContext(new Namespace(),0));
            check(((Number)((JSpinner)find(panel,"PaperSpacing")).getValue()).intValue()==90,"Navigation lost pending paper options");
            panel.cancel(new ApplyEvent(context));panel.onEntry(context);check(((Number)((JSpinner)find(panel,"PaperSpacing")).getValue()).intValue()==30,"Cancel kept pending paper options");
            ((JSpinner)find(panel,"PaperSpacing")).setValue(90);((JComboBox<?>)find(panel,"PaperStyle")).setSelectedIndex(5);panel.onExit(context);panel.apply(new ApplyEvent(context));
            NotebookSettings saved=NotebookSettings.parse(Files.readString(settings));check(saved.spacing==90&&saved.paper==5,"Apply lost paper options");
            NotebookPreferencesPanel reopened=new NotebookPreferencesPanel();reopened.onEntry(new TraversableContext(new Namespace(),0));check(((JComboBox<?>)find(reopened,"PaperStyle")).getSelectedIndex()==5,"Reopen lost paper options");
            ((JTextField)find(reopened,"PaperLineColor")).setText("bad");boolean rejected=false;try{reopened.onExit(new TraversableContext(new Namespace(),0));}catch(TraversalException expected){rejected=true;}check(rejected,"Invalid paper color accepted");
            panel.setSize(720,620);layout(panel);BufferedImage ui=new BufferedImage(720,620,BufferedImage.TYPE_INT_RGB);Graphics2D pg=ui.createGraphics();panel.printAll(pg);pg.dispose();ImageIO.write(ui,"png",out.resolve("notebook-preferences.png").toFile());
            check(find(panel,"PaperLineStyle").getWidth()>120&&find(panel,"PaperPreview").getHeight()>=140,"Paper panel controls clipped");
            ((JTabbedPane)find(panel,"PaperTabs")).setSelectedIndex(1);layout(panel);
            check(find(panel,"PaperStyle").getWidth()>120,"Paper background controls clipped");
            pg=ui.createGraphics();panel.printAll(pg);pg.dispose();ImageIO.write(ui,"png",out.resolve("notebook-preferences-background.png").toFile());
            System.out.println("PASS native paper preferences: navigation, Cancel, Apply, reopen, validation, controls, preview; light/dark and disable");
            EditorBridge.configureNotebook("{}");
        }catch(Exception error){throw new RuntimeException(error);}});System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
}
