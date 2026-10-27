param(
    [Parameter(Mandatory=$true)][string]$SqlDeveloperPath,
    [string]$JdkPath,
    [int]$SqlDeveloperProcessId = 0,
    [switch]$NoBuild
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Common.ps1')
$sdk = Get-SqlDeveloperHome $SqlDeveloperPath
$tools = Get-Toolchain $JdkPath
if (!$NoBuild) { & (Join-Path $repo 'build.ps1') -SqlDeveloperPath $sdk -JdkPath $tools.Home }
$output = Join-Path $repo 'out'
$images = Join-Path $repo 'docs\images'
$classes = Join-Path $output 'documentation-classes'
foreach ($directory in @($images,$classes)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
$oracle = Get-OracleClasspath $sdk
$extension = Join-Path $output 'result-grid-font-extension.jar'
$cp = (Join-Path $output 'extension\editor-bridge.jar')+';'+$extension+';'+$oracle+';'+$classes
$sources = @('tests\java\DocumentationViews.java','tests\java\DocumentationAgent.java','tests\java\AttachDocumentation.java','tests\java\io\github\sqldevgrid\DocumentationOracleGrid.java') | ForEach-Object { Join-Path $repo $_ }
& $tools.Javac '-proc:none' '-encoding' 'UTF-8' '--release' '17' '-cp' $cp '-d' $classes @sources
Assert-Exit 'Documentation renderer compilation'
$agent = Join-Path $output 'documentation-editor-agent.jar'
New-CleanAgent $extension $agent
$java = Get-OracleJava $sdk $tools
$oldAppData = $env:APPDATA
try {
    $env:APPDATA = Join-Path $output 'documentation-appdata'
    & $java '-Djava.awt.headless=true' '-Dswing.aatext=true' '-Dawt.useSystemAAFontSettings=on' ('-javaagent:'+$agent) '-cp' $cp DocumentationViews $images
    Assert-Exit 'Documentation views'
} finally { $env:APPDATA = $oldAppData }
$start = [Diagnostics.ProcessStartInfo]::new((Join-Path $repo 'dist\SQLDeveloperConfigurator.exe'))
$start.Arguments = '--smoke-test "'+(Join-Path $images 'configurator.png')+'" "'+(Join-Path $output 'documentation-wpf-appdata')+'"'
$start.UseShellExecute = $false; $start.CreateNoWindow = $true
$process = [Diagnostics.Process]::Start($start)
if (!$process.WaitForExit(60000)) { throw 'WPF render did not finish within 60 seconds.' }
if ($process.ExitCode -ne 0) { throw 'WPF render failed; inspect docs/images/configurator.png.txt.' }
Copy-Item -LiteralPath (Join-Path $output 'Font50.png') -Destination (Join-Path $images 'icon.png') -Force
if ($SqlDeveloperProcessId -gt 0) {
    $app = Get-Process -Id $SqlDeveloperProcessId -ErrorAction Stop
    if ($app.Path -notlike ($sdk.TrimEnd('\')+'\*')) { throw 'The specified process is not from this SQL Developer installation.' }
    $manifest = Join-Path $output 'documentation-agent.mf'
    [IO.File]::WriteAllText($manifest,"Manifest-Version: 1.0`r`nAgent-Class: DocumentationAgent`r`n`r`n",[Text.UTF8Encoding]::new($false))
    $renderAgent = Join-Path $output 'documentation-agent.jar'
    & $tools.Jar '--create' '--file' $renderAgent '--manifest' $manifest '-C' $classes 'DocumentationAgent.class'
    Assert-Exit 'Documentation agent packaging'
    $result = Join-Path $images '.oracle-render-result.txt'
    if (Test-Path -LiteralPath $result) { Remove-Item -LiteralPath $result }
    & $tools.Java '-cp' $classes AttachDocumentation ([string]$SqlDeveloperProcessId) $renderAgent ($classes+'|'+$images)
    Assert-Exit 'Isolated Oracle renderer attachment'
    $deadline = (Get-Date).AddSeconds(30)
    while (!(Test-Path -LiteralPath $result) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
    if (!(Test-Path -LiteralPath $result)) { throw 'Oracle renderer did not produce a result.' }
    $report = Get-Content -LiteralPath $result -Encoding UTF8 -Raw
    if (!$report.StartsWith('PASS')) { throw $report }
    Write-Output $report
} else {
    Write-Output 'Oracle result-grid image was not regenerated. Supply -SqlDeveloperProcessId to render its in-memory demo inside a running SQL Developer.'
}
Write-Output "Documentation images: $images"
