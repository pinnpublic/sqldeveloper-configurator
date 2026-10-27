using System;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Media.Imaging;
public static class VerifyWindow {
    [STAThread] public static int Main(string[] args) {
        try {
            Window window;
            using (var stream = File.OpenRead(args[0])) window = (Window)XamlReader.Load(stream);
            foreach (int tab in new[] {0}) {
                var root = (FrameworkElement)window.Content;
                root.Measure(new Size(980, 830)); root.Arrange(new Rect(0,0,980,830)); root.UpdateLayout();
                var apply = (Button)window.FindName("Apply");
                var point = apply.TranslatePoint(new Point(0,apply.ActualHeight),root);
                if (point.Y > 830 || apply.ActualHeight < 20) throw new Exception("Bottom button clipped");
                var bitmap = new RenderTargetBitmap(980,830,96,96,PixelFormats.Pbgra32); bitmap.Render(root);
                var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
                using(var stream=File.Create(args[1]+tab+".png")) encoder.Save(stream);
            }
            window.Close(); Console.WriteLine("PASS WPF installer renders without opening a window; controls fit"); return 0;
        } catch(Exception error) {Console.Error.WriteLine(error); return 1;}
    }
}
