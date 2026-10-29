using System;
using System.IO;
using System.Linq;
using System.Text;
using System.Collections.Generic;
using SqlDeveloperFont50;

public static class EngineTests {
    static void Check(bool value, string name) { if (!value) throw new Exception(name); Console.WriteLine("PASS " + name); }
    public static int Main(string[] args) {
        try {
            string source = Path.GetFullPath(args[0]), tests = Path.GetFullPath(args[1]);
            var utf8 = new UTF8Encoding(false);
            Check(Engine.Inspect(Path.GetPathRoot(source)) == null, "drive root is not mistaken for a relative installation path");
            foreach (bool legacy in new[] { false, true }) {
                string root = Path.Combine(tests, legacy ? "기존 패치 PC" : "새 PC"), appdata = Path.Combine(root, "user-data");
                Directory.CreateDirectory(root);
                foreach (string folder in new[] { @"ide\extensions", @"sqldeveloper\extensions", @"sqldeveloper\bin", "configuration" }) Directory.CreateDirectory(Path.Combine(root, folder));
                File.WriteAllText(Path.Combine(root, "sqldeveloper.exe"), "test", utf8);
                File.WriteAllText(Path.Combine(root, @"sqldeveloper\bin\version.properties"), "VER_FULL=" + Engine.SupportedVersion, utf8);
                string originalConfig = "# original configuration\r\nAddVMOption -Xmx512m\r\n";
                string configPath = Path.Combine(root, @"sqldeveloper\bin\sqldeveloper.conf"); File.WriteAllText(configPath, originalConfig, utf8);
                string editor = Path.Combine(root, @"ide\extensions\oracle.ide.ceditor.jar"), bundles = Path.Combine(root, @"configuration\bundles.info"), extension = Path.Combine(root, @"sqldeveloper\extensions\result-grid-font-extension.jar");
                string originalEditor = Path.Combine(source, @".font50-backup\editor.jar");
                if (!File.Exists(originalEditor)) originalEditor = Path.Combine(source, @"ide\extensions\oracle.ide.ceditor.jar");
                File.Copy(originalEditor, editor, true);
                string oldLine = "io.github.sqldevgrid.result-grid-font,0.12.0,../sqldeveloper/extensions/result-grid-font-extension.jar,4,false";
                string baseBundles = "unrelated.bundle,1.0.0,../other.jar,4,false\r\n" + (legacy ? oldLine + "\r\n" : "");
                File.WriteAllText(bundles, baseBundles, utf8);
                if (legacy) using (var input = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("result-grid-font-extension.jar"))
                    using (var output = File.Create(extension)) input.CopyTo(output);
                var engine = new Engine(appdata); engine.Log = Console.WriteLine;
                var install = Engine.Inspect(root); Check(install != null, "inspect Unicode path with spaces");
                string pref = Path.Combine(engine.SystemFolder(install), @"o.sqldeveloper\product-preferences.xml");
                Directory.CreateDirectory(Path.GetDirectoryName(pref));
                string originalPreferences = "<?xml version='1.0' encoding='UTF-8'?><ide:preferences xmlns:ide='http://xmlns.oracle.com/ide/hash'><hash n=\"FontSizeOptions\"><value n=\"fontFamily\" v=\"Monospaced\"/><value n=\"fontSize\" v=\"34\"/></hash><hash n=\"Unrelated\"><value n=\"keep\" v=\"한글\"/></hash></ide:preferences>";
                File.WriteAllText(pref, originalPreferences, utf8);
                Directory.CreateDirectory(Path.GetDirectoryName(engine.GridSettingsPath));
                File.WriteAllText(engine.GridSettingsPath, "{\"enabled\":true,\"fontSize\":0}", utf8);
                Check(engine.ReadOptions(install).GridSize == 34, "legacy follow-editor size 0 imports as 34pt, never 6pt");
                File.Delete(engine.GridSettingsPath);
                string originalNotebook = "{\"enabled\":true,\"paper\":8,\"spacing\":15,\"memo\":\"기존 설정\"}";
                if (legacy) File.WriteAllText(engine.NotebookSettingsPath, originalNotebook, utf8);
                Directory.CreateDirectory(engine.CachePath(install)); File.WriteAllText(Path.Combine(engine.CachePath(install), "all-resources.dat"), "stale-classes", utf8);
                string originalExtensionHash = legacy ? Engine.Hash(extension) : "";
                engine.Install(install, new FontOptions { EditorSize = 50, GridSize = 50, Padding = 8, GridFamily = "Monospaced",
                    SoftWrap = true, HorizontalRules = true, RuleThickness = 50, RuleColor = "#12abef", RuleStyle = "double" });
                var display = engine.ReadOptions(install);
                Check(File.ReadAllText(engine.NotebookSettingsPath, utf8).Contains("\"enabled\":true"), "notebook enabled with separate native preference settings");
                if (legacy) Check(File.ReadAllText(engine.NotebookSettingsPath, utf8) == originalNotebook, "upgrade preserves existing notebook settings");
                string notebookCustom = "{\"enabled\":true,\"paper\":5,\"spacing\":75,\"memo\":\"한글\"}";
                File.WriteAllText(engine.NotebookSettingsPath, notebookCustom, utf8);
                Check(display.SoftWrap && display.HorizontalRules && display.RuleThickness == 50 && display.RuleColor == "#12ABEF" && display.RuleStyle == "double", "50px and new double rule saved and reloaded by installer");
                Check(engine.ReadOptions(install).EditorSize == 50 && engine.ReadOptions(install).GridSize == 50, "editor and result size saved as 50pt");
                using (var jar = System.IO.Compression.ZipFile.OpenRead(extension))
                    Check(jar.GetEntry("io/github/sqldevgrid/EditorPreferencesPanel.class") != null, "native preference panel packaged for the Oracle preference loader");
                Check(File.ReadAllText(pref, utf8).Contains("v=\"한글\""), "unrelated UTF-8 preferences preserved");
                Check(!Directory.Exists(engine.CachePath(install)), "stale code cache invalidated");
                Check(Directory.GetDirectories(Path.GetDirectoryName(engine.CachePath(install)), "cache.font50-*").Length == 1, "code cache backed up");
                Check(File.ReadAllText(bundles, utf8).Contains(Engine.BundleLine), "new extension registered");
                Check(File.ReadAllText(configPath, utf8).Contains("AddVMOption -javaagent:"), "startup agent registered independently of lazy extension loading");
                using (var jar = System.IO.Compression.ZipFile.OpenRead(Path.Combine(root,@"sqldeveloper\extensions\result-grid-font-agent.jar"))) {
                    Check(jar.GetEntry("editor-bridge.jar") != null && jar.GetEntry("io/github/sqldevgrid/EditorTransformer.class") != null,
                        "installed startup agent includes editor hooks and bootstrap helper");
                    Check(jar.GetEntry("META-INF/extension.xml") == null, "startup agent excludes Oracle extension metadata");
                    Check(jar.GetEntry("io/github/sqldevgrid/NativePreferencesBridge.class") != null,"startup agent includes native preference registry hook");
                    Check(jar.GetEntry("io/github/sqldevgrid/EditorPreferencesPanel.class") == null, "native preferences page stays in Oracle bundle, outside bootstrap agent");
                    Check(jar.GetEntry("io/github/sqldevgrid/NotebookPreferencesPanel.class") == null, "notebook native page stays outside startup agent");
                }
                engine.Install(install, new FontOptions { EditorSize = 34, GridSize = 34, Padding = 10 });
                Check(File.ReadAllText(engine.NotebookSettingsPath, utf8) == notebookCustom, "reapply preserves custom notebook settings byte-for-byte");
                Check(engine.ReadOptions(install).EditorSize == 34 && engine.ReadOptions(install).Padding == 10, "reapply updates size and padding");
                foreach (int requestedPadding in new[] {-20, 0, 30}) {
                    engine.Install(install, new FontOptions { EditorSize = 34, GridSize = 34, Padding = requestedPadding });
                    Check(engine.ReadOptions(install).Padding == requestedPadding, "padding saves and reloads " + requestedPadding + "px");
                }
                Check(Engine.Hash(Path.Combine(root, @".font50-backup\editor.jar")) == Engine.OriginalHash, "reapply keeps original backup");
                engine.Remember(root); Check(engine.LastRoot() == root, "installation path remembered");
                Check(engine.Discover().Any(x => x.Root == root), "remembered installation discovered automatically");
                File.AppendAllText(bundles, "added.later,1,../added.jar,4,false\r\n", utf8);
                engine.Restore(install);
                Check(Engine.Hash(editor) == Engine.OriginalHash, "original editor restored byte-for-byte");
                Check(File.ReadAllText(bundles, utf8) == baseBundles + "added.later,1,../added.jar,4,false\r\n", "restore preserves other extensions");
                Check(legacy ? File.Exists(extension) && Engine.Hash(extension) == originalExtensionHash : !File.Exists(extension), "original extension state restored");
                Check(File.ReadAllText(pref, utf8) == originalPreferences, "user preferences restored byte-for-byte");
                Check(File.ReadAllText(configPath, utf8) == originalConfig, "startup configuration restored byte-for-byte");
                Check(!File.Exists(engine.GridSettingsPath), "new grid settings removed on restore");
                Check(!File.Exists(engine.EditorSettingsPath), "new editor settings removed on restore");
                Check(legacy ? File.ReadAllText(engine.NotebookSettingsPath, utf8) == originalNotebook : !File.Exists(engine.NotebookSettingsPath), "original notebook settings restored or new settings removed");
                install.Version = "99.0.0";
                bool rejected = false; try { engine.Install(install, new FontOptions()); } catch (InvalidOperationException) { rejected = true; }
                Check(rejected && Engine.Hash(editor) == Engine.OriginalHash, "unsupported version rejected without mutations");
            }
            Console.WriteLine("ALL ENGINE TESTS PASSED"); return 0;
        } catch (Exception ex) { Console.Error.WriteLine(ex); return 1; }
    }
}
