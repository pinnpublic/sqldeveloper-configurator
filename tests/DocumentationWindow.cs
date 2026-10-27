using System;
using System.IO;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using SqlDeveloperFont50;

// Renders the shipped XAML at native 2x resolution without opening a window.
public static class DocumentationWindow {
    [STAThread] public static void Main(string[] args) {
        var assembly = Assembly.LoadFrom(args[0]);
        Window window;
        using (var stream = assembly.GetManifestResourceStream("MainWindow.xaml")) window = (Window)XamlReader.Load(stream);
        Func<string, ComboBox> combo = name => (ComboBox)window.FindName(name);
        combo("Installations").Items.Add(Engine.Inspect(args[2])); combo("Installations").SelectedIndex = 0;
        for (int i = 7; i <= 50; i++) combo("EditorSize").Items.Add(i);
        for (int i = 6; i <= 50; i++) combo("GridSize").Items.Add(i);
        for (int i = -20; i <= 30; i++) combo("Padding").Items.Add(i);
        combo("EditorSize").SelectedItem = 24; combo("GridSize").SelectedItem = 24; combo("Padding").SelectedItem = 8;
        combo("GridFont").Items.Add("(편집기 글꼴 따르기)"); combo("GridFont").SelectedIndex = 0;
        ((TextBlock)window.FindName("InstallInfo")).Text = "버전 " + Engine.SupportedVersion + " · 지원되는 설치본";
        ((TextBlock)window.FindName("Status")).Text = "적용할 준비가 됐습니다.";
        ((TextBox)window.FindName("Log")).Text = "설치 경로 1개를 찾았습니다.";
        var content = (FrameworkElement)window.Content; window.Content = null;
        var canvas = new Border { Width = 980, Height = 770, Background = window.Background, Child = content, Resources = window.Resources };
        TextElement.SetFontFamily(canvas, window.FontFamily); TextElement.SetFontSize(canvas, window.FontSize);
        TextOptions.SetTextFormattingMode(canvas, TextFormattingMode.Display);
        TextOptions.SetTextRenderingMode(canvas, TextRenderingMode.Grayscale);
        canvas.Measure(new Size(980,770)); canvas.Arrange(new Rect(0,0,980,770)); canvas.UpdateLayout();
        var bitmap = new RenderTargetBitmap(1960,1540,192,192,PixelFormats.Pbgra32); bitmap.Render(canvas);
        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using (var file = File.Create(args[1])) encoder.Save(file);
        Console.WriteLine("PASS WPF documentation: 1960x1540, 2x native rendering");
    }
}
