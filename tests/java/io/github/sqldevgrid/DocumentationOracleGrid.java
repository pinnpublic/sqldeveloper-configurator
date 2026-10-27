package io.github.sqldevgrid;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.PreparedStatement;
import javax.swing.*;
import javax.imageio.ImageIO;
import oracle.dbtools.raptor.controls.grid.*;

/** All rows below are synthetic documentation data. Database access deliberately throws. */
public final class DocumentationOracleGrid {
    static final class Model extends ResultSetTableModel {
        final String[] names={"ID","NAME","SAMPLE"};
        final String[][] rows={{"row_01","가나다","Agjpqy 0123"},{"row_02","라마바","한글 ABC 456"},{"row_03","사아자","한글 789 xyz"}};
        Model(){gridColumns=new java.util.ArrayList<>();for(String name:names)gridColumns.add(new ResultSetGridColumn(name,false,java.sql.Types.VARCHAR));}
        protected PreparedStatement prepareStatement(){throw new UnsupportedOperationException("Documentation never accesses a database");}
        public int getColumnCount(){return 3;}public int getRowCount(){return 3;}public int getLoadedRowCount(){return 3;}
        public Object getValueAt(int row,int col){return rows[row][col];}public String getColumnName(int col){return new String[]{"ID","NAME","SAMPLE"}[col];}
        public Class<?> getColumnClass(int col){return String.class;}
        public oracle.dbtools.raptor.query.Column getQueryColumn(int row,int col){return null;}
        public String getDisplayValueAt(int row,int col,int flags){return rows[row][col];}
        public boolean isCellEditorEditable(int row,int col){return false;}public boolean isSortable(){return false;}
        public String getModifiedSql(){return "";}public boolean isSkipColumn(int col){return false;}public boolean isHiddenColumn(int col){return false;}
        public boolean isColumnSortable(int col){return false;}public Color getRowHighlightColor(int row){return null;}
    }
    public static void render(String output)throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            ResultSetTable table=new ResultSetTable();table.setModel(new Model());table.createDefaultColumnsFromModel();table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            ClassLoader.getSystemClassLoader().loadClass("io.github.sqldevgrid.BootstrapAgent").getMethod("styleTableForTest",JTable.class,int.class,int.class).invoke(null,table,50,8);
            int[] widths={270,280,690};int width=0;for(int col=0;col<widths.length;col++){table.getColumnModel().getColumn(col).setWidth(widths[col]);table.getColumnModel().getColumn(col).setPreferredWidth(widths[col]);width+=widths[col];}
            int rows=table.getRowHeight()*3;table.setSize(width,rows);table.doLayout();table.changeSelection(1,1,false,false);
            int header=Math.max(25,table.getTableHeader().getPreferredSize().height);table.getTableHeader().setSize(width,header);
            JTable numbers=table.getRowHeader();numbers.setRowHeight(table.getRowHeight());numbers.setSize(48,rows);
            BufferedImage image=new BufferedImage(width+48,rows+header,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());
            Graphics2D h=(Graphics2D)g.create(48,0,width,header);table.getTableHeader().paint(h);h.dispose();
            Graphics2D b=(Graphics2D)g.create(48,header,width,rows);table.paint(b);b.dispose();
            Graphics2D n=(Graphics2D)g.create(0,header,48,rows);numbers.paint(n);n.dispose();g.dispose();ImageIO.write(image,"png",new java.io.File(output));
            if(table.getFont().getSize()!=50)throw new AssertionError("Documented result table font is not 50pt");
        }catch(Exception error){throw new RuntimeException(error);}});
    }
}
