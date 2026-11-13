"""Resume los resultados JUnit (XML) de Gradle: totales por clase y en general.

Uso: python3 test_summary.py DIR [DIR ...]
Si una misma clase aparece en varias carpetas, vale la última.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

roots = sys.argv[1:] or ["app/build/test-results"]
by_class = {}
for root in roots:
    for path in sorted(glob.glob(os.path.join(root, "**", "TEST-*.xml"), recursive=True)):
        s = ET.parse(path).getroot()
        total = int(s.get("tests", 0))
        failed = int(s.get("failures", 0)) + int(s.get("errors", 0))
        skipped = int(s.get("skipped", 0))
        name = s.get("name", "?")
        by_class[name] = (name.split(".")[-1], total, total - failed - skipped, failed, skipped)
rows = [by_class[k] for k in sorted(by_class)]

t = sum(r[1] for r in rows)
ok = sum(r[2] for r in rows)
bad = sum(r[3] for r in rows)
skip = sum(r[4] for r in rows)

print("## Tests\n")
print("| Clase | Total | Exitosos | Fallidos | Omitidos |")
print("|---|---|---|---|---|")
for r in rows:
    print(f"| {r[0]} | {r[1]} | {r[2]} | {r[3]} | {r[4]} |")
print(f"| **Total** | **{t}** | **{ok}** | **{bad}** | **{skip}** |")
print()
print(f"Tests ejecutados: {t}")
print(f"Exitosos: {ok}")
print(f"Fallidos: {bad}")
# Cobertura (v0.63.0): report.xml de JaCoCo de los tests de lógica, si está
cov = None
for root in roots:
    for path in glob.glob(os.path.join(root, "**", "report.xml"), recursive=True):
        try:
            r = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for c in r.findall("counter"):
            if c.get("type") == "LINE":
                missed, covered = int(c.get("missed", 0)), int(c.get("covered", 0))
                if missed + covered:
                    cov = (covered, missed + covered)
if cov:
    print(f"Cobertura: {100 * cov[0] / cov[1]:.1f} % de las líneas ({cov[0]} de {cov[1]}, tests de lógica; sin las capturas ni el motor de CarLink)")
else:
    print("Cobertura: no medida")
print(f"Estado: {'OK' if t > 0 and bad == 0 else ('SIN RESULTADOS' if t == 0 else 'CON FALLAS')}")
