param(
    [Parameter(Mandatory=$true)][string]$SqlDeveloperPath,
    [string]$JdkPath,
    [switch]$JavaOnly
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
$sdk = Get-SqlDeveloperHome $SqlDeveloperPath
$tools = Get-Toolchain $JdkPath
$output = Join-Path $PSScriptRoot 'out'
$classes = Join-Path $output 'extension'
$bridge = Join-Path $output 'bootstrap'
$patcher = Join-Path $output 'patcher'
$dist = Join-Path $PSScriptRoot 'dist'
foreach ($directory in @($output,$classes,$bridge,$patcher,$dist,(Join-Path $classes 'META-INF'))) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
$utf8 = [Text.UTF8Encoding]::new($false)

& $tools.Javac '-encoding' 'UTF-8' '--release' '17' '-d' $bridge (Join-Path $PSScriptRoot 'src\java\bootstrap\io\github\sqldevgrid\bridge\EditorBridge.java')
Assert-Exit 'Bootstrap bridge compilation'
& $tools.Jar '--create' '--file' (Join-Path $classes 'editor-bridge.jar') '-C' $bridge '.'
Assert-Exit 'Bootstrap bridge packaging'
& $tools.Javac '-encoding' 'UTF-8' '-source' '17' '-target' '17' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' '-d' $classes (Join-Path $PSScriptRoot 'src\java\instrumentation\io\github\sqldevgrid\EditorTransformer.java')
Assert-Exit 'Instrumentation compilation'
$javaSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\java\agent') -Recurse -File -Filter '*.java' | Select-Object -ExpandProperty FullName)
$oracleApi = @('ide\extensions\oracle.ide.jar','ide\lib\idert.jar','ide\lib\javatools.jar') | ForEach-Object { Join-Path $sdk $_ }
& $tools.Javac '-encoding' 'UTF-8' '--release' '17' '-cp' ($oracleApi -join ';') '-d' $classes @javaSources
Assert-Exit 'Java extension compilation'
$manifest = "Manifest-Version: 1.0`r`nPremain-Class: io.github.sqldevgrid.BootstrapAgent`r`nBundle-ManifestVersion: 2`r`nBundle-SymbolicName: io.github.sqldevgrid.result-grid-font`r`nBundle-Version: 0.12.0`r`nBundle-ClassPath: .`r`nRequire-Bundle: oracle.ide,oracle.ide.ceditor`r`n`r`n"
[IO.File]::WriteAllText((Join-Path $output 'extension.mf'),$manifest,$utf8)
$descriptor = @'
<?xml version="1.0" encoding="UTF-8"?>
<extension xmlns="http://jcp.org/jsr/198/extension-manifest" id="io.github.sqldevgrid.result-grid-font" version="0.12.0" esdk-version="1.0">
  <name>SQL Developer Configurator</name><owner>SQL Developer Configurator contributors</owner>
  <feature-member id="sqldeveloper" xmlns="http://xmlns.oracle.com/ide/extension"/>
  <hooks><jdeveloper-hook xmlns="http://xmlns.oracle.com/jdeveloper/1013/extension"><addins><addin>io.github.sqldevgrid.ResultGridFontAddin</addin></addins></jdeveloper-hook></hooks>
</extension>
'@
$null = [xml]$descriptor
[IO.File]::WriteAllText((Join-Path $classes 'META-INF\extension.xml'),$descriptor,$utf8)
& $tools.Jar '--create' '--file' (Join-Path $output 'result-grid-font-extension.jar') '--manifest' (Join-Path $output 'extension.mf') '-C' $classes '.'
Assert-Exit 'Java extension packaging'
& $tools.Javac '-encoding' 'UTF-8' '-source' '17' '-target' '17' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' '-d' $patcher (Join-Path $PSScriptRoot 'src\java\patcher\EditorPatcher.java')
Assert-Exit 'Editor patcher compilation'
& $tools.Jar '--create' '--file' (Join-Path $output 'font50-patcher.jar') '-C' $patcher '.'
Assert-Exit 'Editor patcher packaging'
if ($JavaOnly) { return }

$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$csc = Join-Path $framework 'csc.exe'
if (!(Test-Path -LiteralPath $csc)) { throw '.NET Framework 4.8 compiler not found.' }
& $csc '/nologo' '/target:exe' '/codepage:65001' '/reference:System.Drawing.dll' ('/out:'+(Join-Path $output 'MakeIcon.exe')) (Join-Path $PSScriptRoot 'src\wpf\MakeIcon.cs')
Assert-Exit 'Icon compiler'
& (Join-Path $output 'MakeIcon.exe') $output
Assert-Exit 'Icon generation'
$references = @('System.dll','System.Core.dll','System.Xml.dll','System.Web.Extensions.dll','System.IO.Compression.dll','System.IO.Compression.FileSystem.dll','WPF\WindowsBase.dll','WPF\PresentationCore.dll','WPF\PresentationFramework.dll','System.Xaml.dll') | ForEach-Object { '/reference:'+(Join-Path $framework $_) }
$source = Join-Path $PSScriptRoot 'src\wpf'
$binary = Join-Path $dist 'SQLDeveloperConfigurator.exe'
& $csc '/nologo' '/target:winexe' '/platform:anycpu' '/codepage:65001' '/optimize+' ('/win32icon:'+(Join-Path $output 'Font50.ico')) ('/win32manifest:'+(Join-Path $source 'app.manifest')) ('/out:'+$binary) @references ('/resource:'+(Join-Path $output 'Font50.ico')+',Font50.ico') ('/resource:'+(Join-Path $source 'MainWindow.xaml')+',MainWindow.xaml') ('/resource:'+(Join-Path $output 'font50-patcher.jar')+',font50-patcher.jar') ('/resource:'+(Join-Path $output 'result-grid-font-extension.jar')+',result-grid-font-extension.jar') (Join-Path $source 'Engine.cs') (Join-Path $source 'App.cs') (Join-Path $source 'AssemblyInfo.cs')
Assert-Exit 'WPF application compilation'
$hash = (Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $dist 'SHA256SUMS.txt'),$hash+'  SQLDeveloperConfigurator.exe'+[Environment]::NewLine,$utf8)
Write-Output "Built: $binary"
