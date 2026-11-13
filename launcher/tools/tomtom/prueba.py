"""Prueba de búsqueda y rutas con TomTom (v0.75.0, pedido del autor). Corre en la CI con el secreto
TOMTOM_API_KEY; nunca imprime la clave. Compara con Nominatim y Photon (lo que usa Onix hoy).

python3 tools/tomtom/prueba.py "9 de julio 550, Tafí Viejo" "Av. Adolfo de la Vega 473"
"""
import json
import os
import sys
import urllib.parse
import urllib.request

KEY = os.environ.get("TOMTOM_API_KEY", "").strip()
LAT, LON = -26.8303, -65.2038   # Plaza Independencia, San Miguel de Tucumán
UA = {"User-Agent": "OnixLauncher-prueba/1.0"}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return {"_error": f"HTTP {e.code} {e.read()[:200].decode(errors='replace')}"}
    except Exception as e:
        return {"_error": str(e)}


def tomtom(kind, q):
    qs = urllib.parse.urlencode({"key": KEY, "countrySet": "AR", "lat": LAT, "lon": LON, "limit": 5,
                                 "language": "es-419", "radius": 60000} if kind == "search" else
                                {"key": KEY, "countrySet": "AR", "lat": LAT, "lon": LON, "limit": 5, "language": "es-419"})
    d = get(f"https://api.tomtom.com/search/2/{kind}/{urllib.parse.quote(q)}.json?{qs}")
    if "_error" in d:
        return [d["_error"]], None
    out, first = [], None
    for r in d.get("results", []):
        a = r.get("address", {})
        p = r.get("position", {})
        out.append(f"{r.get('type')} · {a.get('freeformAddress')} · ({p.get('lat'):.5f}, {p.get('lon'):.5f}) · coincidencia {r.get('matchConfidence', {}).get('score', '?')}")
        first = first or p
    return out or ["(sin resultados)"], first


def route(p):
    path = f"{LAT},{LON}:{p['lat']},{p['lon']}"
    d = get(f"https://api.tomtom.com/routing/1/calculateRoute/{path}/json?" +
            urllib.parse.urlencode({"key": KEY, "traffic": "true", "travelMode": "car", "language": "es-AR", "instructionsType": "text"}))
    if "_error" in d:
        return d["_error"]
    s = d["routes"][0]["summary"]
    n = len(d["routes"][0].get("guidance", {}).get("instructions", []))
    return f"{s['lengthInMeters'] / 1000:.1f} km · {s['travelTimeInSeconds'] // 60} min (con tránsito, demora {s.get('trafficDelayInSeconds', 0) // 60} min) · {n} indicaciones"


def osm(q):
    n = get("https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode(
        {"format": "jsonv2", "limit": 3, "countrycodes": "ar", "q": q}))
    ph = get("https://photon.komoot.io/api/?" + urllib.parse.urlencode({"q": q, "limit": 3, "lat": LAT, "lon": LON}))
    nn = [x.get("display_name", "")[:90] for x in n] if isinstance(n, list) else [str(n.get("_error"))]
    pp = [", ".join(str(f["properties"].get(k, "")) for k in ("name", "street", "housenumber", "city") if f["properties"].get(k))
          for f in ph.get("features", [])] if isinstance(ph, dict) and "features" in ph else [str(ph.get("_error"))]
    return nn or ["(sin resultados)"], pp or ["(sin resultados)"]


def main(queries):
    if not KEY:
        print("::error::Falta el secreto TOMTOM_API_KEY")
        return 1
    for q in queries:
        print(f"\n===== {q} =====")
        for kind, label in (("geocode", "TomTom direcciones"), ("search", "TomTom búsqueda")):
            res, first = tomtom(kind, q)
            print(f"-- {label}:")
            for r in res:
                print("   " + r)
            if kind == "geocode" and first:
                print("   Ruta desde Plaza Independencia: " + route(first))
        n, p = osm(q)
        print("-- Nominatim (Onix hoy): " + " | ".join(n))
        print("-- Photon (Onix hoy): " + " | ".join(p))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
