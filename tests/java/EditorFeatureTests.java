import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.*;
import oracle.javatools.editor.BasicEditorPane;
import io.github.sqldevgrid.bridge.EditorBridge;

public final class EditorFeatureTests {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void size(BasicEditorPane editor, JScrollPane scroll, int width) {
        scroll.setSize(width, 700); scroll.doLayout();
        scroll.getViewport().setExtentSize(new Dimension(width - 25, 650));
        editor.setSize(width - 25, 3000);
        editor.getUI().getRootView(editor).setSize(width - 25, 3000);
    }
    static BufferedImage paint(BasicEditorPane editor, int width, int height) {
        editor.setSize(width, height); editor.getUI().getRootView(editor).setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
        editor.paint(graphics); graphics.dispose(); return image;
    }
    public static void main(String[] args) throws Exception {
        System.out.println("VM " + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println("Agent source " + Class.forName("io.github.sqldevgrid.BootstrapAgent").getProtectionDomain().getCodeSource());
        System.out.println("Bootstrap error " + System.getProperty("sqldev.font50.bootstrapError"));
        try {
        SwingUtilities.invokeAndWait(() -> {
            try {
                BasicEditorPane editor = new BasicEditorPane(); editor.setBaseFont("Monospaced", 20);
                JScrollPane scroll = new JScrollPane(editor);
                String sql = "select very_long_column_name, other_column_name, '한글 값 abcdefghijklmnopqrstuvwxyz 0123456789' from very_long_table_name where id = 12345;\n---\nselect\t'다음 줄' from dual;\n";
                editor.setText(sql); size(editor, scroll, 420);
                String exported = editor.getText();
                String source = editor.getDocument().getText(0, editor.getDocument().getLength());
                EditorBridge.configureEditor(editor, true, true, 2f, 0xBB2244, "solid");
                Rectangle first = editor.modelToView(0), later = editor.modelToView(95);
                System.out.println("DEBUG transform=" + System.getProperty("sqldev.font50.viewTransformed") + " bridgeLoader=" + EditorBridge.class.getClassLoader()
                    + " first=" + first + " later=" + later + " root=" + editor.getUI().getRootView(editor).getView(0).getClass().getName()
                    + " active=" + EditorBridge.active(editor.getUI().getRootView(editor).getView(0), false) + " parent=" + editor.getParent().getSize());
                check(later.y > first.y, "Long SQL did not wrap");
                int nativeLines = editor.getLineCount();
                check(nativeLines == 4, "Physical SQL line count changed");
                check(editor.getRowForLine(1) > 1, "Line-number row mapping did not include wrapped rows");
                check(editor.getLineFromRow(editor.getRowForLine(1)) == 1, "Line-number mapping lost the next SQL line");
                int rows = editor.getRowForLine(nativeLines);
                check(editor.getLineFromRow(rows+3) == nativeLines+3, "Gutter repeats the last line below the document");
                for (int offset = 0; offset < sql.length(); offset++) {
                    if (sql.charAt(offset) == '\n' || sql.charAt(offset) == '\t') continue;
                    Rectangle cell = editor.modelToView(offset);
                    check(cell.x >= 0 && cell.x <= 420, "Wrapped character outside viewport at " + offset + " " + cell);
                    int roundTrip = editor.viewToModel(new Point(cell.x, cell.y + cell.height / 2));
                    check(roundTrip == offset, "Caret round trip failed at " + offset + " -> " + roundTrip);
                }
                int narrowHeight = editor.getPreferredSize().height;
                BufferedImage wrapped = paint(editor, 395, Math.max(400, narrowHeight));
                ImageIO.write(wrapped, "png", Path.of(args[0], "editor-wrap.png").toFile());
                check(editor.getText().equals(exported) && editor.getDocument().getText(0, editor.getDocument().getLength()).equals(source), "Painting changed SQL text");
                size(editor, scroll, 760);
                check(editor.getPreferredSize().height < narrowHeight, "Wider viewport did not reduce wrapped rows");
                editor.setCaretPosition(5);
                int before = editor.getCaretPosition();
                javax.swing.Action down = ((oracle.javatools.editor.BasicEditorKit)editor.getEditorKit()).findAction("caret-down");
                check(down != null, "Native Down action missing");
                down.actionPerformed(new java.awt.event.ActionEvent(editor, 0, ""));
                check(editor.getCaretPosition() > before, "Native Down action did not move across visual rows");
                EditorBridge.configureEditor(editor, false, false, 1, 0, "solid");
                check(editor.modelToView(95).y == editor.modelToView(0).y, "Disabling wrap did not restore native coordinates");
                check(editor.getText().equals(exported) && editor.getDocument().getText(0, editor.getDocument().getLength()).equals(source), "Feature toggles changed SQL");
                System.out.println("PASS native Oracle editor: wrap, resizing, caret round trips, Down action, line mapping, toggle off, unchanged SQL");

                EditorBridge.configureEditor(editor, true, false, 1, 0, "solid");
                size(editor, scroll, 420);
                editor.select(5, 105); editor.getCaret().setSelectionVisible(true);
                check(editor.getSelectedText().equals(source.substring(5,105)), "Wrapped selection copied wrong text");
                ImageIO.write(paint(editor,395,400), "png", Path.of(args[0], "editor-wrap-selection.png").toFile());
                editor.select(0,0);
                editor.getDocument().insertString(10, "추가_text ", null);
                check(editor.getDocument().getText(0, editor.getDocument().getLength()).equals(source.substring(0,10)+"추가_text "+source.substring(10)), "Wrapped edit changed another location");
                Rectangle edited = editor.modelToView(110);
                check(editor.viewToModel(new Point(edited.x,edited.y+edited.height/2)) == 110, "Edit did not refresh wrap positions");
                editor.getDocument().remove(10,8);
                check(editor.getDocument().getText(0, editor.getDocument().getLength()).equals(source), "Wrapped removal changed another location");
                editor.setBaseFont("Monospaced",50); size(editor,scroll,420);
                check(editor.modelToView(95).y > editor.modelToView(0).y, "50pt did not wrap");
                ImageIO.write(paint(editor,395,1000),"png",Path.of(args[0],"editor-wrap-50pt.png").toFile());
                editor.setBaseFont("Monospaced",20);
                System.out.println("PASS wrapped selection, copy text, insert/remove, refresh after edit, 50pt layout");

                String markers = "---\nselect '---' from dual;\n'quoted\n---\nvalue'\nq'[text\n---\n]'\n  ---  \n/*\n---\n*/\n";
                check(EditorBridge.ruleLines(markers).length == 2, "Rule appeared inside a quoted string/block comment");
                check(EditorBridge.ruleLines("안녕하세요. --- 입니다\n\"안녕하세요. --- 입니다\"\n--- 뒤에 글자\n앞에 글자 ---\n----\n").length == 0,
                    "Rule appeared in a line containing other text");
                editor.setText("---\nselect 1 from dual;\n---\n"); size(editor, scroll, 620);
                String ruleSource = editor.getText();
                java.util.Set<Integer> patterns=new java.util.HashSet<>();
                for (String style : new String[] {"solid", "dashed", "dotted", "dashdot", "longdash", "shortdash", "dashdotdot", "longdashdot", "double"}) {
                    EditorBridge.configureEditor(editor, false, true, 4, 0xEE2244, style);
                    BufferedImage image = paint(editor, 595, 220);
                    int colored = 0;
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 150; x < image.getWidth() - 20; x++)
                        if ((image.getRGB(x, y) & 0xFFFFFF) == 0xEE2244) colored++;
                    check(colored > 100, "Configured " + style + " rule color/stroke was not painted");
                    check(patterns.add(Arrays.hashCode(image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()))),"Rule styles look identical: "+style);
                    ImageIO.write(image, "png", Path.of(args[0], "editor-rule-" + style + ".png").toFile());
                }
                check(editor.getText().equals(ruleSource), "Rules changed SQL source");
                editor.setBaseFont("Monospaced",50);editor.setText("\n\n---\n\n\n");size(editor,scroll,620);
                String thickSource=editor.getText();
                editor.getCaret().setVisible(false);
                for(boolean wrapMode:new boolean[]{false,true}) for(float thickness:new float[]{1,8,25,50}) {
                    // Use the global settings path as the actual preferences service does.
                    editor.putClientProperty("sqldev.font50.editorFeatures",null);
                    EditorBridge.configure(wrapMode,true,thickness,0xEE2244,"solid");EditorBridge.invalidate(editor);
                    BufferedImage image=paint(editor,595,400);
                    int top=400,bottom=-1;
                    for(int y=0;y<400;y++) if((image.getRGB(200,y)&0xFFFFFF)==0xEE2244){top=Math.min(top,y);bottom=y;}
                    check(Math.abs((bottom-top+1)-thickness)<=1,"Actual stroke differs from "+thickness+"px (wrap="+wrapMode+"): "+(bottom-top+1));
                    if(thickness==50) {
                        BufferedImage clipped=new BufferedImage(595,400,BufferedImage.TYPE_INT_RGB);
                        Graphics2D clipGraphics=clipped.createGraphics();clipGraphics.setClip(180,bottom-4,40,3);editor.paint(clipGraphics);clipGraphics.dispose();
                        check(clipped.getRGB(200,bottom-3)==image.getRGB(200,bottom-3),"Partial repaint missed outer edge of 50px rule");
                        ImageIO.write(image,"png",Path.of(args[0],"editor-rule-50px-"+wrapMode+".png").toFile());
                    }
                }
                check(editor.getText().equals(thickSource),"Thick rules changed SQL source");
                System.out.println("PASS 9 distinct styles; global 1/8/25/50px actual stroke and clipped repaint with/without wrap");
                editor.setBaseFont("Monospaced",50); editor.setText("---"); editor.select(0,0); editor.getCaret().setVisible(false); size(editor,scroll,620);
                for(boolean wrapMode : new boolean[] {false,true}) {
                    EditorBridge.configureEditor(editor,wrapMode,true,1,0xEE2244,"solid");
                    BufferedImage image=paint(editor,595,140); int stray=0;
                    for(int y=0;y<100;y++) for(int x=3;x<220;x++) {
                        int rgb=image.getRGB(x,y)&0xFFFFFF;
                        // Native marker glyphs are grey/black; only the red line should remain.
                        if(rgb!=0xFFFFFF && ((rgb>>16)&255) < ((rgb>>8)&255)+20) stray++;
                    }
                    check(stray==0,"Original --- glyphs remain visible (wrap="+wrapMode+"): "+stray);
                    ImageIO.write(image,"png",Path.of(args[0],"rule-no-marker-"+wrapMode+".png").toFile());
                }
                EditorBridge.configureEditor(editor,false,false,1,0,"solid");
                BufferedImage nativeMarker=paint(editor,595,140); int ink=0;
                for(int y=0;y<100;y++)for(int x=3;x<220;x++)if((nativeMarker.getRGB(x,y)&0xFFFFFF)!=0xFFFFFF)ink++;
                check(ink>20,"Disabling rules did not restore original --- glyphs");
                editor.select(0,3);editor.getCaret().setSelectionVisible(true);
                BufferedImage selectedNative=paint(editor,595,140);
                EditorBridge.configureEditor(editor,false,true,1,0xEE2244,"solid");
                BufferedImage selectedRule=paint(editor,595,140);
                check(selectedNative.getRGB(5,5)==selectedRule.getRGB(5,5) && (selectedRule.getRGB(5,5)&0xFFFFFF)!=0xFFFFFF,
                    "Hiding marker glyphs erased native selection background");
                check(editor.getSelectedText().equals("---"),"Hidden marker selection changed copied text");
                editor.select(0,0);
                editor.getDocument().remove(2,1);
                BufferedImage removed=paint(editor,595,140); int red=0;
                for(int y=0;y<100;y++)for(int x=220;x<590;x++)if((removed.getRGB(x,y)&0xFFFFFF)==0xEE2244)red++;
                check(red==0 && editor.getText().equals("--"),"Deleting a hyphen did not remove the rule");
                System.out.println("PASS marker glyphs hidden with/without wrap; toggling rules restores text; deleting a hyphen removes rule");
                System.out.println("PASS horizontal rules: 9 styles, color, thickness, quoted-marker exclusion, unchanged SQL");
                editor.dispose();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
}
