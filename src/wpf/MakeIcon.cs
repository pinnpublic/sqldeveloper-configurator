using System;
using System.IO;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Collections.Generic;

public static class MakeIcon {
    static Bitmap Draw(int size) {
        var bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(bitmap)) {
            g.SmoothingMode = SmoothingMode.AntiAlias; g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.ScaleTransform(size / 256f, size / 256f);
            using (var body = new LinearGradientBrush(new RectangleF(30, 42, 150, 176), Color.FromArgb(221, 231, 242), Color.FromArgb(102, 133, 167), LinearGradientMode.Horizontal))
            using (var outline = new Pen(Color.FromArgb(60, 85, 120), 6)) {
                g.FillEllipse(body, 30, 173, 150, 50);
                g.FillRectangle(body, 30, 49, 150, 149);
                g.DrawLine(outline, 30, 49, 30, 196); g.DrawLine(outline, 180, 49, 180, 196);
                g.DrawArc(outline, 30, 172, 150, 50, 0, 180);
                g.DrawArc(outline, 30, 120, 150, 50, 0, 180); g.DrawArc(outline, 30, 72, 150, 50, 0, 180);
                using (var top = new SolidBrush(Color.FromArgb(232, 240, 250))) g.FillEllipse(top, 30, 23, 150, 52);
                g.DrawEllipse(outline, 30, 23, 150, 52);
            }
            using (var badge = new SolidBrush(Color.FromArgb(22, 119, 100))) g.FillEllipse(badge, 122, 124, 122, 122);
            using (var rim = new Pen(Color.White, 6)) g.DrawEllipse(rim, 122, 124, 122, 122);
            using (var font = new Font("Segoe UI", size < 32 ? 67 : 53, FontStyle.Bold, GraphicsUnit.Pixel))
            using (var text = new SolidBrush(Color.White))
            using (var format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
                g.DrawString(size < 32 ? "A" : "Aa", font, text, new RectangleF(126, 124, 114, 117), format);
        }
        return bitmap;
    }
    public static void Main(string[] args) {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        var frames = new List<byte[]>();
        foreach (int size in sizes) using (var image = Draw(size)) using (var stream = new MemoryStream()) {
            image.Save(stream, ImageFormat.Png); frames.Add(stream.ToArray());
            if (size == 256) image.Save(Path.Combine(args[0], "Font50.png"), ImageFormat.Png);
        }
        using (var writer = new BinaryWriter(File.Create(Path.Combine(args[0], "Font50.ico")))) {
            writer.Write((ushort)0); writer.Write((ushort)1); writer.Write((ushort)sizes.Length);
            int offset = 6 + 16 * sizes.Length;
            for (int i = 0; i < sizes.Length; i++) {
                writer.Write((byte)(sizes[i] == 256 ? 0 : sizes[i])); writer.Write((byte)(sizes[i] == 256 ? 0 : sizes[i]));
                writer.Write((byte)0); writer.Write((byte)0); writer.Write((ushort)1); writer.Write((ushort)32);
                writer.Write(frames[i].Length); writer.Write(offset); offset += frames[i].Length;
            }
            foreach (byte[] frame in frames) writer.Write(frame);
        }
    }
}
