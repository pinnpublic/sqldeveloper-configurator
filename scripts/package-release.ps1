param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $repo 'dist'
$binary = Join-Path $dist 'SQLDeveloperConfigurator.exe'
if (!(Test-Path -LiteralPath $binary)) { throw 'Run build.ps1 first.' }
$version = (((Get-Item -LiteralPath $binary).VersionInfo.FileVersion).Split('.') | Select-Object -First 3) -join '.'
$path = Join-Path $dist ('SQLDeveloperConfigurator-'+$version+'-win.zip')
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path }
$zip = [IO.Compression.ZipFile]::Open($path,[IO.Compression.ZipArchiveMode]::Create)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$binary,'SQLDeveloperConfigurator.exe') | Out-Null
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $repo 'LICENSE'),'LICENSE') | Out-Null
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $repo 'THIRD_PARTY_NOTICES.md'),'THIRD_PARTY_NOTICES.md') | Out-Null
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $repo 'docs\QUICKSTART.md'),'QUICKSTART.md') | Out-Null
} finally { $zip.Dispose() }
$lines = foreach ($file in @($binary,$path)) { (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+[IO.Path]::GetFileName($file) }
[IO.File]::WriteAllText((Join-Path $dist 'SHA256SUMS.txt'),($lines -join [Environment]::NewLine)+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
Write-Output "Release archive: $path"
