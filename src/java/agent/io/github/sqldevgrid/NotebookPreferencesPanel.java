package io.github.sqldevgrid;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.event.*;
import oracle.ide.panels.*;
import io.github.sqldevgrid.bridge.*;

/** Native Preferences page. Navigation stages values; only Apply/OK persists them. */
public final class NotebookPreferencesPanel extends DefaultTraversablePanel implements ApplyListener {
    private static final String KEY="io.github.sqldevgrid.paperOptions";
    private final JCheckBox enabled=check("소스 편집기에 노트 배경 표시","PaperEnabled");
    private final JCheckBox lines=check("모든 글줄 아래에 선 표시","PaperLines");
    private final JCheckBox theme=check("편집기 테마의 배경색 사용","PaperTheme");
    private final JComboBox<String> paper=combo(NotebookSettings.PAPER_NAMES,"PaperStyle");
    private final JComboBox<String> texture=combo(NotebookSettings.TEXTURE_NAMES,"PaperTexture");
    private final JComboBox<String> style=combo(NotebookSettings.LINE_NAMES,"PaperLineStyle");
    private final Map<String,JSpinner> numbers=new LinkedHashMap<>();
    private final Map<String,JTextField> colors=new LinkedHashMap<>();
    private String pending;
    private boolean loading;
    private final JPanel preview=new JPanel() {
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            try {
                NotebookSettings s=collect();
                int h=23,row=h+Math.round(h*s.spacing/100f);
                double shift=h+h*s.gap/100.0+Math.max(1,h*s.width/100.0)/2;
                double[] rules=new double[Math.max(1,getHeight()/row+2)];
                for(int i=0;i<rules.length;i++)rules[i]=12+i*row+shift;
                PaperRenderer.Layout layout=new PaperRenderer.Layout(getWidth(),getHeight(),h,row,0,12+shift,rules).withGeometry(85,170,null);
                RGB bg=s.theme?new RGB(250,250,250):s.paperColor;
                graphics.drawImage(PaperRenderer.render(s,layout,bg,100),0,0,null);
                graphics.setColor(new Color(0x334155));graphics.setFont(new Font("Dialog",Font.PLAIN,17));
                graphics.drawString("select '노트 배경' from dual;",16,12+h-5);
                graphics.drawString("-- SQL 원문은 그대로 유지됩니다.",16,12+row+h-5);
            } catch(TraversalException ignored) { }
        }
    };
    private static JCheckBox check(String label,String name){JCheckBox c=new JCheckBox(label);c.setName(name);return c;}
    private static JComboBox<String> combo(String[] labels,String name){JComboBox<String> c=new JComboBox<>(labels);c.setName(name);return c;}
    public NotebookPreferencesPanel() {
        setDefaultTitle("노트 배경");setLayout(new BorderLayout(0,8));setBorder(BorderFactory.createEmptyBorder(10,12,10,12));
        add(enabled,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.setName("PaperTabs");
        JPanel lineForm=form(),paperForm=form();
        row(lineForm,lines,null);row(lineForm,new JLabel("선 종류"),style);
        number(lineForm,"두께 — 글자 높이의 %","width",1,25);
        number(lineForm,"텍스트와 선 간격 (%)","gap",-50,100);
        number(lineForm,"추가 줄 간격 (%)","spacing",0,200);
        color(lineForm,"선 색상","lineColor");
        number(lineForm,"선 진하기 (%)","lineOpacity",1,100);
        number(lineForm,"현재 줄 강조 불투명도 (%)","highlightOpacity",0,100);
        row(paperForm,new JLabel("배경 종류"),paper);row(paperForm,theme,null);
        color(paperForm,"종이색 — 라이트","paperColor");color(paperForm,"종이색 — 다크","darkPaperColor");
        color(paperForm,"무늬 색상","patternColor");
        number(paperForm,"무늬 크기 (%)","scale",50,300);
        number(paperForm,"무늬 진하기 (%)","patternOpacity",1,100);
        row(paperForm,new JLabel("종이 질감"),texture);
        number(paperForm,"질감 진하기 (%)","textureOpacity",0,100);
        tabs.addTab("선",new JScrollPane(lineForm));tabs.addTab("배경",new JScrollPane(paperForm));
        add(tabs,BorderLayout.CENTER);
        JPanel lower=new JPanel(new BorderLayout(0,5));preview.setName("PaperPreview");preview.setPreferredSize(new Dimension(450,145));
        lower.add(preview,BorderLayout.CENTER);lower.add(new JLabel("적용하면 열린 편집기에 반영됩니다. --- 구분선은 별도 설정입니다."),BorderLayout.SOUTH);
        add(lower,BorderLayout.SOUTH);
        for(JCheckBox c:new JCheckBox[]{enabled,lines,theme})c.addActionListener(e->preview.repaint());
        paper.addActionListener(e->preview.repaint());style.addActionListener(e->preview.repaint());
        texture.addActionListener(e->{
            if(!loading&&texture.getSelectedIndex()>0){
                RGB light=NotebookSettings.TEXTURE_COLORS[texture.getSelectedIndex()];
                colors.get("paperColor").setText(light.hex());colors.get("darkPaperColor").setText(NotebookSettings.darkPaper(light).hex());
            }
            preview.repaint();
        });
        load(NotebookSettings.parse("{}"));
    }
    private static JPanel form(){JPanel p=new JPanel(new GridBagLayout());p.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));return p;}
    private static void row(JPanel p,JComponent label,JComponent control){
        int row=p.getClientProperty("rows") instanceof Integer n?n:0;p.putClientProperty("rows",row+1);
        GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(4,2,4,10);
        if(control==null){c.gridwidth=2;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;p.add(label,c);}
        else{p.add(label,c);c.gridx=1;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;p.add(control,c);}
    }
    private void number(JPanel p,String label,String key,int min,int max){
        JSpinner s=new JSpinner(new SpinnerNumberModel(min,min,max,1));s.setName("Paper"+Character.toUpperCase(key.charAt(0))+key.substring(1));
        numbers.put(key,s);s.addChangeListener(e->preview.repaint());row(p,new JLabel(label),s);
    }
    private void color(JPanel p,String label,String key){
        JTextField input=new JTextField(10);input.setName("Paper"+Character.toUpperCase(key.charAt(0))+key.substring(1));colors.put(key,input);
        JPanel field=new JPanel(new BorderLayout(5,0));field.add(input,BorderLayout.CENTER);JButton choose=new JButton("선택…");field.add(choose,BorderLayout.EAST);
        choose.addActionListener(e->{Color initial;try{initial=Color.decode(input.getText());}catch(Exception ignored){initial=Color.WHITE;}
            Color c=JColorChooser.showDialog(this,label,initial);if(c!=null)input.setText(String.format("#%06X",c.getRGB()&0xFFFFFF));});
        input.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){preview.repaint();}public void removeUpdate(DocumentEvent e){preview.repaint();}public void changedUpdate(DocumentEvent e){preview.repaint();}});
        row(p,new JLabel(label),field);
    }
    private void load(NotebookSettings s){
        loading=true;
        try{
            enabled.setSelected(s.enabled);lines.setSelected(s.lines);theme.setSelected(s.theme);
            paper.setSelectedIndex(s.paper);texture.setSelectedIndex(s.texture);style.setSelectedIndex(s.lineStyle);
            for(var e:numbers.entrySet())e.getValue().setValue(NotebookSettings.class.getField(e.getKey()).getInt(s));
            for(var e:colors.entrySet())e.getValue().setText(((RGB)NotebookSettings.class.getField(e.getKey()).get(s)).hex());
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}finally{loading=false;}
        preview.repaint();
    }
    private NotebookSettings collect() throws TraversalException {
        StringBuilder json=new StringBuilder("{\"enabled\":"+enabled.isSelected()+",\"lines\":"+lines.isSelected()+",\"theme\":"+theme.isSelected()
            +",\"paper\":"+paper.getSelectedIndex()+",\"texture\":"+texture.getSelectedIndex()+",\"lineStyle\":"+style.getSelectedIndex());
        for(var e:numbers.entrySet()){
            JSpinner s=e.getValue();try{s.commitEdit();}catch(java.text.ParseException error){throw new TraversalException("숫자 범위를 확인하세요: "+e.getKey());}
            int v=((Number)s.getValue()).intValue();SpinnerNumberModel m=(SpinnerNumberModel)s.getModel();
            if(v<((Number)m.getMinimum()).intValue()||v>((Number)m.getMaximum()).intValue())throw new TraversalException("숫자 범위를 확인하세요: "+e.getKey());
            json.append(",\"").append(e.getKey()).append("\":").append(v);
        }
        for(var e:colors.entrySet()){
            String hex=e.getValue().getText().trim().toUpperCase(java.util.Locale.ROOT);
            if(!hex.matches("#[0-9A-F]{6}"))throw new TraversalException("색상은 #RRGGBB로 입력하세요.");
            json.append(",\"").append(e.getKey()).append("\":\"").append(hex).append('"');
        }
        return NotebookSettings.parse(json.append('}').toString());
    }
    private static Path path(){return GridSettings.path().resolveSibling("notebook-settings.json");}
    private static String read(){try{return Files.readString(path(),StandardCharsets.UTF_8);}catch(java.io.IOException ignored){return "{}";}}
    public void onEntry(TraversableContext context){Object staged=context.getDesignTimeObject(KEY);load(NotebookSettings.parse(staged instanceof String s?s:pending!=null?pending:read()));}
    public void onExit(TraversableContext context)throws TraversalException{pending=collect().toJson();context.putDesignTimeObject(KEY,pending);}
    public void apply(ApplyEvent event){
        try{
            Object staged=event.getTraversableContext().getDesignTimeObject(KEY);String json=staged instanceof String s?s:pending!=null?pending:collect().toJson();
            Files.createDirectories(path().getParent());Path tmp=Files.createTempFile(path().getParent(),"notebook-",".tmp");
            try{Files.writeString(tmp,json,StandardCharsets.UTF_8);try{Files.move(tmp,path(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(tmp,path(),StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
            ClassLoader.getSystemClassLoader().loadClass("io.github.sqldevgrid.EditorFeatureService").getMethod("reloadNow").invoke(null);pending=null;
        }catch(Exception error){throw new IllegalStateException("노트 배경 설정을 저장하지 못했습니다.",error);}
    }
    public void cancel(ApplyEvent event){pending=null;event.getTraversableContext().putDesignTimeObject(KEY,null);}
}
