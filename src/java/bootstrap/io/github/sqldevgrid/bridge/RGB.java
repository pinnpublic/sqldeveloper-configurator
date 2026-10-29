package io.github.sqldevgrid.bridge;
/** Small immutable color value; independent of SWT. */
public final class RGB {
 public final int red,green,blue;
 public RGB(int r,int g,int b){red=r;green=g;blue=b;}
 public static RGB decode(String s){java.awt.Color c=java.awt.Color.decode(s);return new RGB(c.getRed(),c.getGreen(),c.getBlue());}
 public String hex(){return String.format("#%02X%02X%02X",red,green,blue);}
 public boolean equals(Object o){return o instanceof RGB c&&red==c.red&&green==c.green&&blue==c.blue;}
 public int hashCode(){return red<<16|green<<8|blue;}
 public String toString(){return hex();}
}
