"""Resume el reporte XML de Android Lint: problemas por tipo y severidad.

Destaca NewApi / InlinedApi (APIs más nuevas que minSdk 24 = Android 7.0/7.1).
Uso: python3 lint_summary.py app/build/reports/lint-results-debug.xml
"""
import collections
import os
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1] if len(sys.argv) > 1 else "app/build/reports/lint-results-debug.xml"
print("## Lint (Android)\n")
if not os.path.exists(path):
    print("Sin reporte de lint: el paso no se ejecutó o falló antes de generarlo.")
    sys.exit(0)

issues = ET.parse(path).getroot().findall("issue")
count = collections.Counter((i.get("severity"), i.get("id")) for i in issues)
print("| Severidad | Tipo | Cantidad |")
print("|---|---|---|")
for (sev, iid), n in sorted(count.items(), key=lambda x: (x[0][0] != "Fatal", x[0][0] != "Error", -x[1])):
    print(f"| {sev} | {iid} | {n} |")
print()
compat = [i for i in issues if i.get("id") in ("NewApi", "InlinedApi")]
print(f"Problemas totales: {len(issues)}")
print(f"Compatibilidad con Android 7 (NewApi/InlinedApi): {len(compat)}")
for i in compat[:20]:
    loc = i.find("location")
    where = f"{os.path.basename(loc.get('file', '?'))}:{loc.get('line', '?')}" if loc is not None else "?"
    print(f"- {i.get('id')} en {where}: {i.get('message')}")

# AUD-059 (v0.64.1): los errores y los avisos de seguridad con su archivo y línea, para leerlos
# desde el log de la CI (el reporte XML no se puede bajar desde las sesiones de Claude).
SECURITY = {"TrustAllX509TrustManager", "BadHostnameVerifier", "SSLCertificateSocketFactoryGetInsecure",
            "ExportedService", "ExportedReceiver", "InsecureBaseConfiguration", "SetJavaScriptEnabled"}
detail = [i for i in issues if i.get("severity") in ("Fatal", "Error") or i.get("id") in SECURITY]
if detail:
    print()
    print("Errores y avisos de seguridad:")
for i in detail[:40]:
    loc = i.find("location")
    where = "?"
    if loc is not None:
        f = loc.get("file", "?")
        f = f.split("/onix-launcher/", 1)[-1] if "/onix-launcher/" in f else f.split("/caches/", 1)[-1]
        where = f"{f}:{loc.get('line', '?')}"
    print(f"- [{i.get('severity')}] {i.get('id')} en {where}: {i.get('message')}")
