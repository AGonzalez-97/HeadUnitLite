<#
.SYNOPSIS
  Compila y publica la release de Onix Launcher en GitHub Releases directamente desde la PC,
  sin consumir minutos de GitHub Actions.
.DESCRIPTION
  Uso:
    .\tools\publicar.ps1
  El token se puede guardar una sola vez en tools/github_token.txt (ignorado por git)
  o en la variable de entorno UPDATER_TOKEN.
#>
param(
    [string]$Token = $env:UPDATER_TOKEN,
    # Publica el APK ya compilado (tiene que ser de la versión de version.properties)
    [switch]$SkipBuild,
    # Compila sin correr los tests (solo si ya se corrieron para este mismo código)
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path -Parent $PSScriptRoot
Set-Location $rootDir

# 1. Obtener el token de GitHub
$tokenFile = Join-Path $PSScriptRoot "github_token.txt"
if (-not $Token -and (Test-Path $tokenFile)) {
    $Token = (Get-Content $tokenFile -Raw).Trim()
}
if (-not $Token) {
    Write-Host ""
    Write-Host "No se encontró el token de GitHub." -ForegroundColor Yellow
    Write-Host "Pegá tu token de GitHub (con permiso Contents: Read and Write):" -ForegroundColor Cyan
    $Token = Read-Host
    if (-not $Token) {
        Write-Error "Operación cancelada: falta el token de GitHub."
        exit 1
    }
    # Guardar en tools/github_token.txt para no volver a pedirlo
    Set-Content -Path $tokenFile -Value $Token.Trim() -Encoding UTF8
    Write-Host "Token guardado en tools/github_token.txt (está en .gitignore, no se sube al repo)." -ForegroundColor Green
}

# 2. Leer versión actual
$versionLine = Get-Content "version.properties" | Where-Object { $_ -match "^version=" }
$version = ($versionLine -split "=")[1].Trim()
$vTag = "v$version"
Write-Host "Versión a publicar: $vTag" -ForegroundColor Cyan

# 3. Obtener el número de build más alto desde GitHub
$headers = @{
    "Authorization" = "Bearer $Token"
    "Accept"        = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
    "User-Agent"    = "OnixLauncher-Publisher"
}

Write-Host "Consultando releases existentes en GitHub..." -ForegroundColor Cyan
$nextBuild = 168
try {
    $releases = Invoke-RestMethod -Uri "https://api.github.com/repos/AGonzalez-97/onix-launcher/releases?per_page=20" -Headers $headers -Method Get
    $buildNums = @()
    foreach ($rel in $releases) {
        if ($rel.tag_name -match "build-(\d+)") {
            $buildNums += [int]$Matches[1]
        }
    }
    if ($buildNums.Count -gt 0) {
        $maxBuild = ($buildNums | Measure-Object -Maximum).Maximum
        $nextBuild = $maxBuild + 1
    }
} catch {
    Write-Host "Aviso: no se pudo listar releases ($_); usando build-$nextBuild" -ForegroundColor Yellow
}

$buildTag = "build-$nextBuild"
$releaseTitle = "Onix Launcher $vTag ($buildTag)"
Write-Host "Preparando $releaseTitle..." -ForegroundColor Cyan

# 4. Compilar APK si no existe o se pide
$apkPath = Join-Path $rootDir "app\build\outputs\apk\release\app-release.apk"
if (-not $SkipBuild -or -not (Test-Path $apkPath)) {
    # Se respetan JAVA_HOME y ANDROID_HOME si ya están definidos; si no, los de la PC del autor
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot" }
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
    # Los tests van antes de compilar y de crear la release: con una falla no se publica nada.
    # (GitHub Actions hacía este control; publicando desde la PC lo hace este script.)
    if (-not $SkipTests) {
        Write-Host "Corriendo los tests antes de publicar..." -ForegroundColor Cyan
        & .\gradlew.bat :app:testDebugUnitTest --console=plain -q
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Hay tests que fallan: no se publica. Detalle en app\build\reports\tests\testDebugUnitTest\index.html"
            exit 1
        }
        Write-Host "Tests OK." -ForegroundColor Green
    }
    Write-Host "Compilando release optimizada en tu PC para $buildTag..." -ForegroundColor Cyan
    $env:GITHUB_RUN_NUMBER = [string]($nextBuild - 100)
    & .\gradlew.bat :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Fallo la compilación de la app."
        exit 1
    }
}

if (-not (Test-Path $apkPath)) {
    Write-Error "No se encontró el APK en $apkPath"
    exit 1
}

# Con -SkipBuild el APK puede ser de otra versión: se compara con version.properties antes de subirlo
$metaPath = Join-Path (Split-Path -Parent $apkPath) "output-metadata.json"
if (Test-Path $metaPath) {
    $apkVersion = (Get-Content $metaPath -Raw | ConvertFrom-Json).elements[0].versionName
    if ($apkVersion -ne $version) {
        Write-Error "El APK compilado es de la versión $apkVersion y version.properties dice $version. Corré el script sin -SkipBuild."
        exit 1
    }
}

$apkSizeMB = [math]::Round((Get-Item $apkPath).Length / 1MB, 1)
Write-Host "APK listo: $apkPath ($apkSizeMB MB)" -ForegroundColor Green

# 5. Notas de la versión
$notesPath = Join-Path $rootDir "docs\releases\$vTag.md"
[string]$bodyText = if (Test-Path $notesPath) {
    [System.IO.File]::ReadAllText($notesPath, [System.Text.Encoding]::UTF8)
} else {
    "Actualización de Onix Launcher $vTag ($buildTag)."
}

# 6. Crear Release en GitHub
$createBody = @{
    tag_name   = [string]$buildTag
    name       = [string]$releaseTitle
    body       = [string]$bodyText
    draft      = $false
    prerelease = $false
} | ConvertTo-Json -Compress

$createUrl = "https://api.github.com/repos/AGonzalez-97/onix-launcher/releases"
$newRelease = Invoke-RestMethod -Uri $createUrl -Headers $headers -Method Post -Body $createBody -ContentType "application/json; charset=utf-8"
$releaseId = $newRelease.id
Write-Host "Release creada con éxito (ID: $releaseId, Tag: $buildTag)" -ForegroundColor Green

# 7. Subir el APK como asset
Write-Host "Subiendo onix-launcher.apk a la release..." -ForegroundColor Cyan
$uploadUrl = "https://uploads.github.com/repos/AGonzalez-97/onix-launcher/releases/$releaseId/assets?name=onix-launcher.apk"
$uploadHeaders = @{
    "Authorization" = "Bearer $Token"
    "Accept"        = "application/vnd.github+json"
    "Content-Type"  = "application/vnd.android.package-archive"
    "User-Agent"    = "OnixLauncher-Publisher"
}

$apkBytes = [System.IO.File]::ReadAllBytes($apkPath)
$uploadResp = Invoke-RestMethod -Uri $uploadUrl -Headers $uploadHeaders -Method Post -Body $apkBytes
Write-Host "APK subido correctamente: $($uploadResp.browser_download_url)" -ForegroundColor Green

# 8. También crear o actualizar tag vX.Y.Z
try {
    $commitSha = (git rev-parse HEAD).Trim()
    $tagBody = @{
        ref = "refs/tags/$vTag"
        sha = $commitSha
    } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "https://api.github.com/repos/AGonzalez-97/onix-launcher/git/refs" -Headers $headers -Method Post -Body $tagBody -ContentType "application/json" | Out-Null
    Write-Host "Tag $vTag apuntado al commit $commitSha" -ForegroundColor Green
} catch {
    # Si ya existe, no es un error
    Write-Host "Tag $vTag ya existía o no requirió cambios." -ForegroundColor Gray
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  ¡LISTO! Versión $vTag ($buildTag) publicada en GitHub" -ForegroundColor Green
Write-Host "  Tu radio ya puede detectarla en Ajustes -> Sistema!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

# Liberar memoria RAM cerrando los daemons de Gradle
try {
    & .\gradlew.bat --stop | Out-Null
    Write-Host "Memoria RAM de Java liberada exitosamente." -ForegroundColor Gray
} catch {}
