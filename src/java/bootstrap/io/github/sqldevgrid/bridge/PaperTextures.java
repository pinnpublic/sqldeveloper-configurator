package io.github.sqldevgrid.bridge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/** Seamless paper grain and fibres. Material scale stays independent of editor fonts and rules. */
final class PaperTextures {
    private static final int TILE = 384;
    private static PhotoMaterial crumpled, xuan;
    private static final Map<String, BufferedImage> CACHE = new LinkedHashMap<String, BufferedImage>(8, .75f, true) {
        private static final long serialVersionUID = 1L;
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> entry) {
            if (size() <= 4) return false;
            entry.getValue().flush(); return true;
        }
    };
    private PaperTextures() { }
    private static java.io.InputStream material(String name) throws java.io.IOException {
        java.io.InputStream input=PaperTextures.class.getResourceAsStream("/assets/paper/"+name);
        if(input!=null)return input;
        String helper=System.getProperty("sqldev.font50.bridgeJar");
        if(helper==null)return null;
        try(java.util.jar.JarFile jar=new java.util.jar.JarFile(helper)){
            java.util.jar.JarEntry entry=jar.getJarEntry("assets/paper/"+name);
            if(entry==null)return null;
            try(java.io.InputStream source=jar.getInputStream(entry)){return new java.io.ByteArrayInputStream(source.readAllBytes());}
        }
    }

    static synchronized void fill(Graphics2D graphics, NotebookSettings settings, RGB background,
            int zoom, int width, int height,int offsetX,int offsetY) {
        if (!settings.enabled || settings.texture == 0 || settings.textureOpacity == 0) {
            graphics.setColor(new Color(background.red, background.green, background.blue));
            graphics.fillRect(0, 0, width, height); return;
        }
        String key = settings.texture + ":" + settings.textureOpacity + ":" + background + ":" + zoom;
        BufferedImage tile = CACHE.get(key);
        if (tile == null) {
            tile = create(settings.texture, settings.textureOpacity, background, zoom);
            CACHE.put(key, tile);
        }
        graphics.setPaint(new TexturePaint(tile, new Rectangle((int)Math.round(offsetX*zoom/100.0), (int)Math.round(offsetY*zoom/100.0), tile.getWidth(), tile.getHeight())));
        graphics.fillRect(0, 0, width, height);
    }

    private static BufferedImage create(int type, int strength, RGB background, int zoom) {
        double z = zoom / 100.0, amount = strength / 50.0;
        int size = Math.max(1, (int) Math.round(TILE * z));
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        PhotoMaterial photo = null;
        if (type == 12 || type == 13 || type == 14 || type == 35) {
            if (crumpled == null) crumpled = new PhotoMaterial("crumpled.png");
            photo = crumpled;
        } else if (type >= 17 && type <= 19) {
            if (xuan == null) xuan = new PhotoMaterial("xuan.png");
            photo = xuan;
        }
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            double v = y / (double) size;
            for (int x = 0; x < size; x++) {
                double u = x / (double) size;
                double broad = noise(u, v, 6, 6, type * 29);
                double grain = noise(u, v, 64, 64, type * 31);
                double fine = noise(u, v, 256, 256, type * 37);
                double fibre = noise(u, v, 12, 192, type * 41);
                double delta;
                switch (type) {
                case 1: delta = broad * .7 + grain * .5 + fine * 1.1; break;
                case 2: delta = broad + grain * 1.1 + fine * 2.1; break;
                case 3: delta = broad * 2 + grain * 4.2 + fine * 2.4; break;
                case 4: delta = broad * 2.3 + grain * 3.2 + fine * 3 + fibre; break;
                case 5: delta = broad * 2.7 + grain * 2.7 + fine * 2 + fibre * 1.6; break;
                case 6: delta = broad * 1.5 + grain * 1.7 + fine * 1.5 + fibre * 1.7; break;
                case 7:
                    double tooth = noise(u, v, 100, 100, 709);
                    delta = broad * 2 + grain * 2.5 + fine + Math.copySign(tooth * tooth * 5, tooth);
                    break;
                case 8:
                    double warp = Math.sin(u * TILE * Math.PI / 2.4);
                    double weft = Math.sin(v * TILE * Math.PI / 2.4);
                    delta = broad + grain + fine + warp * weft * 2.8 + (warp + weft) * .7;
                    break;
                case 9: delta = broad * 9 + grain * 2 + fine * 1.3; break;
                case 10: delta = broad * 1.8 + grain * 2.5 + fine * 1.6; break;
                case 11: delta = broad * 1.6 + grain * 1.7 + fine * 2.8 + fibre; break;
                case 12: delta = broad * 1.7 + grain * 1.5 + fine * 1.2; break;
                case 13: delta = broad * 3.5 + grain * 2.4 + fine * 1.8; break;
                case 14: delta = broad * 2.8 + grain + fine; break;
                case 15: delta = fold(u,.5,2.6) * 13 + fold(v,.5,3.4) * 10 + broad + fine; break;
                case 16: delta = Math.sin(u*Math.PI*2*12)*5 + fold(u*12,.5,24)*11 + fine; break;
                case 17: delta = broad * 2.8 + grain * 1.4 + fine * 1.5 + fibre * .7; break;
                case 18: delta = broad * 3.8 + grain * 4 + fine * 2 + fibre * 2.7; break;
                case 19: delta = broad * 1.2 + grain * .7 + fine + fibre * .8; break;
                case 20: delta = broad * 3 + grain * 2.4 + fine * 2 + fibre * 3; break;
                case 21: delta = broad * 5 + grain * 1.4 + fine; break;
                case 22: delta = broad * 1.6 + noise(u,v,96,7,221)*3.8 + fine * 1.4; break;
                case 23: delta = broad * 2 + grain * 2.8 + fine * 1.3; break;
                case 24: delta = broad * 4.5 + grain * 3.5 + fine * 2; break;
                case 25: delta = broad * 5 + grain * 5 + fine * 2.8; break;
                case 26:
                    double flute = Math.cos(u*Math.PI*2*12);
                    delta = flute*7 - Math.pow((1-flute)*.5,12)*4 + fibre * 1.5 + fine;
                    break;
                case 27:
                    double px = u*48, py = v*48;
                    double dx = px-Math.floor(px)-.5, dy = py-Math.floor(py)-.5;
                    delta = (dx+dy)*Math.exp(-(dx*dx+dy*dy)*14)*24 + grain + fine;
                    break;
                case 28:
                    delta = Math.cos(v*Math.PI*2*128)*1.8 + Math.pow((1+Math.cos(u*Math.PI*2*8))*.5,30)*3 + fine;
                    break;
                case 29: delta = broad * 2.7 + noise(u,v,4,24,292)*1.3 + fine * .6; break;
                case 30: delta = broad * .5 + noise(u,v,2,48,303)*1.4 + fine * .7; break;
                case 31: delta = broad * 7 + grain * 1.7 - Math.pow(Math.max(0,-broad),2)*12 + fine; break;
                case 32:
                    double swirl = Math.sin(u*Math.PI*2*5 + noise(u,v,4,4,322)*8 + noise(u,v,12,12,323)*1.5);
                    delta = broad * 2 - Math.pow((1+swirl)*.5,15)*13 + grain * .7 + fine;
                    break;
                case 33: delta = broad * 2.7 + grain * 1.2 + fine * 2.7 + fibre * .8; break;
                case 34: delta = broad * 2.2 + grain * 4 + fine * 3.2; break;
                case 35: delta = broad * 3.2 + fine * .6; break;
                default: delta = 0;
                }
                if (photo != null) {
                    double detail = photo.sample(type == 14 ? 1-u : u, v);
                    double contrast = type==12?.55:type==13?1.25:type==14?.7:type==18?1.15:type==19?.35:type==35?.28:.8;
                    delta += detail * contrast;
                }
                delta *= amount;
                // Slightly warmer depressions imitate paper relief without hard black speckles.
                int red = channel(background.red + delta);
                int green = channel(background.green + delta * .96);
                int blue = channel(background.blue + delta * .86);
                pixels[y * size + x] = red << 16 | green << 8 | blue;
            }
        }
        image.setRGB(0, 0, size, size, pixels, 0, size);
        if (type == 4 || type == 5 || type == 6 || type == 10 || type == 11 || (type >= 17 && type <= 25))
            fibres(image, type, amount, z, background);
        if (type == 31) stains(image,amount,z);
        return image;
    }

    private static void fibres(BufferedImage image, int type, double amount, double zoom, RGB background) {
        Graphics2D g = image.createGraphics();
        try {
            g.scale(zoom, zoom);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Random random = new Random(472939L + type);
            int count = type == 6 ? 220 : type == 4 ? 140 : 95;
            if (type >= 17) count = new int[] {500,450,120,180,65,320,110,160,240}[type-17];
            for (int i = 0; i < count; i++) {
                double x = random.nextDouble() * TILE, y = random.nextDouble() * TILE;
                double length = type == 6 ? 8 + random.nextDouble() * 24 : 2 + random.nextDouble() * 10;
                if (type == 17 || type == 18 || type == 19) length = 12 + random.nextDouble()*45;
                if (type == 20 || type == 25) length = 8 + random.nextDouble()*38;
                if (type == 21) length = 55 + random.nextDouble()*105;
                if (type == 22) length = 12 + random.nextDouble()*45;
                if (type == 23 || type == 24) length = 5 + random.nextDouble()*30;
                double angle = random.nextDouble() * Math.PI * 2;
                if (type == 5) angle = Math.PI / 2 + (random.nextDouble() - .5) * .7;
                if (type == 11) angle = (random.nextDouble() - .5) * .8;
                if (type == 22) angle = Math.PI/2 + (random.nextDouble()-.5)*.5;
                double dx = Math.cos(angle) * length, dy = Math.sin(angle) * length;
                int alpha = Math.min(75, (int) ((9 + random.nextInt(15)) * amount));
                boolean light = random.nextBoolean();
                if (type == 21) { light = true; alpha = Math.min(95,(int)((25+random.nextInt(25))*amount)); }
                if (type == 19) alpha /= 2;
                if (type == 23 || type == 24) { light = false; alpha = Math.min(75,(int)((20+random.nextInt(20))*amount)); }
                g.setColor(light ? new Color(255, 255, 250, alpha)
                    : new Color(channel(background.red - 55), channel(background.green - 52), channel(background.blue - 46), alpha));
                double thickness = .18 + random.nextDouble()*.32;
                if (type == 20 || type == 25) thickness = .3 + random.nextDouble()*.7;
                if (type == 21) thickness = .5 + random.nextDouble()*1.2;
                if (type == 23 || type == 24) thickness = .6 + random.nextDouble()*1.5;
                g.setStroke(new BasicStroke((float) thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int tx = -1; tx <= 1; tx++) for (int ty = -1; ty <= 1; ty++) {
                    double ax = x + tx * TILE, ay = y + ty * TILE;
                    Path2D path = new Path2D.Double(); path.moveTo(ax, ay);
                    double curve = type == 21 ? .65 : .1;
                    path.quadTo(ax + dx * .5 + dy * curve, ay + dy * .5 - dx * curve, ax + dx, ay + dy);
                    g.draw(path);
                }
            }
        } finally { g.dispose(); }
    }

    /** Bundled photographic-style source, sampled at native DPI and tinted to the chosen paper color. */
    private static final class PhotoMaterial {
        final int width,height;
        final float[] luminance;
        final double mean;
        PhotoMaterial(String name) {
            try (java.io.InputStream input=material(name)) {
                if(input==null)throw new java.io.IOException("Missing bundled paper material: "+name);
                BufferedImage source=ImageIO.read(input);
                if(source==null)throw new java.io.IOException("Unreadable paper material: "+name);
                width=source.getWidth();height=source.getHeight();luminance=new float[width*height];
                int[] row=new int[width];double total=0;
                for(int y=0;y<height;y++) {
                    source.getRGB(0,y,width,1,row,0,width);
                    for(int x=0;x<width;x++) {
                        int p=row[x];float value=(float)(((p>>>16)&255)*.2126+((p>>>8)&255)*.7152+(p&255)*.0722);
                        luminance[y*width+x]=value;total+=value;
                    }
                }
                mean=total/luminance.length;source.flush();
            } catch(java.io.IOException error){throw new IllegalStateException("Cannot load paper material",error);}
        }
        double sample(double u,double v) {
            double wu=edgeWeight(u),wv=edgeWeight(v),a=at(u,v);
            if(wu>0)a=a*(1-wu)+at(1-u,v)*wu;
            if(wv>0) {
                double b=at(u,1-v);
                if(wu>0)b=b*(1-wu)+at(1-u,1-v)*wu;
                a=a*(1-wv)+b*wv;
            }
            return a-mean;
        }
        private static double edgeWeight(double p) {
            double distance=Math.min(p,1-p);
            return distance>=.08?0:.5*(1-smooth(distance/.08));
        }
        private double at(double u,double v) {
            double x=u*(width-1),y=v*(height-1);int ix=(int)x,iy=(int)y;
            int nx=Math.min(width-1,ix+1),ny=Math.min(height-1,iy+1);
            double fx=x-ix,fy=y-iy;
            double a=luminance[iy*width+ix]*(1-fx)+luminance[iy*width+nx]*fx;
            double b=luminance[ny*width+ix]*(1-fx)+luminance[ny*width+nx]*fx;
            return a*(1-fy)+b*fy;
        }
    }
    private static double fold(double coordinate,double center,double width) {
        double d=coordinate-center;d-=Math.rint(d);d=d*TILE/width;
        return d*Math.exp(-d*d*.5)-Math.exp(-d*d*8)*.16;
    }
    private static void stains(BufferedImage image,double amount,double zoom) {
        Graphics2D g=image.createGraphics();
        try {
            g.scale(zoom,zoom);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Random random=new Random(312011);
            for(int i=0;i<3;i++) {
                double x=random.nextDouble()*TILE,y=random.nextDouble()*TILE,r=28+random.nextDouble()*44;
                for(int tx=-1;tx<=1;tx++)for(int ty=-1;ty<=1;ty++)for(int layer=5;layer>=1;layer--) {
                    g.setColor(new Color(121,82,41,Math.min(30,(int)((layer==1?8:2)*amount))));
                    g.setStroke(new BasicStroke(layer*1.1f));
                    g.draw(new java.awt.geom.Ellipse2D.Double(x+tx*TILE-r,y+ty*TILE-r,r*2,r*1.85));
                }
            }
        } finally {g.dispose();}
    }

    private static double noise(double u, double v, int nx, int ny, int seed) {
        double x = u * nx, y = v * ny;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = smooth(x - ix), fy = smooth(y - iy);
        double a = hash(Math.floorMod(ix,nx), Math.floorMod(iy,ny), seed), b = hash(Math.floorMod(ix+1,nx), Math.floorMod(iy,ny), seed);
        double c = hash(Math.floorMod(ix,nx), Math.floorMod(iy+1,ny), seed), d = hash(Math.floorMod(ix+1,nx), Math.floorMod(iy+1,ny), seed);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }
    private static double hash(int x, int y, int seed) {
        int n = x * 0x1f123bb5 ^ y * 0x5f356495 ^ seed * 0x6c8e9cf5;
        n ^= n >>> 16; n *= 0x7feb352d; n ^= n >>> 15; n *= 0x846ca68b; n ^= n >>> 16;
        return (n & 0xFFFFFF) / 8388607.5 - 1;
    }
    private static double smooth(double n) { return n * n * (3 - 2 * n); }
    private static int channel(double value) { return Math.max(0, Math.min(255, (int) Math.round(value))); }
}
