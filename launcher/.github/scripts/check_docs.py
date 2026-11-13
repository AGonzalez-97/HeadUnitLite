"""Regla de oro: la documentación acompaña al código en el mismo cambio.

Falla (exit 1) si:
- la versión de version.properties no está en CHANGELOG.md, VERSION.md, el README
  ni tiene sus notas en docs/releases/vX.Y.Z.md;
- un archivo Kotlin de la app no figura en ARCHITECTURE.md y en README.md;
- un permiso del manifiesto no está explicado en README.md;
- ARCHITECTURE.md no dice la versión actual.
Uso: python3 .github/scripts/check_docs.py  (desde la raíz del repo)
"""
import glob
import os
import re
import sys

errors = []


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


version = re.search(r"^version=(\S+)", read("version.properties"), re.M).group(1)
v = "v" + version

changelog = read("CHANGELOG.md")
if f"## [{v}]" not in changelog:
    errors.append(f"CHANGELOG.md no tiene la sección ## [{v}]")
if f"**Versión:** {v}" not in read("VERSION.md"):
    errors.append(f"VERSION.md no dice **Versión:** {v}")
readme = read("README.md")
if f"versión-{version}-" not in readme or f"`{version}`" not in readme:
    errors.append(f"README.md no muestra la versión {version} (insignia y tabla)")
if not os.path.exists(f"docs/releases/{v}.md"):
    errors.append(f"faltan las notas de la versión: docs/releases/{v}.md")

architecture = read("ARCHITECTURE.md")
if f"**Versión documentada:** {v}" not in architecture:
    errors.append(f"ARCHITECTURE.md no está al día (**Versión documentada:** {v})")

for path in sorted(glob.glob("app/src/main/java/**/*.kt", recursive=True)):
    name = os.path.basename(path)
    if name not in architecture:
        errors.append(f"ARCHITECTURE.md no menciona {name}")
    if name not in readme:
        errors.append(f"README.md no menciona {name} (tabla de módulos)")

manifest = read("app/src/main/AndroidManifest.xml")
for perm in re.findall(r'uses-permission android:name="android\.permission\.([A-Z_]+)"', manifest):
    if perm not in readme:
        errors.append(f"README.md no explica el permiso {perm}")

if errors:
    print("Documentación desactualizada (regla de oro, ver CLAUDE.md):")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print(f"Documentación al día para {v}.")
