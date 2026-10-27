Set-StrictMode -Version Latest

function Get-Toolchain([string]$JdkPath) {
    if ($JdkPath) {
        $homePath = (Resolve-Path -LiteralPath $JdkPath).Path
        $javacPath = Join-Path $homePath 'bin\javac.exe'
    } elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        $homePath = $env:JAVA_HOME
        $javacPath = Join-Path $homePath 'bin\javac.exe'
    } else {
        $javacPath = (Get-Command javac.exe -ErrorAction Stop).Source
        $homePath = Split-Path (Split-Path $javacPath -Parent) -Parent
    }
    $toolchain = @{ Home = $homePath; Javac = $javacPath; Java = (Join-Path $homePath 'bin\java.exe'); Jar = (Join-Path $homePath 'bin\jar.exe') }
    foreach ($name in @('Javac','Java','Jar')) {
        if (!(Test-Path -LiteralPath $toolchain[$name])) { throw "JDK tool not found: $name. Set -JdkPath to a JDK 17+ installation." }
    }
    return $toolchain
}

function Get-SqlDeveloperHome([string]$Path) {
    $homePath = (Resolve-Path -LiteralPath $Path).Path
    foreach ($relative in @('ide\extensions\oracle.ide.jar','ide\extensions\oracle.ide.ceditor.jar','ide\lib\idert.jar','ide\lib\javatools.jar','sqldeveloper\bin\version.properties')) {
        if (!(Test-Path -LiteralPath (Join-Path $homePath $relative))) { throw "SQL Developer dependency missing: $relative" }
    }
    $version = Get-Content -LiteralPath (Join-Path $homePath 'sqldeveloper\bin\version.properties') -Encoding UTF8 -Raw
    if ($version -notmatch '(?m)^VER_FULL=26\.2\.0\.186\.2220\s*$') { throw 'Supported SQL Developer build: 26.2.0.186.2220' }
    return $homePath
}

function Get-OracleClasspath([string]$HomePath) {
    $jars = foreach ($folder in @('ide\extensions','ide\lib','jlib','jdev\lib','modules','external','jdbc','sqldeveloper\lib','sqldeveloper\extensions')) {
        Get-ChildItem -LiteralPath (Join-Path $HomePath $folder) -Recurse -File -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike 'result-grid-font-*.jar' -and $_.FullName -notmatch '\\javafx\\' } |
            Select-Object -ExpandProperty FullName
    }
    return ($jars -join ';')
}

function Get-OracleJava([string]$HomePath, $Toolchain) {
    foreach ($relative in @('jdk\jre\bin\java.exe','jdk\bin\java.exe')) {
        $candidate = Join-Path $HomePath $relative
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $Toolchain.Java
}

function Assert-Exit([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed (exit code $LASTEXITCODE)." }
}

function New-CleanAgent([string]$Extension, [string]$OutputPath) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path -LiteralPath $OutputPath) { Remove-Item -LiteralPath $OutputPath }
    $inputJar = [IO.Compression.ZipFile]::OpenRead($Extension)
    $outputJar = [IO.Compression.ZipFile]::Open($OutputPath,[IO.Compression.ZipArchiveMode]::Create)
    try {
        $writer = [IO.StreamWriter]::new($outputJar.CreateEntry('META-INF/MANIFEST.MF').Open(),[Text.UTF8Encoding]::new($false))
        $writer.Write("Manifest-Version: 1.0`r`nPremain-Class: io.github.sqldevgrid.BootstrapAgent`r`nCan-Redefine-Classes: true`r`n`r`n"); $writer.Dispose()
        foreach ($entry in $inputJar.Entries) {
            if ($entry.FullName.StartsWith('io/github/sqldevgrid/EditorPreferencesPanel')) { continue }
            if ($entry.FullName.EndsWith('.class') -or $entry.FullName -eq 'editor-bridge.jar') {
                $read = $entry.Open(); $write = $outputJar.CreateEntry($entry.FullName).Open()
                try { $read.CopyTo($write) } finally { $read.Dispose(); $write.Dispose() }
            }
        }
    } finally { $inputJar.Dispose(); $outputJar.Dispose() }
}
