param([string]$OutputPath)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$OutputPath) { $OutputPath = Join-Path $repo 'dist\SQLDeveloperConfigurator-source.zip' }
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Path (Split-Path $OutputPath -Parent) -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath $OutputPath) { Remove-Item -LiteralPath $OutputPath }
$allowedRoot = @('README.md','LICENSE','THIRD_PARTY_NOTICES.md','.gitignore','.gitattributes','.editorconfig','build.ps1','test.ps1')
$files = @(Get-ChildItem -LiteralPath $repo -File | Where-Object { $_.Name -in $allowedRoot })
foreach ($folder in @('src','tests','scripts','docs')) {
    $files += Get-ChildItem -LiteralPath (Join-Path $repo $folder) -File -Recurse | Where-Object {
        $_.Extension -in @('.cs','.java','.xaml','.manifest','.ps1','.md','.sql','.png')
    }
}
$archive = [IO.Compression.ZipFile]::Open($OutputPath,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($repo.Length).TrimStart('\').Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,'SQLDeveloperConfigurator/'+$relative) | Out-Null
    }
} finally { $archive.Dispose() }
Write-Output "Source archive: $OutputPath ($($files.Count) files)"
