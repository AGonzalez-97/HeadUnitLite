"""Genera las voces del inicio con ElevenLabs (v0.70.0). Uso en la CI: lee la clave de la variable
ELEVENLABS_API_KEY (GitHub Secrets) y nunca la imprime. Sin clave, no hace nada.

python3 tools/voces/elevenlabs.py tools/voces/elevenlabs.txt app/src/main/assets/voz
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.elevenlabs.io/v1"
# Voces prediseñadas de ElevenLabs (IDs públicos de su documentación). Se usan si la clave no tiene
# permiso para leer las voces o si la biblioteca no se puede usar desde la API (v0.70.1).
# Las cuentas no tienen todas las mismas: se prueban en orden hasta que una existe.
PREMADE = {"george": "JBFqnCBsd6RMkjVDRZzb", "brian": "nPczCjzI2devNBz1zQrw",
           "daniel": "onwK4e9ZLuTAKqWW03F9", "sarah": "EXAVITQu4vr4xnSDxMaL",
           "liam": "TX3LPaxmHKxFdv7VYWLS", "eric": "cjVigY5qzO86Huf0OWal",
           "chris": "iP95p4xoKVk53GoZ742B", "roger": "CwhRBWXzGAHq8TQ4Fs17",
           "charlie": "IKne3meq5aSn9XLyUdCD", "will": "bIHbv24MWmeRgasZH58o"}
PHRASES = {}  # frases de navegación y avisos: archivo y voz (se guardan como ia_<clave>.ogg)
EXTRAS = {}  # frase extra por voz: nombre → texto (por ejemplo, el aviso de luces)
FALLBACK = {"male": ["brian", "daniel", "liam", "eric", "chris", "roger", "charlie", "will", "george"],
            "female": ["sarah"]}


def why(e):
    msg = str(e)
    if hasattr(e, "read"):
        try:
            msg += " " + e.read().decode()[:300]
        except Exception:
            pass
    return msg


def call(method, path, key, body=None, raw=False):
    req = urllib.request.Request(API + path, method=method)
    req.add_header("xi-api-key", key)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, data, timeout=60) as r:
        out = r.read()
    return out if raw else json.loads(out or b"{}")


def parse(path):
    """Filas de voces y frases extra ("@<nombre>|<texto>": se generan con cada voz, v0.72.0)."""
    rows = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line.startswith("@@frases|"):
            # "@@frases|<archivo clave|texto>|<id de voz>": todas las frases grabadas con esa voz (v0.72.2)
            _, path2, vid = line.split("|", 2)
            PHRASES["file"], PHRASES["voice"] = path2.strip(), vid.strip()
            continue
        if line.startswith("@") and "|" in line:
            name, text = line[1:].split("|", 1)
            EXTRAS[name.strip()] = text.strip()
            continue
        if not line or line.startswith("#") or line.count("|") < 5:
            continue
        vid, voice, model, stab, style, text = line.split("|", 5)
        rows.append(dict(id=vid, voice=voice, model=model, stability=float(stab), style=float(style), text=text))
    return rows


def my_voices(key, cache):
    if "_mine" not in cache:
        try:
            cache["_mine"] = call("GET", "/voices", key).get("voices", [])
        except Exception as e:  # clave sin permiso voices_read: se sigue con las prediseñadas
            print(f"::warning::ElevenLabs: no se pudieron leer las voces ({why(e)[:160]}); "
                  "la clave necesita el permiso Voices: Read para usar la biblioteca")
            cache["_mine"] = []
    return cache["_mine"]


def best_premade(mine, spec):
    """Voces prediseñadas de la cuenta que mejor encajan con una "biblioteca:..." (v0.72.0).
    El plan gratuito no deja usar voces de la biblioteca por la API (HTTP 402): se elige entre las
    prediseñadas por género, uso (por ejemplo "advertisement"), descripción ("deep") y acento."""
    spec = spec.split(">")[-1]
    kind, _, rest = spec.partition(":")
    if kind == "diseno":  # "diseno:<género>:<idioma>:<descripción>": se buscan palabras de la descripción
        gender, _, desc = (rest.split(":", 2) + ["", "", ""])[:3]
        d = desc.lower()
        accent = "american" if "american" in d else ""
        use_case = "advertisement" if ("commercial" in d or "advert" in d) else ""
        descriptive = "deep" if "deep" in d else ""
    else:
        lang, accent, gender, use_case, descriptive = (rest.split(":") + ["", "", "", "", ""])[:5]
    scored = []
    for v in mine:
        if v.get("category") != "premade":
            continue
        lab = {k: str(x).lower() for k, x in (v.get("labels") or {}).items()}
        if gender and lab.get("gender") and lab["gender"] != gender:
            continue
        text = " ".join(lab.values()) + " " + str(v.get("description", "")).lower()
        score = (3 if use_case and use_case[:6] in text else 0) + (2 if descriptive and descriptive in text else 0)
        score += 1 if accent and accent[:5] in lab.get("accent", "") else 0
        score += 1 if use_case == "" and ("narrat" in text or "confident" in text) else 0
        scored.append((score, v["name"].split(" - ")[0], v["voice_id"]))
    scored.sort(key=lambda t: -t[0])
    return [(name, vid) for _, name, vid in scored]


def premade(names, cache, notes, spec, reason=""):
    """Devuelve la lista de candidatas (nombre, id); main() se queda con la primera que exista."""
    names = [names] if isinstance(names, str) else names
    cands = [(n, PREMADE[n]) for n in names]
    if isinstance(names, list) and cache.get("_mine"):
        cands = best_premade(cache["_mine"], spec) + cands
    # Distintas voces para cada fila: primero las que todavía no usó otra
    used = cache.setdefault("_used", set())
    cands = [c for c in cands if c[1] not in used] + [c for c in cands if c[1] in used]
    if cands:
        used.add(cands[0][1])
    cache[spec] = cands
    cache["_reason_" + spec] = reason
    return cands


SAMPLE = {
    "es": "Hola, ¿cómo andás? Arrancamos el viaje con todo: rutas nuevas, buena música y el auto listo para salir a la calle.",
    "en": "Every road is a new story. Feel the power, the comfort and the freedom of the open road, every single day you drive.",
}


def design(spec, key, cache, notes, mine):
    """Voz diseñada con una descripción ("diseno:<género>:<idioma>:<descripción>", v0.72.0). El diseño
    de voces (Voice Design) sí se puede usar con el plan gratuito, a diferencia de la biblioteca."""
    _, _, rest = spec.partition(":")
    gender, lang, desc = (rest.split(":", 2) + ["", "", ""])[:3]
    name = "Onix " + desc[:40]
    have = next((m for m in mine if m.get("name") == name), None)
    if have:
        vid = have["voice_id"]
    else:
        text = SAMPLE.get(lang, SAMPLE["en"])
        try:  # API actual
            prev = call("POST", "/text-to-voice/design", key,
                        {"voice_description": desc, "model_id": "eleven_multilingual_ttv_v2", "text": text})
            gid = prev["previews"][0]["generated_voice_id"]
            vid = call("POST", "/text-to-voice", key,
                       {"voice_name": name, "voice_description": desc, "generated_voice_id": gid})["voice_id"]
        except urllib.error.HTTPError as e:
            if e.code not in (404, 405, 422):
                raise
            prev = call("POST", "/text-to-voice/create-previews", key, {"voice_description": desc, "text": text})
            gid = prev["previews"][0]["generated_voice_id"]
            vid = call("POST", "/text-to-voice/create-voice-from-preview", key,
                       {"voice_name": name, "voice_description": desc, "generated_voice_id": gid})["voice_id"]
    cache[spec] = [(f"voz diseñada \"{desc}\"", vid)]
    return cache[spec]


def resolve(spec, key, cache, notes):
    if spec in cache:
        return cache[spec]
    # "a>b": primero a; si no está o falla, b (v0.72.0: una voz propia creada en la web y, si no, el diseño)
    if ">" in spec:
        first, _, others = spec.partition(">")
        kind = first.partition(":")[0]
        mine = my_voices(key, cache)
        if kind == "propia":
            name = first.partition(":")[2].lower()
            v = next((v for v in mine if v.get("name", "").lower().startswith(name)), None)
            if v:
                cache[spec] = [(v["name"], v["voice_id"])]
                return cache[spec]
        cache[spec] = resolve(others, key, cache, notes)
        cache["_reason_" + spec] = cache.get("_reason_" + others, "")
        return cache[spec]
    kind, _, rest = spec.partition(":")
    mine = my_voices(key, cache)
    if kind == "diseno":
        try:
            return design(spec, key, cache, notes, mine)
        except Exception as e:
            print(f"::warning::ElevenLabs: no se pudo diseñar la voz ({why(e)[:200]}); se usa una prediseñada")
            g = rest.split(":")[0]
            return premade(FALLBACK.get(g, FALLBACK["male"]), cache, notes, spec, " — prediseñada: no se pudo diseñar la voz")
    if kind == "propia":
        v = next((v for v in mine if v.get("name", "").lower().startswith(rest.lower())), None)
        if v:
            cache[spec] = [(v["name"], v["voice_id"])]
            cache.setdefault("_used", set()).add(v["voice_id"])
            return cache[spec]
        if rest.lower() in PREMADE:
            return premade(rest.lower(), cache, notes, spec)
        raise RuntimeError(f"no está la voz propia {rest}")
    lang, accent, gender, use_case, descriptive = (rest.split(":") + ["", "", "", "", ""])[:5]
    try:
        # El nombre del acento en la biblioteca puede variar: se prueban variantes
        variants = [accent] + (["argentine", "argentina", "rioplatense", "latin american"] if "argent" in accent else [])
        found = []
        for acc in variants:
            params = {"page_size": 10, "language": lang, "accent": acc, "gender": gender,
                      "sort": "usage_character_count_1y"}
            if use_case:
                params["use_cases"] = use_case
            if descriptive:
                params["descriptives"] = descriptive
            q = urllib.parse.urlencode(params)
            found = call("GET", "/shared-voices?" + q, key).get("voices", [])
            if found:
                break
        if not found:
            raise RuntimeError(f"la biblioteca no tiene voces {rest}")
        v = found[0]
        name = " ".join(x for x in ["Onix", accent, gender, use_case, descriptive] if x)
        have = next((m for m in mine if m.get("name") == name), None)
        if have:
            vid = have["voice_id"]
        else:
            vid = call("POST", f"/voices/add/{v['public_owner_id']}/{v['voice_id']}", key, {"new_name": name})["voice_id"]
    except Exception as e:
        reason = why(e)[:160]
        print(f"::warning::ElevenLabs: biblioteca no disponible para {spec} ({reason}); se usa una voz prediseñada")
        return premade(FALLBACK.get(gender, FALLBACK["male"]), cache, notes, spec,
                       " — sin acento argentino: la biblioteca no estuvo disponible")
    cache[spec] = [(f"\"{v.get('name')}\" de la biblioteca ({v.get('accent')}, {v.get('gender')}, "
                    f"{v.get('age', '')}, {v.get('use_case', '')})", vid)]
    return cache[spec]


def main(cfg, out):
    key = os.environ.get("ELEVENLABS_API_KEY", "").strip()
    if not key:
        print("::notice::ElevenLabs: sin el secreto ELEVENLABS_API_KEY, no se generan voces")
        return 0
    cache, notes, made = {}, [], 0
    rows = parse(cfg)
    # Primero las voces propias fijas, para que las de respaldo no repitan la misma voz
    rows.sort(key=lambda r: 0 if r["voice"].startswith("propia:") and ">" not in r["voice"] else 1)
    for row in rows:
        jobs = [(f"inicio_{row['id']}", row["text"], row["stability"], row["style"])]
        # Avisos (v0.72.0, pedido del autor): tono serio, no alegre → más estable y sin exagerar el estilo
        jobs += [(f"{name}_{row['id']}", text, max(row["stability"], 0.8), 0.0) for name, text in EXTRAS.items()]
        if PHRASES.get("voice") == row["id"] and os.path.exists(PHRASES["file"]):
            # Indicaciones y avisos con tono serio (pedido del autor): ia_<clave>.ogg; "inicio" es el slogan
            for line in open(PHRASES["file"], encoding="utf-8"):
                line = line.strip()
                if line and not line.startswith("#") and "|" in line:
                    k, t = line.split("|", 1)
                    if k.strip() != "inicio":
                        jobs.append((f"ia_{k.strip()}", t.strip(), 0.85, 0.0))
        for fname, text, stability, style in jobs:
            try:
                cands = resolve(row["voice"], key, cache, notes)
                body = {"text": text, "model_id": row["model"],
                        "voice_settings": {"stability": stability, "similarity_boost": 0.8,
                                           "style": style, "use_speaker_boost": True}}
                mp3, last = None, None
                for name, vid in list(cands):
                    try:
                        mp3 = call("POST", f"/text-to-speech/{vid}?output_format=mp3_44100_128", key, body, raw=True)
                    except urllib.error.HTTPError as e:
                        if e.code == 402 and row["voice"].startswith("biblioteca:") and not cache.get("_reason_" + row["voice"]):
                            # Plan gratuito: la biblioteca no se puede usar por la API → prediseñada parecida
                            g = (row["voice"].split(":") + ["", "", ""])[3]
                            print(f"::warning::ElevenLabs: {row['voice']} pide plan pago (HTTP 402); se usa la prediseñada más parecida")
                            fb = premade(FALLBACK.get(g, FALLBACK["male"]), cache, notes, row["voice"],
                                         " — prediseñada: la biblioteca pide plan pago")
                            cands[:] = fb
                            mp3 = "retry"
                            break
                        if e.code != 404:
                            raise
                        last = e
                        cands.remove((name, vid))  # esa voz no está en la cuenta: la próxima no la prueba
                        continue
                    note = f"{row['id']} ({row['voice']}): {name[:1].upper() + name[1:]} (ElevenLabs){cache.get('_reason_' + row['voice'], '')}"
                    if note not in notes:
                        notes.append(note)
                    break
                if mp3 == "retry":
                    mp3 = None
                    for name, vid in list(cands):
                        try:
                            mp3 = call("POST", f"/text-to-speech/{vid}?output_format=mp3_44100_128", key, body, raw=True)
                        except urllib.error.HTTPError as e:
                            if e.code not in (402, 404):
                                raise
                            last = e
                            cands.remove((name, vid))
                            continue
                        note = f"{row['id']} ({row['voice']}): {name} (ElevenLabs){cache.get('_reason_' + row['voice'], '')}"
                        if note not in notes:
                            notes.append(note)
                        break
                if mp3 is None:
                    raise last or RuntimeError("ninguna voz disponible")
                tmp = f"/tmp/el_{fname}.mp3"
                open(tmp, "wb").write(mp3)
                subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp, "-af",
                                "apad=pad_dur=0.3,loudnorm=I=-14:TP=-1.5:LRA=9", "-ac", "1", "-ar", "22050",
                                "-c:a", "libvorbis", "-q:a", "5", f"{out}/{fname}.ogg"], check=True)
                print(f"::notice::ElevenLabs: {fname} listo")
                made += 1
            except Exception as e:  # una voz que falla no frena a las otras
                print(f"::warning::ElevenLabs: {fname} falló: {why(e)}")
    if made == 0:  # sin voces nuevas no se pisa la lista anterior
        print("ElevenLabs: 0 voces")
        return 0
    with open(f"{out}/LEEME_ia.txt", "w", encoding="utf-8") as f:
        f.write("Voces del inicio generadas con ElevenLabs (https://elevenlabs.io), plan gratuito: uso personal,\n"
                "no comercial, con atribución a ElevenLabs. Voces usadas:\n\n")
        f.write("\n".join(notes) + "\n")
    print(f"ElevenLabs: {made} voces")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
