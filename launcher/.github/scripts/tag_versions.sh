#!/usr/bin/env bash
# Crea en GitHub el tag de la versión actual (v$APP_VERSION) sobre el commit compilado,
# solo si todavía no existe. Requiere GH_TOKEN con permiso contents: write.
#
# Las versiones anteriores a v0.18.0 no tienen tag vX.Y.Z: el token de Actions no puede
# crear referencias a commits viejos cuyo workflow es distinto al actual (HTTP 403,
# "Resource not accessible by integration"). Cada una ya tiene su tag build-N
# (tabla de equivalencias en CHANGELOG.md / VERSION.md).
set -euo pipefail

tag="v$APP_VERSION"
if gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$tag" --silent 2>/dev/null; then
  echo "= $tag ya existe"
else
  # v0.68.0: si main ya cambió el workflow mientras compilaba (otra versión subida enseguida),
  # GitHub no deja crear el tag (HTTP 403). La release build-N ya quedó publicada: solo se avisa.
  if gh api -X POST "repos/$GITHUB_REPOSITORY/git/refs" -f ref="refs/tags/$tag" -f sha="$GITHUB_SHA" --silent; then
    echo "+ $tag → ${GITHUB_SHA:0:7}"
  else
    echo "::warning::No se pudo crear el tag $tag (la release build-$GITHUB_RUN_NUMBER sí está publicada)"
  fi
fi
