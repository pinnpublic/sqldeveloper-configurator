import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.*;
import oracle.javatools.editor.*;
import io.github.sqldevgrid.bridge.EditorBridge;

/** Real Oracle keyboard actions and UI-thread work; never connects to a database. */
public final class EditorResponsivenessTests {
    static final java.util.List<String> failures=new ArrayList<>();
    static void check(boolean ok,String message){if(!ok)failures.add(message);}
    static BasicEditorPane editor(String text,int font,int width,boolean wrap,int spacing,int texture){
        BasicEditorPane e=new BasicEditorPane();e.setBaseFont("Monospaced",font);e.setText(text);
        JScrollPane scroll=new JScrollPane(e);scroll.setSize(width+25,500);scroll.doLayout();scroll.getViewport().setExtentSize(new Dimension(width,460));
        e.setSize(width,2000000);e.getUI().getRootView(e).setSize(width,2000000);
        EditorBridge.configureEditor(e,wrap,true,2,0x808080,"solid");
        EditorBridge.configureNotebook("{\"enabled\":true,\"spacing\":"+spacing+",\"texture\":"+texture+"}");EditorBridge.invalidate(e);return e;
    }
    static void action(BasicEditorPane e,String name){Action a=((BasicEditorKit)e.getEditorKit()).findAction(name);check(a!=null,"Missing native action "+name);if(a!=null)a.actionPerformed(new ActionEvent(e,0,""));}
    static void paint(BasicEditorPane e){BufferedImage image=new BufferedImage(760,460,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setClip(0,0,760,460);e.paint(g);g.dispose();}
    static void navigation()throws Exception{
        String sql="select abcdefghijklmnopqrstuvwxyz, '한글 값', another_column, '\t123456789012345678901234567890' from dual;\n\nselect 2 from dual;\n---\nselect 3 from dual;\n";
        for(boolean wrap:new boolean[]{false,true})for(int font:new int[]{7,24,50})for(int spacing:new int[]{0,30,90,200}){
            BasicEditorPane e=editor(sql,font,350,wrap,spacing,0);String source=e.getText();
            e.setCaretPosition(3);e.getCaret().setMagicCaretPosition(null);
            for(int i=0;i<3;i++){
                int before=e.getCaretPosition();Rectangle from=e.modelToView(before);action(e,"caret-down");Rectangle to=e.modelToView(e.getCaretPosition());
                check(to.y==from.y+from.height,"Down stuck/skipped: wrap="+wrap+" font="+font+" spacing="+spacing+" at="+before+" y="+from.y+" -> "+to.y);
            }
            for(int i=0;i<3;i++){
                Rectangle from=e.modelToView(e.getCaretPosition());action(e,"caret-up");Rectangle to=e.modelToView(e.getCaretPosition());
                check(to.y==Math.max(e.getInsets().top,from.y-from.height),"Up stuck/skipped: wrap="+wrap+" font="+font+" spacing="+spacing+" y="+from.y+" -> "+to.y);
            }
            e.setCaretPosition(3);e.getCaret().setMagicCaretPosition(null);action(e,"selection-down");
            check(e.getSelectionEnd()>e.getSelectionStart(),"Shift+Down did not select: "+wrap+"/"+font+"/"+spacing);
            action(e,"selection-up");check(e.getCaretPosition()==3,"Shift+Up did not return to start: "+wrap+"/"+font+"/"+spacing);
            check(e.getText().equals(source),"Navigation changed SQL");
        }
        System.out.println("KEYBOARD cases=24 failures="+failures.size());
    }
    static void benchmark(Path output)throws Exception{
        StringBuilder csv=new StringBuilder("lines,wrap,spacing,coordinate_ms,paint_ms,edit_ms\n");
        for(int count:new int[]{100,1000})for(boolean wrap:new boolean[]{false,true}){
            String sql="select '한글 값', abcdefghijklmnopqrstuvwxyz, 1234567890 from dual;\n".repeat(count);
            BasicEditorPane e=editor(sql,24,760,wrap,30,17);e.modelToView(5);paint(e);
            long start=System.nanoTime();for(int i=0;i<20;i++){Rectangle r=e.modelToView(5);e.viewToModel(new Point(r.x,r.y+2));}double coords=(System.nanoTime()-start)/1e6;
            start=System.nanoTime();for(int i=0;i<3;i++)paint(e);double painting=(System.nanoTime()-start)/1e6;
            start=System.nanoTime();for(int i=0;i<3;i++){e.getDocument().insertString(7,"x",null);e.modelToView(8);paint(e);e.getDocument().remove(7,1);}double edits=(System.nanoTime()-start)/1e6;
            csv.append(String.format(Locale.ROOT,"%d,%s,30,%.3f,%.3f,%.3f%n",count,wrap,coords,painting,edits));
            System.out.printf(Locale.ROOT,"PERF lines=%d wrap=%s coordinates20=%.2fms paints3=%.2fms edits3=%.2fms%n",count,wrap,coords,painting,edits);
            check(coords<1000,"Stable coordinates blocked EDT for "+coords+"ms / "+count+" lines");
        }
        Files.writeString(output,csv);
    }
    static void stress()throws Exception{
        StringBuilder source=new StringBuilder();for(int i=0;i<10000;i++)source.append("select '한글 값', column_").append(i).append(", '\uD83D\uDE00 abcdefghijklmnopqrstuvwxyz' from dual;\n");
        BasicEditorPane e=editor(source.toString(),24,760,true,30,17);String exported=e.getText(),raw=e.getDocument().getText(0,e.getDocument().getLength());e.modelToView(5);paint(e);
        long start=System.nanoTime();for(int i=0;i<100;i++){Rectangle r=e.modelToView(5);e.viewToModel(new Point(r.x,r.y+2));e.getPreferredSize();}
        double stable=(System.nanoTime()-start)/1e6;
        check(stable<1000,"10,000-line warm navigation/layout exceeded 1s: "+stable);
        start=System.nanoTime();for(int i=0;i<3;i++){e.getDocument().insertString(7,"x",null);e.modelToView(8);paint(e);e.getDocument().remove(7,1);}double edits=(System.nanoTime()-start)/1e6;
        check(e.getText().equals(exported)&&e.getDocument().getText(0,e.getDocument().getLength()).equals(raw),"Incremental editing corrupted SQL");
        System.out.printf(Locale.ROOT,"STRESS unique_lines=10000 coordinates100_and_spans=%.2fms edits3=%.2fms%n",stable,edits);
        e.setCaretPosition(3);action(e,DefaultEditorKit.pageDownAction);check(e.modelToView(e.getCaretPosition()).y>3,"Page Down did not move");
        action(e,DefaultEditorKit.pageUpAction);check(e.modelToView(e.getCaretPosition()).y<460,"Page Up did not return");
        e=editor("---\n".repeat(10000),24,760,true,30,0);paint(e);start=System.nanoTime();for(int i=0;i<5;i++)paint(e);double markers=(System.nanoTime()-start)/1e6;
        check(markers<1000,"Off-screen markers made repaint exceed 1s: "+markers);
        System.out.printf(Locale.ROOT,"STRESS marker_lines=10000 paints5=%.2fms%n",markers);
    }
    public static void main(String[] args)throws Exception{
        try{SwingUtilities.invokeAndWait(()->{try{navigation();benchmark(Path.of(args[0]));if(args.length<2||!args[1].equals("baseline"))stress();}catch(Exception error){throw new RuntimeException(error);}});
            for(String failure:failures)System.out.println("FAIL "+failure);
            if(!failures.isEmpty()&&(args.length<2||!args[1].equals("baseline")))throw new AssertionError("Responsiveness failures: "+failures.size());
            System.out.println(failures.isEmpty()?"PASS native keyboard and responsiveness":"BASELINE reproduced failures="+failures.size());System.exit(0);
        }catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
}
