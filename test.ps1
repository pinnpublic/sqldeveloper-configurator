param(
    [Parameter(Mandatory=$true)][string]$SqlDeveloperPath,
    [string]$JdkPath,
    [switch]$NoBuild
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
$sdk = Get-SqlDeveloperHome $SqlDeveloperPath
$tools = Get-Toolchain $JdkPath
if (!$NoBuild) { & (Join-Path $PSScriptRoot 'build.ps1') -SqlDeveloperPath $sdk -JdkPath $tools.Home }
$output = Join-Path $PSScriptRoot 'out'
$classes = Join-Path $output 'tests'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$extension = Join-Path $output 'result-grid-font-extension.jar'
$patcher = Join-Path $output 'font50-patcher.jar'
$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$references = @('System.dll','System.Core.dll','System.Xml.dll','System.Web.Extensions.dll','System.IO.Compression.dll','System.IO.Compression.FileSystem.dll') | ForEach-Object { '/reference:'+(Join-Path $framework $_) }
& (Join-Path $framework 'csc.exe') '/nologo' '/target:exe' '/codepage:65001' ('/out:'+(Join-Path $output 'EngineTests.exe')) @references ('/resource:'+$patcher+',font50-patcher.jar') ('/resource:'+$extension+',result-grid-font-extension.jar') (Join-Path $PSScriptRoot 'src\wpf\Engine.cs') (Join-Path $PSScriptRoot 'tests\csharp\EngineTests.cs')
Assert-Exit 'Engine test compilation'
$oldJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $tools.Home
    & (Join-Path $output 'EngineTests.exe') $sdk (Join-Path $output ('install-tests-'+(Get-Date -Format 'yyyyMMddHHmmssfff')))
    Assert-Exit 'Backup/install/reapply/restore tests'
} finally { $env:JAVA_HOME = $oldJavaHome }

$oracle = Get-OracleClasspath $sdk
$java = Get-OracleJava $sdk $tools
$cp = $extension+';'+$oracle+';'+$classes
$agent = Join-Path $output 'editor-test-agent.jar'
New-CleanAgent $extension $agent
$compileCp = (Join-Path $output 'extension\editor-bridge.jar')+';'+$cp
$javaSources = @('EditorFeatureTests.java','NativePreferencesTest.java') | ForEach-Object { Join-Path $PSScriptRoot ('tests\java\'+$_) }
$javaSources += Join-Path $PSScriptRoot 'tests\java\io\github\sqldevgrid\GridLayoutTest.java'
$javaSources += Join-Path $PSScriptRoot 'tests\java\io\github\sqldevgrid\NativeFontPreferencesTest.java'
& $tools.Javac '-proc:none' '-encoding' 'UTF-8' '--release' '17' '-cp' $compileCp '-d' $classes @javaSources
Assert-Exit 'Java test compilation'
$oldAppData = $env:APPDATA
try {
    $env:APPDATA = Join-Path $output 'test-appdata'
    & $java '-Djava.awt.headless=true' '-cp' $cp io.github.sqldevgrid.GridLayoutTest (Join-Path $classes 'results-50pt.png')
    Assert-Exit 'Grid layout tests'
    & $java '-Djava.awt.headless=true' ('-javaagent:'+$agent) '-cp' ($oracle+';'+$classes) io.github.sqldevgrid.NativeFontPreferencesTest
    Assert-Exit 'Native font preference and result synchronization tests'
    & $java '-Djava.awt.headless=true' ('-javaagent:'+$agent) '-cp' ($oracle+';'+$classes) EditorFeatureTests $classes
    Assert-Exit 'Native editor tests'
    & $java '-Djava.awt.headless=true' '-cp' $cp NativePreferencesTest $classes
    Assert-Exit 'Native preferences tests'
} finally { $env:APPDATA = $oldAppData }
Write-Output 'All tests passed. The running SQL Developer and its user settings were not changed.'
