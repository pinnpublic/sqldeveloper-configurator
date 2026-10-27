param(
    [Parameter(Mandatory=$true)][string]$SqlDeveloperPath,
    [Parameter(Mandatory=$true)][int]$SqlDeveloperProcessId,
    [string]$JdkPath
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Common.ps1')
$sdk = Get-SqlDeveloperHome $SqlDeveloperPath
$tools = Get-Toolchain $JdkPath
$app = Get-Process -Id $SqlDeveloperProcessId -ErrorAction Stop
if ($app.Path -notlike ($sdk.TrimEnd('\')+'\*')) { throw 'The process is not from this SQL Developer installation.' }
$classes = Join-Path $repo 'out\live-verification'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& $tools.Javac '-encoding' 'UTF-8' '--release' '17' '-d' $classes (Join-Path $repo 'tests\java\ActualPreferencesTreeProbe.java') (Join-Path $repo 'tests\java\AttachDocumentation.java')
Assert-Exit 'Live verifier compilation'
$manifest = Join-Path $classes 'agent.mf'
[IO.File]::WriteAllText($manifest,"Manifest-Version: 1.0`r`nAgent-Class: ActualPreferencesTreeProbe`r`n`r`n",[Text.UTF8Encoding]::new($false))
$agent = Join-Path $classes 'verifier.jar'
& $tools.Jar '--create' '--file' $agent '--manifest' $manifest '-C' $classes 'ActualPreferencesTreeProbe.class'
Assert-Exit 'Live verifier packaging'
$result = Join-Path $classes 'result.txt'
if (Test-Path -LiteralPath $result) { Remove-Item -LiteralPath $result }
& $tools.Java '-cp' $classes AttachDocumentation ([string]$SqlDeveloperProcessId) $agent $result
Assert-Exit 'Live verifier attachment'
$deadline = (Get-Date).AddSeconds(30)
while (!(Test-Path -LiteralPath $result) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
if (!(Test-Path -LiteralPath $result)) { throw 'Live verification timed out.' }
$report = Get-Content -LiteralPath $result -Encoding UTF8 -Raw
Write-Output $report
if (!$report.Contains('PASS ACTUAL ORACLE LEFT TREE')) { throw 'Native preference tree verification failed.' }
