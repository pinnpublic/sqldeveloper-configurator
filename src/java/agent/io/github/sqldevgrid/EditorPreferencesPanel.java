package io.github.sqldevgrid;

import java.awt.*;
import java.awt.geom.Line2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;
import oracle.ide.panels.*;

/** SQL Developer's native Preferences page; changes commit on OK/Apply, never on navigation. */
public final class EditorPreferencesPanel extends DefaultTraversablePanel implements ApplyListener {
    private static final String KEY = "io.github.sqldevgrid.editorOptions";
    private static final String[] STYLES = {"solid", "dashed", "dotted", "dashdot", "longdash", "shortdash", "dashdotdot", "longdashdot", "double"};
    private String pending;
    private final JCheckBox wrap = new JCheckBox("자동 줄바꿈 사용 (화면 너비에 맞춰 표시)");
    private final JCheckBox rules = new JCheckBox("---만 있는 줄에 수평선 표시");
    private final JSpinner thickness = new JSpinner(new SpinnerNumberModel(1.0, 1.0, 50.0, 1.0));
    private final JComboBox<String> style = new JComboBox<>(new String[] {"실선", "파선", "점선", "일점쇄선", "긴 파선", "짧은 파선", "이점쇄선", "긴 일점쇄선", "이중선"});
    private final JTextField color = new JTextField("#6F8296", 10);
    private final JPanel preview = new JPanel() {
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D copy = (Graphics2D)graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float width = ((Number)thickness.getValue()).floatValue(), scale = Math.max(1,width);
                float[] dash = switch(style.getSelectedIndex()) {
                    case 1 -> new float[] {8*scale,5*scale}; case 2 -> new float[] {scale,4*scale};
                    case 3 -> new float[] {8*scale,4*scale,scale,4*scale};
                    case 4 -> new float[] {14*scale,5*scale};
                    case 5 -> new float[] {3*scale,3*scale};
                    case 6 -> new float[] {8*scale,4*scale,scale,4*scale,scale,4*scale};
                    case 7 -> new float[] {14*scale,4*scale,scale,4*scale};
                    default -> null;
                };
                try { copy.setColor(Color.decode(color.getText().trim())); } catch(NumberFormatException ignored) { copy.setColor(new Color(0x6F8296)); }
                boolean doubled = style.getSelectedIndex()==8;
                copy.setStroke(new BasicStroke(doubled ? width/3 : width, style.getSelectedIndex()==2 ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,10,dash,0));
                float y=getHeight()/2f;
                if(doubled) {
                    copy.draw(new Line2D.Float(10,y-width/3,getWidth()-10,y-width/3));
                    copy.draw(new Line2D.Float(10,y+width/3,getWidth()-10,y+width/3));
                } else copy.draw(new Line2D.Float(10,y,getWidth()-10,y));
            } finally {copy.dispose();}
        }
    };
    public EditorPreferencesPanel() {
        setDefaultTitle("자동 줄바꿈 · 수평선"); setLayout(new BorderLayout());
        JPanel form = new JPanel(new GridBagLayout()); form.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        wrap.setName("SoftWrap"); rules.setName("HorizontalRules"); thickness.setName("RuleThickness");
        style.setName("RuleStyle"); color.setName("RuleColor");
        GridBagConstraints c = new GridBagConstraints(); c.insets=new Insets(5,0,8,10); c.anchor=GridBagConstraints.WEST;
        c.gridx=0; c.gridy=0; c.gridwidth=3; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL; form.add(wrap,c);
        c.gridy++; form.add(new JLabel("SQL 원문에 실제 개행을 추가하지 않습니다."),c);
        c.gridy++; form.add(rules,c);
        c.gridy++; form.add(new JLabel("다른 글자가 있는 줄, 문자열 및 블록 주석 안에는 표시하지 않습니다."),c);
        c.gridy++; c.gridwidth=1; c.weightx=0; form.add(new JLabel("선 두께 (1~50px)"),c); c.gridx=1; c.gridwidth=2; form.add(thickness,c);
        c.gridy++; c.gridx=0; c.gridwidth=1; form.add(new JLabel("선 모양"),c); c.gridx=1; c.gridwidth=2; form.add(style,c);
        c.gridy++; c.gridx=0; c.gridwidth=1; form.add(new JLabel("선 색상"),c); c.gridx=1; c.weightx=1; form.add(color,c);
        JButton choose = new JButton("색상 선택…"); c.gridx=2; c.weightx=0; form.add(choose,c);
        c.gridy++; c.gridx=0; c.gridwidth=3; c.weightx=1; preview.setName("RulePreview"); preview.setPreferredSize(new Dimension(360,80)); form.add(preview,c);
        c.gridy++; form.add(new JLabel("적용하면 열린 편집기에 바로 반영됩니다."),c);
        c.gridy++; c.weighty=1; c.fill=GridBagConstraints.BOTH; form.add(new JPanel(),c);
        add(form,BorderLayout.CENTER);
        choose.addActionListener(event -> {
            Color initial; try {initial=Color.decode(color.getText().trim());}catch(NumberFormatException error){initial=new Color(0x6F8296);}
            Color chosen=JColorChooser.showDialog(this,"수평선 색상",initial);
            if(chosen!=null) color.setText(String.format("#%06X",chosen.getRGB()&0xFFFFFF));
        });
        thickness.addChangeListener(event -> preview.repaint()); style.addActionListener(event -> preview.repaint());
        color.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e){preview.repaint();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){preview.repaint();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){preview.repaint();}
        });
    }
    static Path path() {return GridSettings.path().resolveSibling("editor-settings.json");}
    private static String read() {try{return Files.readString(path(),StandardCharsets.UTF_8);}catch(java.io.IOException error){return "{}";}}
    private void load(String json) {
        wrap.setSelected(Boolean.parseBoolean(GridSettings.value(json,"softWrap","false")));
        rules.setSelected(Boolean.parseBoolean(GridSettings.value(json,"horizontalRules","false")));
        double value;try{value=Double.parseDouble(GridSettings.value(json,"ruleThickness","1"));}catch(NumberFormatException error){value=1;}
        thickness.setValue(Double.isFinite(value) ? Math.max(1,Math.min(50,value)) : 1.0);
        color.setText(GridSettings.value(json,"ruleColor","\"#6F8296\"").replace("\"",""));
        String chosen=GridSettings.value(json,"ruleStyle","\"solid\"").replace("\"","");
        int index=java.util.Arrays.asList(STYLES).indexOf(chosen); style.setSelectedIndex(Math.max(0,index));
    }
    private String collect() throws TraversalException {
        try{thickness.commitEdit();}catch(java.text.ParseException error){throw new TraversalException("선 두께는 1~50px로 입력하세요.");}
        String hex=color.getText().trim().toUpperCase(java.util.Locale.ROOT);
        if(!hex.matches("#[0-9A-F]{6}")) throw new TraversalException("선 색상은 #RRGGBB로 입력하세요.");
        double value=((Number)thickness.getValue()).doubleValue();
        if(!Double.isFinite(value)||value<1||value>50) throw new TraversalException("선 두께는 1~50px로 입력하세요.");
        return "{\"softWrap\":"+wrap.isSelected()+",\"horizontalRules\":"+rules.isSelected()+",\"ruleThickness\":"+value
            +",\"ruleColor\":\""+hex+"\",\"ruleStyle\":\""+STYLES[Math.max(0,style.getSelectedIndex())]+"\"}";
    }
    public void onEntry(TraversableContext context) {
        Object staged=context.getDesignTimeObject(KEY); load(staged instanceof String ? (String)staged : pending != null ? pending : read());
    }
    public void onExit(TraversableContext context) throws TraversalException {pending=collect();context.putDesignTimeObject(KEY,pending);}
    public void apply(ApplyEvent event) {
        try {
            Object staged=event.getTraversableContext().getDesignTimeObject(KEY);
            String json=staged instanceof String ? (String)staged : pending != null ? pending : collect();
            Files.createDirectories(path().getParent()); Path temp=Files.createTempFile(path().getParent(),"editor-options-",".tmp");
            try {
                Files.writeString(temp,json,StandardCharsets.UTF_8);
                try{Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException error){Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(temp);}
            ClassLoader.getSystemClassLoader().loadClass("io.github.sqldevgrid.EditorFeatureService").getMethod("reloadNow").invoke(null);
            pending=null;
        } catch(Exception error){throw new IllegalStateException("편집기 표시 설정을 저장하지 못했습니다.",error);}
    }
    public void cancel(ApplyEvent event) {pending=null;event.getTraversableContext().putDesignTimeObject(KEY,null);}
}
