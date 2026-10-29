using System;
using System.IO;
using System.IO.Compression;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;
using System.Xml;
using System.Globalization;
using Microsoft.Win32;

namespace SqlDeveloperFont50 {
    public sealed class Installation {
        public string Root { get; set; }
        public string Version { get; set; }
        public override string ToString() { return Root; }
    }
    public sealed class FontOptions {
        public int EditorSize = 24, GridSize = 24, Padding = 8;
        public string GridFamily = "";
        public bool Header;
        public bool SoftWrap, HorizontalRules;
        public double RuleThickness = 1;
        public string RuleColor = "#6F8296", RuleStyle = "solid";
    }
    public sealed class Engine {
        public const string SupportedVersion = "26.2.0.186.2220";
        public const string OriginalHash = "814A788583F93BF7DE5BB3837462F6A3AF05C7B8FA1AD690003C7179D12151C8";
        public const string BundleId = "io.github.sqldevgrid.result-grid-font";
        public const string BundleLine = "io.github.sqldevgrid.result-grid-font,0.11.0,../sqldeveloper/extensions/result-grid-font-extension.jar,4,false";
        public readonly string AppData;
        public readonly string ToolFolder;
        public Action<string> Log = delegate { };
        static readonly UTF8Encoding Utf8 = new UTF8Encoding(false, true);
        static readonly JavaScriptSerializer Json = new JavaScriptSerializer();
        public Engine(string appData) {
            AppData = Path.GetFullPath(appData);
            ToolFolder = Path.Combine(AppData, "SQL Developer Font 50 Manager");
        }
        public static string Hash(string path) {
            using (var stream = File.OpenRead(path)) using (var sha = SHA256.Create())
                return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "");
        }
        public static Dictionary<string, object> ReadJson(string path) {
            return Json.Deserialize<Dictionary<string, object>>(File.ReadAllText(path, Utf8));
        }
        public static string Text(Dictionary<string, object> obj, string name, string fallback) {
            return obj.ContainsKey(name) && obj[name] != null ? Convert.ToString(obj[name]) : fallback;
        }
        static int Number(Dictionary<string, object> obj, string name, int fallback) {
            int result; return int.TryParse(Text(obj, name, ""), out result) ? result : fallback;
        }
        static void WriteJson(string path, object value) { AtomicText(path, Json.Serialize(value)); }
        static void CommitTemp(string temp, string target) {
            for (int attempt = 0; ; attempt++) {
                try { if (File.Exists(target)) File.Replace(temp, target, null); else File.Move(temp, target); return; }
                catch (IOException) {
                    if (attempt >= 7 || !File.Exists(temp)) throw;
                    System.Threading.Thread.Sleep(25 * (attempt + 1));
                }
            }
        }
        public static void AtomicText(string path, string value) {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            string temp = path + ".font50-" + Guid.NewGuid().ToString("N") + ".tmp";
            File.WriteAllText(temp, value, Utf8);
            try { CommitTemp(temp, path); }
            finally { if (File.Exists(temp)) File.Delete(temp); }
        }
        static void AtomicCopy(string source, string target) {
            string temp = target + ".font50-" + Guid.NewGuid().ToString("N") + ".tmp";
            File.Copy(source, temp);
            try { CommitTemp(temp, target); }
            finally { if (File.Exists(temp)) File.Delete(temp); }
        }
        public static Installation Inspect(string root) {
            try {
                root = Path.GetFullPath(root);
                if (root.Length > Path.GetPathRoot(root).Length) root = root.TrimEnd(Path.DirectorySeparatorChar);
                string versionFile = Path.Combine(root, "sqldeveloper", "bin", "version.properties");
                if (!File.Exists(Path.Combine(root, "sqldeveloper.exe")) || !File.Exists(versionFile) ||
                    !File.Exists(Path.Combine(root, "ide", "extensions", "oracle.ide.ceditor.jar"))) return null;
                var match = Regex.Match(File.ReadAllText(versionFile, Utf8), @"(?m)^VER_FULL=([^\r\n]+)");
                return match.Success ? new Installation { Root = root, Version = match.Groups[1].Value.Trim() } : null;
            } catch { return null; }
        }
        public string LastRoot() {
            try { return Text(ReadJson(Path.Combine(ToolFolder, "manager.json")), "lastRoot", ""); } catch { return ""; }
        }
        public void Remember(string root) { WriteJson(Path.Combine(ToolFolder, "manager.json"), new { lastRoot = root }); }
        public List<Installation> Discover() {
            var found = new Dictionary<string, Installation>(StringComparer.OrdinalIgnoreCase);
            Action<string> candidate = delegate(string path) {
                if (string.IsNullOrWhiteSpace(path)) return;
                try {
                    var dir = new DirectoryInfo(path);
                    for (int i = 0; dir != null && i < 5; i++, dir = dir.Parent) {
                        var install = Inspect(dir.FullName);
                        if (install != null) { found[install.Root] = install; return; }
                    }
                } catch { }
            };
            candidate(LastRoot()); candidate(AppDomain.CurrentDomain.BaseDirectory);
            foreach (var process in Process.GetProcesses()) {
                using (process) {
                    try { if (process.ProcessName.StartsWith("sqldeveloper", StringComparison.OrdinalIgnoreCase)) candidate(Path.GetDirectoryName(process.MainModule.FileName)); } catch { }
                }
            }
            foreach (RegistryKey hive in new[] { Registry.CurrentUser, Registry.LocalMachine }) {
                foreach (string keyName in new[] { @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall", @"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall" }) {
                    try {
                        using (var uninstall = hive.OpenSubKey(keyName)) {
                            if (uninstall == null) continue;
                            foreach (string sub in uninstall.GetSubKeyNames()) using (var entry = uninstall.OpenSubKey(sub)) {
                                if (entry != null && Convert.ToString(entry.GetValue("DisplayName")).IndexOf("SQL Developer", StringComparison.OrdinalIgnoreCase) >= 0)
                                    candidate(Convert.ToString(entry.GetValue("InstallLocation")));
                            }
                        }
                    } catch { }
                }
            }
            // SQL Developer is often unpacked rather than installed through MSI.
            var seeds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var drive in DriveInfo.GetDrives()) {
                try {
                    if (!drive.IsReady || drive.DriveType != DriveType.Fixed) continue;
                    string root = drive.RootDirectory.FullName;
                    candidate(Path.Combine(root, "sqldeveloper"));
                    foreach (string name in new[] { "dev", "tools", "oracle", "apps", "Program Files", "Program Files (x86)" })
                        if (Directory.Exists(Path.Combine(root, name))) seeds.Add(Path.Combine(root, name));
                    foreach (var directory in Directory.GetDirectories(root))
                        if (Regex.IsMatch(Path.GetFileName(directory), "sql|oracle|tool|dev", RegexOptions.IgnoreCase)) seeds.Add(directory);
                } catch { }
            }
            foreach (string folder in new[] { Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads") })
                if (Directory.Exists(folder)) seeds.Add(folder);
            var queue = new Queue<KeyValuePair<string, int>>(seeds.Select(p => new KeyValuePair<string, int>(p, 0)));
            int count = 0;
            while (queue.Count > 0 && count++ < 10000) {
                var item = queue.Dequeue();
                var install = Inspect(item.Key);
                if (install != null) { found[install.Root] = install; continue; }
                if (item.Value >= 4) continue;
                try {
                    foreach (string child in Directory.GetDirectories(item.Key)) {
                        string name = Path.GetFileName(child);
                        if (name.StartsWith(".") || Regex.IsMatch(name, "^(node_modules|build|bin|obj|jdk|jre|Windows|font50-package|target)$", RegexOptions.IgnoreCase)) continue;
                        if ((File.GetAttributes(child) & FileAttributes.ReparsePoint) == 0) queue.Enqueue(new KeyValuePair<string, int>(child, item.Value + 1));
                    }
                } catch { }
            }
            string remembered = LastRoot();
            return found.Values.OrderByDescending(p => p.Root.Equals(remembered, StringComparison.OrdinalIgnoreCase)).ThenBy(p => p.Root).ToList();
        }
        public string SystemFolder(Installation install) { return Path.Combine(AppData, "SQL Developer", "system" + install.Version); }
        public string GridSettingsPath { get { return Path.Combine(AppData, "SQL Developer Result Grid Font", "settings.json"); } }
        public string EditorSettingsPath { get { return Path.Combine(AppData, "SQL Developer Result Grid Font", "editor-settings.json"); } }
        public string RuntimePath { get { return Path.Combine(AppData, "SQL Developer Result Grid Font", "runtime-state.json"); } }
        string PreferencesPath(Installation install) { return Path.Combine(SystemFolder(install), "o.sqldeveloper", "product-preferences.xml"); }
        public FontOptions ReadOptions(Installation install) {
            var options = new FontOptions();
            try {
                var xml = new XmlDocument(); xml.Load(PreferencesPath(install));
                var node = xml.SelectSingleNode("//hash[@n='FontSizeOptions']/value[@n='fontSize']/@v");
                if (node != null) options.EditorSize = Math.Max(7, Math.Min(50, int.Parse(node.Value)));
            } catch { }
            try {
                var grid = ReadJson(GridSettingsPath);
                int storedSize = Number(grid, "fontSize", options.EditorSize);
                options.GridSize = storedSize == 0 ? options.EditorSize : Math.Max(6, Math.Min(50, storedSize));
                options.Padding = Math.Max(-20, Math.Min(30, Number(grid, "paddingPerSide", 8)));
                options.GridFamily = Text(grid, "fontFamily", "");
                options.Header = Text(grid, "applyToHeader", "false").Equals("true", StringComparison.OrdinalIgnoreCase);
            } catch { }
            try {
                var display = ReadJson(EditorSettingsPath);
                options.SoftWrap = Text(display, "softWrap", "false").Equals("true", StringComparison.OrdinalIgnoreCase);
                options.HorizontalRules = Text(display, "horizontalRules", "false").Equals("true", StringComparison.OrdinalIgnoreCase);
                double thickness;
                if (double.TryParse(display.ContainsKey("ruleThickness") ? Convert.ToString(display["ruleThickness"], CultureInfo.InvariantCulture) : "1", NumberStyles.Float, CultureInfo.InvariantCulture, out thickness)) options.RuleThickness = Math.Max(.5, Math.Min(8, thickness));
                options.RuleColor = Text(display, "ruleColor", "#6F8296"); options.RuleStyle = Text(display, "ruleStyle", "solid");
            } catch { }
            return options;
        }
        public static bool IsRunning(string root) {
            foreach (var process in Process.GetProcesses()) using (process) {
                try {
                    if (process.Id == Process.GetCurrentProcess().Id) continue;
                    if (!Regex.IsMatch(process.ProcessName, "^sqldeveloper(?:64)?w?$", RegexOptions.IgnoreCase) && process.ProcessName != "java" && process.ProcessName != "javaw") continue;
                    string path = process.MainModule.FileName;
                    if (path.StartsWith(root.TrimEnd('\\') + "\\", StringComparison.OrdinalIgnoreCase)) return true;
                } catch { }
            }
            return false;
        }
        static string Resource(string name, string directory) {
            string path = Path.Combine(directory, name);
            using (var input = Assembly.GetExecutingAssembly().GetManifestResourceStream(name)) {
                if (input == null) throw new InvalidOperationException("내장 파일이 없습니다: " + name);
                using (var output = File.Create(path)) input.CopyTo(output);
            }
            return path;
        }
        static void CreateAgent(string extension, string agent) {
            using (var input = ZipFile.OpenRead(extension)) using (var output = ZipFile.Open(agent, ZipArchiveMode.Create)) {
                var manifest = output.CreateEntry("META-INF/MANIFEST.MF");
                using (var writer = new StreamWriter(manifest.Open(), new UTF8Encoding(false)))
                    writer.Write("Manifest-Version: 1.0\r\nPremain-Class: io.github.sqldevgrid.BootstrapAgent\r\n\r\n");
                foreach (var entry in input.Entries) {
                    if (entry.FullName.StartsWith("io/github/sqldevgrid/EditorPreferencesPanel", StringComparison.Ordinal)) continue;
                    if (!entry.FullName.EndsWith(".class", StringComparison.Ordinal) && entry.FullName != "editor-bridge.jar") continue;
                    var copy = output.CreateEntry(entry.FullName);
                    using (var read = entry.Open()) using (var write = copy.Open()) read.CopyTo(write);
                }
            }
        }
        static string JavaPath(Installation install) {
            foreach (string relative in new[] { @"jdk\jre\bin\java.exe", @"jdk\bin\java.exe" }) {
                string path = Path.Combine(install.Root, relative); if (File.Exists(path)) return path;
            }
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrWhiteSpace(javaHome) && File.Exists(Path.Combine(javaHome, "bin", "java.exe"))) return Path.Combine(javaHome, "bin", "java.exe");
            foreach (string part in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';')) {
                try { string path = Path.Combine(part.Trim('"'), "java.exe"); if (File.Exists(path)) return path; } catch { }
            }
            throw new InvalidOperationException("Java 17 이상을 찾지 못했습니다. JDK가 포함된 SQL Developer를 선택하세요.");
        }
        static string Quote(string value) { return "\"" + value.Replace("\"", "\\\"") + "\""; }
        static void PatchEditor(string java, string patcher, string input, string output) {
            var start = new ProcessStartInfo(java, "--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp " + Quote(patcher) + " EditorPatcher " + Quote(input) + " " + Quote(output));
            start.UseShellExecute = false; start.CreateNoWindow = true; start.RedirectStandardOutput = true; start.RedirectStandardError = true;
            using (var process = Process.Start(start)) {
                var stdout = process.StandardOutput.ReadToEndAsync(); var stderr = process.StandardError.ReadToEndAsync();
                if (!process.WaitForExit(60000)) { process.Kill(); throw new InvalidOperationException("편집기 패치 시간이 초과됐습니다."); }
                if (process.ExitCode != 0) throw new InvalidOperationException("편집기 패치 실패: " + stderr.Result + stdout.Result);
            }
        }
        public string CachePath(Installation install) { return Path.Combine(SystemFolder(install), "system_cache", "var", "cache"); }
        public string RefreshCache(Installation install) {
            string cache = Path.GetFullPath(CachePath(install));
            string system = Path.GetFullPath(SystemFolder(install)).TrimEnd('\\') + "\\";
            if (!cache.StartsWith(system, StringComparison.OrdinalIgnoreCase) || Path.GetFileName(cache) != "cache") throw new InvalidOperationException("캐시 경로 검증 실패");
            if (!Directory.Exists(cache)) return "";
            string target = cache + ".font50-" + DateTime.Now.ToString("yyyyMMdd-HHmmss-fff");
            Directory.Move(cache, target);
            Log("이전 코드 캐시 보관: " + target);
            return target;
        }
        string UserBackupFolder(Installation install) {
            byte[] data = SHA256.Create().ComputeHash(Encoding.UTF8.GetBytes(install.Root.ToUpperInvariant()));
            return Path.Combine(ToolFolder, "backups", BitConverter.ToString(data, 0, 8).Replace("-", ""));
        }
        void BackupUserSettings(Installation install) {
            string folder = UserBackupFolder(install);
            if (File.Exists(Path.Combine(folder, "user-state.json"))) {
                var existing = ReadJson(Path.Combine(folder, "user-state.json"));
                if (!existing.ContainsKey("hadEditor")) {
                    existing["hadEditor"] = File.Exists(EditorSettingsPath);
                    if (File.Exists(EditorSettingsPath)) File.Copy(EditorSettingsPath, Path.Combine(folder, "editor-settings.json"), true);
                    WriteJson(Path.Combine(folder, "user-state.json"), existing);
                }
                return;
            }
            Directory.CreateDirectory(folder);
            string preferences = PreferencesPath(install);
            bool hadPreferences = File.Exists(preferences), hadGrid = File.Exists(GridSettingsPath);
            if (hadPreferences) File.Copy(preferences, Path.Combine(folder, "product-preferences.xml"), true);
            if (hadGrid) File.Copy(GridSettingsPath, Path.Combine(folder, "settings.json"), true);
            bool hadEditor = File.Exists(EditorSettingsPath);
            if (hadEditor) File.Copy(EditorSettingsPath, Path.Combine(folder, "editor-settings.json"), true);
            WriteJson(Path.Combine(folder, "user-state.json"), new { hadPreferences = hadPreferences, hadGrid = hadGrid, hadEditor = hadEditor });
        }
        void SaveOptions(Installation install, FontOptions options) {
            if (options.EditorSize < 7 || options.EditorSize > 50 || options.GridSize < 6 || options.GridSize > 50 || options.Padding < -20 || options.Padding > 30)
                throw new InvalidOperationException("글꼴 크기와 여백 범위를 확인하세요.");
            if (!Regex.IsMatch(options.RuleColor ?? "", "^#[0-9a-fA-F]{6}$") || options.RuleThickness < .5 || options.RuleThickness > 8 ||
                !new[] {"solid", "dashed", "dotted", "dashdot"}.Contains(options.RuleStyle))
                throw new InvalidOperationException("수평선 색상(#RRGGBB), 두께(0.5~8px), 모양을 확인하세요.");
            string preferences = PreferencesPath(install);
            if (File.Exists(preferences)) {
                string text = File.ReadAllText(preferences, Utf8);
                var validate = new XmlDocument(); validate.LoadXml(text);
                var section = Regex.Match(text, "<hash\\s+n=\"FontSizeOptions\">.*?</hash>", RegexOptions.Singleline);
                if (section.Success) {
                    string changed;
                    if (Regex.IsMatch(section.Value, "<value\\s+n=\"fontSize\""))
                        changed = Regex.Replace(section.Value, "(<value\\s+n=\"fontSize\"\\s+v=\")[^\"]*(\")", m => m.Groups[1].Value + options.EditorSize + m.Groups[2].Value);
                    else changed = section.Value.Replace("</hash>", "<value n=\"fontSize\" v=\"" + options.EditorSize + "\"/></hash>");
                    text = text.Substring(0, section.Index) + changed + text.Substring(section.Index + section.Length);
                } else {
                    int end = text.LastIndexOf("</" + validate.DocumentElement.Name + ">", StringComparison.Ordinal);
                    if (end < 0) throw new InvalidOperationException("편집기 환경설정 구조를 확인할 수 없습니다.");
                    text = text.Insert(end, "<hash n=\"FontSizeOptions\"><value n=\"fontSize\" v=\"" + options.EditorSize + "\"/></hash>");
                }
                validate.LoadXml(text);
                var check = validate.SelectSingleNode("//hash[@n='FontSizeOptions']/value[@n='fontSize']/@v");
                if (check == null || check.Value != options.EditorSize.ToString()) throw new InvalidOperationException("편집기 크기 설정을 기록하지 못했습니다.");
                AtomicText(preferences, text);
            } else AtomicText(preferences, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ide:preferences xmlns:ide=\"http://xmlns.oracle.com/ide/hash\"><hash n=\"FontSizeOptions\"><value n=\"fontSize\" v=\"" + options.EditorSize + "\"/></hash></ide:preferences>");
            WriteJson(GridSettingsPath, new { enabled = true, fontFamily = options.GridFamily, fontSize = options.GridSize, paddingPerSide = options.Padding, applyToHeader = options.Header });
            WriteJson(EditorSettingsPath, new { softWrap = options.SoftWrap, horizontalRules = options.HorizontalRules,
                ruleThickness = options.RuleThickness, ruleColor = options.RuleColor.ToUpperInvariant(), ruleStyle = options.RuleStyle });
        }
        public void Install(Installation install, FontOptions options) {
            if (install == null || Inspect(install.Root) == null) throw new InvalidOperationException("SQL Developer 설치 폴더를 선택하세요.");
            if (install.Version != SupportedVersion) throw new InvalidOperationException("현재 지원 빌드: " + SupportedVersion + " / 선택한 빌드: " + install.Version);
            if (IsRunning(install.Root)) throw new InvalidOperationException("SQL Developer를 종료한 뒤 적용을 다시 누르세요. 작업 중인 문서는 자동으로 닫지 않습니다.");
            string editor = Path.Combine(install.Root, @"ide\extensions\oracle.ide.ceditor.jar"), extension = Path.Combine(install.Root, @"sqldeveloper\extensions\result-grid-font-extension.jar");
            string bundles = Path.Combine(install.Root, @"configuration\bundles.info"), backup = Path.Combine(install.Root, ".font50-backup");
            string agent = Path.Combine(install.Root, @"sqldeveloper\extensions\result-grid-font-agent.jar");
            string statePath = Path.Combine(backup, "state.json"), original = editor;
            Dictionary<string, object> state = null;
            if (File.Exists(statePath)) {
                state = ReadJson(statePath); original = Path.Combine(backup, "editor.jar");
                if (Hash(editor) != Text(state, "editorPatchedHash", "") || Hash(original) != OriginalHash)
                    throw new InvalidOperationException("기존 패치 이후 편집기 파일이 변경됐습니다. 원본을 덮어쓰지 않도록 중단했습니다.");
                if (!File.Exists(extension) || Hash(extension) != Text(state, "extensionPatchedHash", "")) throw new InvalidOperationException("기존 결과셋 확장 파일이 변경됐습니다.");
            } else if (Hash(editor) != OriginalHash) throw new InvalidOperationException("검증한 원본과 다른 편집기 JAR입니다. 이 버전에는 적용할 수 없습니다.");
            string text = File.ReadAllText(bundles, Utf8);
            var registrations = text.Split(new[] { "\r\n", "\n" }, StringSplitOptions.None).Where(s => s.StartsWith(BundleId + ",", StringComparison.Ordinal)).ToList();
            if (registrations.Count > 1 || registrations.Any(s => !Regex.IsMatch(s, "^" + Regex.Escape(BundleId) + @",0\.(?:[1-9]|10|11)\.0,\.\./sqldeveloper/extensions/result-grid-font-extension\.jar,4,false$")))
                throw new InvalidOperationException("기존 결과셋 확장 등록이 예상과 다릅니다.");
            string lineEnding = text.Contains("\r\n") ? "\r\n" : "\n";
            string changed = registrations.Count == 1 ? text.Replace(registrations[0], BundleLine) : text.TrimEnd('\r', '\n') + lineEnding + BundleLine + lineEnding;
            string stage = Path.Combine(install.Root, ".font50-stage-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(stage);
            try {
                string patcher = Resource("font50-patcher.jar", stage), newExtension = Resource("result-grid-font-extension.jar", stage), patched = Path.Combine(stage, "editor.jar");
                string newAgent = Path.Combine(stage, "agent.jar"); CreateAgent(newExtension, newAgent);
                Log("편집기 및 결과셋 표시 기능 적용 중…");
                PatchEditor(JavaPath(install), patcher, original, patched);
                if (state == null) {
                    if (Directory.Exists(backup)) throw new InvalidOperationException("완료되지 않은 이전 백업 폴더가 있습니다: " + backup);
                    Directory.CreateDirectory(backup); File.Copy(editor, Path.Combine(backup, "editor.jar")); File.Copy(bundles, Path.Combine(backup, "bundles.info"));
                    bool hadExtension = File.Exists(extension); if (hadExtension) File.Copy(extension, Path.Combine(backup, "extension.jar"));
                    state = new Dictionary<string, object> {
                        { "editorOriginalHash", OriginalHash }, { "hadExtension", hadExtension },
                        { "extensionOriginalHash", hadExtension ? Hash(extension) : null },
                        { "bundleOriginalHash", Hash(bundles) }, { "oldBundleLine", registrations.Count == 1 ? registrations[0] : null }
                    };
                }
                BackupUserSettings(install);
                // Snapshot the current install for rollback, including a previous version of this patch.
                File.Copy(editor, Path.Combine(stage, "previous-editor.jar")); File.Copy(bundles, Path.Combine(stage, "previous-bundles.info"));
                bool previousExtension = File.Exists(extension); if (previousExtension) File.Copy(extension, Path.Combine(stage, "previous-extension.jar"));
                bool previousAgent = File.Exists(agent); if (previousAgent) File.Copy(agent, Path.Combine(stage, "previous-agent.jar"));
                if (!state.ContainsKey("hadAgent")) {
                    state["hadAgent"] = previousAgent;
                    if (previousAgent) { File.Copy(agent, Path.Combine(backup, "agent.jar"), true); state["agentOriginalHash"] = Hash(agent); }
                }
                string oldState = File.Exists(statePath) ? File.ReadAllText(statePath, Utf8) : null;
                string prefs = PreferencesPath(install), oldPreferences = File.Exists(prefs) ? File.ReadAllText(prefs, Utf8) : null;
                string oldGrid = File.Exists(GridSettingsPath) ? File.ReadAllText(GridSettingsPath, Utf8) : null;
                string oldEditorSettings = File.Exists(EditorSettingsPath) ? File.ReadAllText(EditorSettingsPath, Utf8) : null;
                string configPath = Path.Combine(install.Root, @"sqldeveloper\bin\sqldeveloper.conf");
                string oldConfig = File.Exists(configPath) ? File.ReadAllText(configPath, Utf8) : "";
                string configBackup = Path.Combine(backup, "sqldeveloper.conf");
                if (!File.Exists(configBackup)) { File.WriteAllText(configBackup, oldConfig, Utf8); state["configOriginalHash"] = Hash(configBackup); }
                try {
                    AtomicCopy(patched, editor); AtomicCopy(newExtension, extension); AtomicText(bundles, changed);
                    AtomicCopy(newAgent, agent);
                    string config = Regex.Replace(oldConfig, @"(?ms)^# FONT50 AGENT BEGIN\r?\n.*?^# FONT50 AGENT END\r?\n?", "");
                    string agentPath = agent.Replace('\\', '/');
                    AtomicText(configPath, config.TrimEnd('\r', '\n') + "\r\n\r\n# FONT50 AGENT BEGIN\r\nAddVMOption -javaagent:" + agentPath + "\r\n# FONT50 AGENT END\r\n");
                    SaveOptions(install, options);
                    state["packageVersion"] = "3.4.1"; state["root"] = install.Root; state["installedAt"] = DateTime.Now.ToString("o");
                    state["editorPatchedHash"] = Hash(editor); state["extensionPatchedHash"] = Hash(extension); state["bundlePatchedLine"] = BundleLine;
                    state["agentPatchedHash"] = Hash(agent);
                    WriteJson(statePath, state);
                    // Resource cache contains class bytecode; updating the JAR alone is insufficient.
                    RefreshCache(install);
                } catch {
                    AtomicCopy(Path.Combine(stage, "previous-editor.jar"), editor); AtomicCopy(Path.Combine(stage, "previous-bundles.info"), bundles);
                    AtomicText(configPath, oldConfig);
                    if (previousExtension) AtomicCopy(Path.Combine(stage, "previous-extension.jar"), extension); else if (File.Exists(extension)) File.Delete(extension);
                    if (previousAgent) AtomicCopy(Path.Combine(stage, "previous-agent.jar"), agent); else if (File.Exists(agent)) File.Delete(agent);
                    if (oldPreferences != null) AtomicText(prefs, oldPreferences);
                    if (oldGrid != null) AtomicText(GridSettingsPath, oldGrid); else if (File.Exists(GridSettingsPath)) File.Delete(GridSettingsPath);
                    if (oldEditorSettings != null) AtomicText(EditorSettingsPath, oldEditorSettings); else if (File.Exists(EditorSettingsPath)) File.Delete(EditorSettingsPath);
                    if (oldState != null) AtomicText(statePath, oldState); else if (File.Exists(statePath)) File.Delete(statePath);
                    throw;
                }
                Remember(install.Root); Log("파일 적용 및 코드 캐시 갱신 완료. SQL Developer를 다시 실행하면 실제 로드 상태를 확인합니다.");
            } finally {
                foreach (string file in Directory.GetFiles(stage)) File.Delete(file);
                Directory.Delete(stage);
            }
        }
        public void Restore(Installation install) {
            if (IsRunning(install.Root)) throw new InvalidOperationException("SQL Developer를 종료한 뒤 복구하세요.");
            string backup = Path.Combine(install.Root, ".font50-backup"), statePath = Path.Combine(backup, "state.json");
            if (!File.Exists(statePath)) throw new InvalidOperationException("이 설치본에는 복구할 패치 백업이 없습니다.");
            var state = ReadJson(statePath);
            string editor = Path.Combine(install.Root, @"ide\extensions\oracle.ide.ceditor.jar"), extension = Path.Combine(install.Root, @"sqldeveloper\extensions\result-grid-font-extension.jar");
            string bundles = Path.Combine(install.Root, @"configuration\bundles.info");
            string agent = Path.Combine(install.Root, @"sqldeveloper\extensions\result-grid-font-agent.jar");
            if (Hash(editor) != Text(state, "editorPatchedHash", "") || Hash(Path.Combine(backup, "editor.jar")) != OriginalHash) throw new InvalidOperationException("편집기 또는 백업이 변경되어 복구를 중단했습니다.");
            if (!File.Exists(extension) || Hash(extension) != Text(state, "extensionPatchedHash", "")) throw new InvalidOperationException("결과셋 확장이 변경되어 복구를 중단했습니다.");
            if (state.ContainsKey("agentPatchedHash") && (!File.Exists(agent) || Hash(agent) != Text(state, "agentPatchedHash", ""))) throw new InvalidOperationException("시작 에이전트가 변경되어 복구를 중단했습니다.");
            bool hadExtension = Convert.ToBoolean(state["hadExtension"]);
            if (hadExtension && Hash(Path.Combine(backup, "extension.jar")) != Text(state, "extensionOriginalHash", "")) throw new InvalidOperationException("확장 백업 검증 실패");
            string text = File.ReadAllText(bundles, Utf8), currentLine = Text(state, "bundlePatchedLine", ""), oldLine = Text(state, "oldBundleLine", "");
            var lines = text.Split(new[] { "\r\n", "\n" }, StringSplitOptions.None).Where(s => s.StartsWith(BundleId + ",", StringComparison.Ordinal)).ToArray();
            if (lines.Length != 1 || lines[0] != currentLine) throw new InvalidOperationException("확장 등록이 변경되어 복구를 중단했습니다.");
            string changed = oldLine.Length > 0 ? text.Replace(currentLine, oldLine) : Regex.Replace(text, "(?m)^" + Regex.Escape(currentLine) + "(\\r?\\n|$)", "");
            string configPath = Path.Combine(install.Root, @"sqldeveloper\bin\sqldeveloper.conf"), configBackup = Path.Combine(backup, "sqldeveloper.conf");
            if (File.Exists(configBackup)) {
                if (Hash(configBackup) != Text(state, "configOriginalHash", "")) throw new InvalidOperationException("시작 설정 백업 검증 실패");
                string currentConfig = File.ReadAllText(configPath, Utf8);
                string withoutAgent = Regex.Replace(currentConfig, @"(?ms)^# FONT50 AGENT BEGIN\r?\n.*?^# FONT50 AGENT END\r?\n?", "");
                string originalConfig = File.ReadAllText(configBackup, Utf8);
                AtomicText(configPath, withoutAgent.TrimEnd('\r', '\n') == originalConfig.TrimEnd('\r', '\n') ? originalConfig : withoutAgent);
            }
            AtomicCopy(Path.Combine(backup, "editor.jar"), editor);
            if (hadExtension) AtomicCopy(Path.Combine(backup, "extension.jar"), extension); else File.Delete(extension);
            if (state.ContainsKey("hadAgent") && Convert.ToBoolean(state["hadAgent"])) AtomicCopy(Path.Combine(backup, "agent.jar"), agent);
            else if (state.ContainsKey("agentPatchedHash") && File.Exists(agent)) File.Delete(agent);
            AtomicText(bundles, changed);
            string userFolder = UserBackupFolder(install), userStatePath = Path.Combine(userFolder, "user-state.json");
            if (File.Exists(userStatePath)) {
                var user = ReadJson(userStatePath);
                if (Convert.ToBoolean(user["hadPreferences"])) AtomicCopy(Path.Combine(userFolder, "product-preferences.xml"), PreferencesPath(install));
                if (Convert.ToBoolean(user["hadGrid"])) AtomicCopy(Path.Combine(userFolder, "settings.json"), GridSettingsPath);
                else if (File.Exists(GridSettingsPath)) File.Delete(GridSettingsPath);
                if (user.ContainsKey("hadEditor")) {
                    if (Convert.ToBoolean(user["hadEditor"])) AtomicCopy(Path.Combine(userFolder, "editor-settings.json"), EditorSettingsPath);
                    else if (File.Exists(EditorSettingsPath)) File.Delete(EditorSettingsPath);
                }
                File.Move(userStatePath, userStatePath + ".restored-" + DateTime.Now.ToString("yyyyMMddHHmmssfff"));
            }
            RefreshCache(install);
            string retired = backup + ".restored-" + DateTime.Now.ToString("yyyyMMddHHmmssfff"); Directory.Move(backup, retired);
            Log("원본 복구 완료. 백업은 보관했습니다: " + retired);
        }
    }
}
