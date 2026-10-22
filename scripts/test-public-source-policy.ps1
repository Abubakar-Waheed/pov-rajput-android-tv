$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'public-source-files.ps1')
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$toolRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.tooling'))
$fixture = Join-Path $toolRoot ('public-source-policy-fixture-' + [Guid]::NewGuid().ToString('N'))
$outside = $fixture + '-outside'
$link = Join-Path $fixture 'docs\escape'
$checks = 0

function Assert-FixturePath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($toolRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        ($full -ne $fixture -and -not $full.StartsWith($fixture + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and $full -ne $outside)) {
        throw 'Fixture cleanup path escaped its dedicated .tooling directory.'
    }
}

try {
    New-Item -ItemType Directory -Path $fixture, $outside -Force | Out-Null
    foreach ($folder in @('app\src', 'scripts', '.github', 'docs', 'design')) {
        New-Item -ItemType Directory -Path (Join-Path $fixture $folder) -Force | Out-Null
    }
    foreach ($relative in @('.gitignore', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'app\build.gradle.kts', 'LICENSE', 'README.md', 'app\src\example.kt')) {
        Set-Content -LiteralPath (Join-Path $fixture $relative) -Value 'public fixture'
    }
    $files = @(Get-PovPublicSourceFiles -ProjectRoot $fixture)
    if ($files.Count -ne 8 -or @($files.RelativePath | Sort-Object -Unique).Count -ne 8) { throw 'Clean source fixture did not produce the expected unique allowlist.' }
    $checks++
    foreach ($relative in @('docs\.env', 'design\.env.preview', 'scripts\debug.log', 'app\src\private.keystore', 'docs\local.properties',
        'docs\private.pem', 'scripts\key.properties', 'app\src\google-services.json', 'docs\pov_accounts_encrypted.xml')) {
        $candidate = Join-Path $fixture $relative
        Assert-FixturePath $candidate
        Set-Content -LiteralPath $candidate -Value 'synthetic fixture'
        try {
            $rejected = $false
            try { [void](Get-PovPublicSourceFiles -ProjectRoot $fixture) } catch { $rejected = $true }
            if (-not $rejected) { throw 'A prohibited fixture file was accepted.' }
            $checks++
        } finally { Remove-Item -LiteralPath $candidate -Force }
    }
    foreach ($relative in @('docs\cache', 'docs\generated', 'scripts\build', 'docs\shared_prefs')) {
        $candidate = Join-Path $fixture $relative
        Assert-FixturePath $candidate
        New-Item -ItemType Directory -Path $candidate | Out-Null
        try {
            $rejected = $false
            try { [void](Get-PovPublicSourceFiles -ProjectRoot $fixture) } catch { $rejected = $true }
            if (-not $rejected) { throw 'A prohibited fixture directory was accepted.' }
            $checks++
        } finally { Remove-Item -LiteralPath $candidate -Force }
    }
    # The target is another new synthetic directory inside .tooling, never user data.
    New-Item -ItemType Junction -Path $link -Target $outside | Out-Null
    $rejected = $false
    try { [void](Get-PovPublicSourceFiles -ProjectRoot $fixture) } catch { $rejected = $true }
    if (-not $rejected) { throw 'A junction escaping the fixture root was accepted.' }
    $checks++
    [pscustomobject]@{ Passed = $true; Checks = $checks; ExportOrUploadPerformed = $false }
} finally {
    # Remove the junction itself before considering a recursive fixture cleanup.
    if (Test-Path -LiteralPath $link) { Assert-FixturePath $link; Remove-Item -LiteralPath $link -Force }
    foreach ($path in @($fixture, $outside)) {
        if (Test-Path -LiteralPath $path) { Assert-FixturePath $path; Remove-Item -LiteralPath $path -Recurse -Force }
    }
}
