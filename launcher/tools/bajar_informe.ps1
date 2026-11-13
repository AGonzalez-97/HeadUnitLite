# Baja a la PC los informes de la Prueba completa que mando la radio (v0.81.23).
#
# La radio los sube a un canal privado de ntfy (el canal de destinos + "-informes"). Los archivos
# duran unas 3 horas en el servidor: hay que correr esto dentro de ese tiempo.
#
# Uso:  .\tools\bajar_informe.ps1            (busca los de las ultimas 12 horas)
#       .\tools\bajar_informe.ps1 -Desde 3h
#
# Quedan en la carpeta "informes" del repo (ignorada por git). El nombre del canal se lee de
# secrets.properties y no se muestra.

param([string]$Desde = "12h")

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$secrets = Join-Path $repo "secrets.properties"
if (-not (Test-Path $secrets)) { Write-Host "Falta secrets.properties"; exit 1 }

$line = Get-Content $secrets | Where-Object { $_ -match '^NTFY_TOPIC=' } | Select-Object -First 1
$topic = if ($line) { ($line -replace '^NTFY_TOPIC=', '').Trim() } else { "" }
if (-not $topic) { Write-Host "secrets.properties no tiene NTFY_TOPIC"; exit 1 }

$dir = Join-Path $repo "informes"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$url = "https://ntfy.sh/" + [uri]::EscapeDataString($topic + "-informes") + "/json?poll=1&since=" + $Desde
try {
    $raw = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30).Content
} catch {
    Write-Host "No se pudo consultar el canal (sin internet?)"
    exit 1
}
if ($raw -is [byte[]]) { $raw = [Text.Encoding]::UTF8.GetString($raw) }

$nuevos = 0
$vencidos = 0
foreach ($l in ($raw -split "`n")) {
    if (-not $l.Trim()) { continue }
    $ev = $l | ConvertFrom-Json
    if ($ev.event -ne "message" -or -not $ev.attachment) { continue }
    # Solo el nombre del archivo, sin carpetas: lo que llega de internet no elige donde se guarda
    $name = [IO.Path]::GetFileName([string]$ev.attachment.name)
    if ($name -notmatch '^onix_informe_[0-9A-Za-z_\-]+\.txt$') { continue }
    $dest = Join-Path $dir $name
    if (Test-Path $dest) { continue }
    try {
        Invoke-WebRequest -Uri $ev.attachment.url -UseBasicParsing -TimeoutSec 60 -OutFile $dest
        Write-Host "Bajado: informes\$name"
        $nuevos++
    } catch {
        $vencidos++
    }
}

if ($nuevos -eq 0) { Write-Host "No hay informes nuevos." }
if ($vencidos -gt 0) { Write-Host "$vencidos informe(s) ya vencieron en el servidor (duran unas 3 horas)." }
