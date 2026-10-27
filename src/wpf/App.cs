using System;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Win32;
using System.Diagnostics;
using System.Windows.Media.Imaging;

namespace SqlDeveloperFont50 {
    public sealed class App {
        static string smokeOutput, smokeAppData;
        Window window;
        Engine engine;
        ComboBox installations, editorSize, gridSize, padding, gridFont;
        CheckBox header;
        TextBlock installInfo, status, health, preview;
        TextBox log;
        Border previewCell;
        Button apply, restore, launch, search, browse;
        bool busy;
        T Find<T>(string name) where T : class { return window.FindName(name) as T; }
        Installation Selected { get { return installations.SelectedItem as Installation; } }
        void Append(string message) {
            if (!window.Dispatcher.CheckAccess()) { window.Dispatcher.BeginInvoke(new Action<string>(Append), message); return; }
            log.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + message + Environment.NewLine); log.ScrollToEnd();
        }
        void SetBusy(bool value) {
            busy = value;
            foreach (Control control in new Control[] { installations, apply, restore, launch, search, browse, editorSize, gridSize, padding, gridFont, header }) control.IsEnabled = !value;
        }
        static int Value(ComboBox combo, int fallback) { return combo.SelectedItem is int ? (int)combo.SelectedItem : fallback; }
        void Preview() {
            if (preview == null) return;
            preview.FontSize = Value(gridSize, 24);
            int verticalPadding = Value(padding, 8);
            previewCell.Padding = new Thickness(8, Math.Max(0, verticalPadding), 8, Math.Max(0, verticalPadding));
            preview.Margin = new Thickness(0, Math.Min(0, verticalPadding), 0, Math.Min(0, verticalPadding));
            previewCell.ClipToBounds = true;
            string family = gridFont.SelectedItem as string;
            preview.FontFamily = new FontFamily(string.IsNullOrWhiteSpace(family) || family.StartsWith("(") ? "Malgun Gothic" : family);
        }
        void SelectInstallation() {
            var selected = Selected;
            if (selected == null) return;
            installInfo.Text = "버전 " + selected.Version + (selected.Version == Engine.SupportedVersion ? " · 지원되는 설치본" : " · 지원하지 않는 빌드") +
                (File.Exists(Path.Combine(selected.Root, ".font50-backup", "state.json")) ? " · 기존 패치/백업 감지" : "");
            var options = engine.ReadOptions(selected);
            editorSize.SelectedItem = options.EditorSize; gridSize.SelectedItem = options.GridSize; padding.SelectedItem = options.Padding;
            if (!string.IsNullOrWhiteSpace(options.GridFamily) && !gridFont.Items.Contains(options.GridFamily)) gridFont.Items.Add(options.GridFamily);
            gridFont.SelectedItem = string.IsNullOrWhiteSpace(options.GridFamily) ? "(편집기 글꼴 따르기)" : options.GridFamily;
            header.IsChecked = options.Header;
            try { engine.Remember(selected.Root); } catch (Exception ex) { Append("경로 기억 실패: " + ex.Message); }
            status.Text = "적용할 준비가 됐습니다.";
        }
        async void Search() {
            SetBusy(true); status.Text = "설치 경로 자동 검색 중…";
            try {
                var found = await Task.Run(() => engine.Discover());
                installations.ItemsSource = found;
                if (found.Count > 0) { installations.SelectedIndex = 0; Append("설치 경로 " + found.Count + "개를 찾았습니다. 경로를 다시 입력할 필요가 없습니다."); }
                else { installInfo.Text = "자동 검색된 설치본이 없습니다. 직접 선택에서 sqldeveloper.exe를 선택하세요."; status.Text = "설치본을 찾지 못했습니다."; }
            } catch (Exception ex) { Append(ex.Message); status.Text = "검색 실패"; }
            finally {
                SetBusy(false);
                if (smokeOutput != null) window.Dispatcher.BeginInvoke(new Action(delegate {
                    try {
                        if (Selected == null || editorSize.Items.Count != 44 || gridSize.Items.Count != 45) throw new Exception("Window controls or automatic detection failed");
                        var visual = (System.Windows.Controls.Grid)window.Content;
                        visual.Background = window.Background;
                        visual.Measure(new Size(window.Width, window.Height - 40));
                        visual.Arrange(new Rect(0, 0, window.Width, window.Height - 40)); visual.UpdateLayout();
                        var bitmap = new RenderTargetBitmap((int)window.Width, (int)window.Height - 40, 96, 96, PixelFormats.Pbgra32);
                        bitmap.Render(visual);
                        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
                        using (var output = File.Create(smokeOutput)) encoder.Save(output);
                        File.WriteAllText(smokeOutput + ".txt", "PASS WPF startup, automatic detection, 7-50pt editor control, 6-50pt result control, rendered window.\r\nSelected: " + Selected.Root, new System.Text.UTF8Encoding(false));
                        Application.Current.Shutdown(0);
                    } catch (Exception ex) { File.WriteAllText(smokeOutput + ".txt", ex.ToString()); Application.Current.Shutdown(1); }
                }), DispatcherPriority.ApplicationIdle);
            }
        }
        void Browse() {
            var dialog = new OpenFileDialog { Title = "sqldeveloper.exe 선택", Filter = "SQL Developer|sqldeveloper.exe|실행 파일|*.exe" };
            if (dialog.ShowDialog(window) != true) return;
            Installation install = null;
            var dir = new DirectoryInfo(Path.GetDirectoryName(dialog.FileName));
            for (int i = 0; dir != null && i < 4 && install == null; i++, dir = dir.Parent) install = Engine.Inspect(dir.FullName);
            if (install == null) { MessageBox.Show(window, "SQL Developer 설치 폴더를 확인할 수 없습니다.", "설치본 확인"); return; }
            var list = installations.ItemsSource as System.Collections.Generic.List<Installation> ?? new System.Collections.Generic.List<Installation>();
            var existing = list.FirstOrDefault(x => x.Root.Equals(install.Root, StringComparison.OrdinalIgnoreCase));
            if (existing == null) { list.Add(install); installations.ItemsSource = null; installations.ItemsSource = list; }
            installations.SelectedItem = existing ?? install;
        }
        async void Apply() {
            var install = Selected;
            if (install == null) { status.Text = "설치본을 먼저 선택하세요."; return; }
            var options = engine.ReadOptions(install);
            options.EditorSize = Value(editorSize, 24); options.GridSize = Value(gridSize, 24); options.Padding = Value(padding, 8);
            options.GridFamily = gridFont.SelectedIndex <= 0 ? "" : Convert.ToString(gridFont.SelectedItem); options.Header = header.IsChecked == true;
            SetBusy(true); status.Text = "백업 및 적용 중…";
            try {
                await Task.Run(() => engine.Install(install, options));
                status.Text = "적용 완료 · SQL Developer를 실행하세요.";
                installInfo.Text = "버전 " + install.Version + " · 파일 패치 및 코드 캐시 갱신 완료";
            } catch (UnauthorizedAccessException) {
                status.Text = "쓰기 권한이 필요합니다.";
                Append("설치 폴더 또는 사용자 캐시에 쓰기 권한이 없습니다. 필요하면 이 프로그램을 관리자 권한으로 실행하세요.");
            } catch (Exception ex) { status.Text = "적용하지 못했습니다."; Append(ex.Message); }
            finally { SetBusy(false); }
        }
        async void Restore() {
            var install = Selected; if (install == null) return;
            SetBusy(true); status.Text = "원본 복구 중…";
            try { await Task.Run(() => engine.Restore(install)); status.Text = "원본 복구 완료"; SelectInstallation(); }
            catch (Exception ex) { status.Text = "복구하지 못했습니다."; Append(ex.Message); }
            finally { SetBusy(false); }
        }
        void Launch() {
            if (Selected == null) return;
            try {
                if (Engine.IsRunning(Selected.Root)) { status.Text = "SQL Developer가 이미 실행 중입니다."; return; }
                Process.Start(new ProcessStartInfo(Path.Combine(Selected.Root, "sqldeveloper.exe")) { WorkingDirectory = Selected.Root, UseShellExecute = true });
                status.Text = "SQL Developer 실행 중…";
            } catch (Exception ex) { Append(ex.Message); }
        }
        void Health() {
            try {
                var info = Engine.ReadJson(engine.RuntimePath);
                long timestamp = long.Parse(Engine.Text(info, "timestamp", "0"));
                long now = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1)).TotalMilliseconds;
                if (now - timestamp > 15000) { health.Text = "실행 확인 대기 · SQL Developer를 다시 실행하세요."; return; }
                string max = Engine.Text(info, "editorMenuMax", "0"), grids = Engine.Text(info, "grids", "0");
                health.Text = "실제 실행 확인: 확장 " + Engine.Text(info, "version", "?") + " · 편집기 최대 " + max + "pt · 결과표 " + grids + "개 · 행 높이 " + Engine.Text(info, "rowHeight", "0") + "px";
                string error = Engine.Text(info, "error", ""); if (error.Length > 0) health.Text += " · " + error;
                health.Foreground = new SolidColorBrush(max == "50" && error.Length == 0 ? Color.FromRgb(20, 105, 80) : Color.FromRgb(160, 65, 20));
            } catch { }
        }
        void Run() {
            var application = new Application();
            using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("MainWindow.xaml")) window = (Window)XamlReader.Load(stream);
            using (var icon = Assembly.GetExecutingAssembly().GetManifestResourceStream("Font50.ico"))
                window.Icon = BitmapFrame.Create(icon, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
            engine = new Engine(smokeAppData ?? Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData)); engine.Log = Append;
            installations = Find<ComboBox>("Installations"); editorSize = Find<ComboBox>("EditorSize"); gridSize = Find<ComboBox>("GridSize"); padding = Find<ComboBox>("Padding"); gridFont = Find<ComboBox>("GridFont");
            header = Find<CheckBox>("Header"); installInfo = Find<TextBlock>("InstallInfo"); status = Find<TextBlock>("Status"); health = Find<TextBlock>("Health"); preview = Find<TextBlock>("Preview"); previewCell = Find<Border>("PreviewCell"); log = Find<TextBox>("Log");
            apply = Find<Button>("Apply"); restore = Find<Button>("Restore"); launch = Find<Button>("Launch"); search = Find<Button>("Search"); browse = Find<Button>("Browse");
            for (int i = 7; i <= 50; i++) editorSize.Items.Add(i);
            for (int i = 6; i <= 50; i++) gridSize.Items.Add(i);
            for (int i = -20; i <= 30; i++) padding.Items.Add(i);
            gridFont.Items.Add("(편집기 글꼴 따르기)");
            foreach (var font in Fonts.SystemFontFamilies.OrderBy(f => f.Source)) gridFont.Items.Add(font.Source);
            editorSize.SelectedItem = 24; gridSize.SelectedItem = 24; padding.SelectedItem = 8; gridFont.SelectedIndex = 0;
            foreach (ComboBox combo in new[] { gridSize, padding, gridFont }) combo.SelectionChanged += delegate { Preview(); };
            installations.SelectionChanged += delegate { SelectInstallation(); };
            search.Click += delegate { Search(); }; browse.Click += delegate { Browse(); }; apply.Click += delegate { Apply(); }; restore.Click += delegate { Restore(); }; launch.Click += delegate { Launch(); };
            window.Closing += delegate(object sender, System.ComponentModel.CancelEventArgs e) { if (busy) { e.Cancel = true; status.Text = "작업이 끝난 뒤 닫을 수 있습니다."; } };
            var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) }; timer.Tick += delegate { Health(); }; timer.Start();
            window.Loaded += delegate { Search(); };
            application.DispatcherUnhandledException += delegate(object sender, DispatcherUnhandledExceptionEventArgs e) { MessageBox.Show(window, e.Exception.Message, "SQL Developer Configurator"); e.Handled = true; };
            if (smokeOutput != null) {
                application.MainWindow = window;
                window.Dispatcher.BeginInvoke(new Action(Search), DispatcherPriority.ApplicationIdle);
                application.Run();
            } else application.Run(window);
        }
        [STAThread] public static void Main(string[] args) {
            if (args.Length == 3 && args[0] == "--smoke-test") { smokeOutput = Path.GetFullPath(args[1]); smokeAppData = Path.GetFullPath(args[2]); }
            try { new App().Run(); }
            catch (Exception ex) { MessageBox.Show(ex.ToString(), "프로그램 시작 실패"); }
        }
    }
}
