#!/usr/bin/env bash
# Arma la descripción de una release de GitHub (regla de oro 3): la nota de la versión
# (docs/releases/vVERSION.md, escrita para quien lea la release, sin hablarle al autor)
# más el resultado real de la compilación.
# Uso: release_notes.sh VERSION BUILD RESULTADOS > notas.md
#   RESULTADOS: archivo con las líneas "- Tests ejecutados: …", "- Exitosos: …", etc.
set -euo pipefail
version="$1"; build="$2"; results="$3"
notas="docs/releases/v${version}.md"
if [ -f "$notas" ]; then
  sed -e "s/se completa al publicar/${build}/" "$notas" | grep -v '^# Release'
else
  echo "Sin notas para v${version} (falta $notas)."
fi
echo
echo "## Resultado de la compilación"
echo
cat "$results"
echo
echo "**Instalación:** descargar \`onix-launcher.apk\` (abajo) e instalarlo sobre la versión anterior, sin desinstalar. Guía de pruebas: \`docs/INSTRUCTIVO.md\`."
