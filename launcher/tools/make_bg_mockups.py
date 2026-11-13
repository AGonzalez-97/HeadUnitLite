"""Fondos de Inicio (v0.29.0) a partir de las imágenes de referencia del autor (día, atardecer y
noche, hechas con otra IA sobre el diseño de la app). Cada imagen trae los paneles y el auto
encima: se borran (máscaras a mano para los paneles y los textos; el auto se ubica buscando
`onix_car.png` en la imagen) y se rellenan con un relleno piramidal suave. Lo que se rellena
queda debajo de los paneles o del auto de la app, que se dibuja en el mismo lugar.
La lluvia se deriva del día (cielo cubierto, colores apagados). La noche se deriva del atardecer
(v0.29.2), para que tenga la misma ruta que los otros climas: escena oscura y fría, cielo con
estrellas (el cielo se ubica con la imagen de día), luces cálidas del valle y luz de los faros.

Uso: python3 tools/make_bg_mockups.py dia.jpg atardecer.jpg onix_car.png carpeta_salida
Salida: bg_dia/atardecer/noche/lluvia.jpg de 1280x750 (se trabaja a 1536x900, el lienzo de 1024x600 dp a 1,5x).
"""
import sys
import cv2
import numpy as np

OUT_W, OUT_H = 1536, 900

# Rectángulos de paneles y textos, en coordenadas de la imagen mostrada (ancho ~2000),
# se escalan al tamaño real. (x0, y0, x1, y1)
PANELS = {
    "dia": (1998, [(60, 0, 945, 250), (1290, 12, 1962, 230), (60, 250, 697, 634), (60, 630, 697, 850),
                   (1498, 238, 1962, 852), (60, 854, 1962, 1044), (70, 1040, 480, 1116), (1380, 1040, 1940, 1116)]),
    "noche": (2000, [(62, 0, 1000, 280), (1297, 20, 1970, 260), (62, 283, 733, 705), (62, 700, 733, 945),
                     (1505, 268, 1970, 945), (62, 950, 1970, 1156), (70, 1150, 500, 1243), (1380, 1150, 1940, 1243)]),
}
PANELS["atardecer"] = PANELS["dia"]


def pyramid_fill(img, mask):
    """Rellena los píxeles con mask=1 promediando desde lo conocido, nivel por nivel."""
    known = (1 - mask).astype(np.float32)
    levels = []
    im, w = img.astype(np.float32) * known[..., None], known
    while min(im.shape[:2]) > 8:
        levels.append((im, w))
        im = cv2.pyrDown(im)
        w = cv2.pyrDown(w)
    est = im / np.maximum(w[..., None], 1e-6)
    for im_l, w_l in reversed(levels):
        est = cv2.resize(est, (im_l.shape[1], im_l.shape[0]), interpolation=cv2.INTER_LINEAR)
        mine = im_l / np.maximum(w_l[..., None], 1e-6)
        a = np.clip(w_l * 4, 0, 1)[..., None]
        est = mine * a + est * (1 - a)
    out = img.astype(np.float32) * known[..., None] + est * (1 - known[..., None])
    return out


def fit_car(img, car):
    """Ubica `onix_car.png` en la imagen con puntos SIFT y una transformación afín (RANSAC).
    Las imágenes de referencia usan ese mismo auto, a veces estirado: la afín lo sigue."""
    sift = cv2.SIFT_create(4000)
    clahe = cv2.createCLAHE(2.0, (8, 8))
    cg = clahe.apply(cv2.cvtColor(car[:, :, :3], cv2.COLOR_BGR2GRAY))
    ig = clahe.apply(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY))
    k1, d1 = sift.detectAndCompute(cg, (car[:, :, 3] > 128).astype(np.uint8) * 255)
    k2, d2 = sift.detectAndCompute(ig, None)
    good = [a for a, b in cv2.BFMatcher().knnMatch(d1, d2, k=2) if a.distance < 0.75 * b.distance]
    src = np.float32([k1[g.queryIdx].pt for g in good])
    dst = np.float32([k2[g.trainIdx].pt for g in good])
    A, _ = cv2.estimateAffine2D(src, dst, ransacReprojThreshold=8)
    return A


def car_mask(img, car, A, extra):
    warped = cv2.warpAffine(car, A, (img.shape[1], img.shape[0]))
    m = (warped[:, :, 3] > 40).astype(np.uint8)
    m = cv2.dilate(m, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * extra + 1, 2 * extra + 1)))
    # Tono del auto de la imagen respecto de la foto de la app (para carTintFor)
    body = (warped[:, :, 3] > 250) & (warped[:, :, :3].mean(2) > 120)
    tint = img[body].astype(np.float64).mean(0) / np.maximum(warped[:, :, :3][body].astype(np.float64).mean(0), 1)
    return m, tint


def process(path, kind, car):
    img = cv2.imread(path)
    H, W = img.shape[:2]
    disp_w, rects = PANELS[kind]
    s = W / disp_w
    mask = np.zeros((H, W), np.uint8)
    for x0, y0, x1, y1 in rects:
        mask[int(y0 * s):int(y1 * s), int(x0 * s):int(x1 * s)] = 1
    A = fit_car(img, car)
    cm, tint = car_mask(img, car, A, 6)
    mask |= cm
    where = (A[0, 2], A[1, 2], 782 * A[0, 0], 512 * A[1, 1])
    filled = pyramid_fill(img, mask.astype(np.float32))
    # grano suave en lo rellenado, para que no se vea plástico
    rng = np.random.default_rng(3)
    grain = cv2.GaussianBlur(rng.normal(0, 1, (H, W)).astype(np.float32), (0, 0), 0.9) * 4
    filled += (grain * mask)[..., None]
    filled = np.clip(filled, 0, 255).astype(np.uint8)
    # Encuadre: alto completo (día y atardecer) o ancho completo (noche), como en Inicio
    if kind == "noche":
        k = W / 1024
        crop_y = int(round(where[1] - 177 * k))
        crop_y = max(0, min(H - int(600 * k), crop_y))
        box = (0, crop_y, W, crop_y + int(600 * k))
    else:
        k = H / 600
        crop_x = int(round((W - 1024 * k) / 2))
        box = (crop_x, 0, crop_x + int(1024 * k), H)
    x0, y0, x1, y1 = box
    out = cv2.resize(filled[y0:y1, x0:x1], (OUT_W, OUT_H), interpolation=cv2.INTER_AREA)
    dp = ((where[0] - x0) / k, (where[1] - y0) / k, where[2] / k, where[3] / k)
    print(kind, "auto (x, y, ancho, alto) dp = (%.1f, %.1f, %.1f, %.1f)" % dp,
          "tono BGR", np.round(tint, 3), "recorte", box)
    return out


def night_from_sunset(sunset, day):
    src = sunset.astype(np.float32) / 255
    d = day.astype(np.float32) / 255
    H, W = src.shape[:2]
    y = np.arange(H)[:, None] / H
    m = cv2.morphologyEx(((d[:, :, 0] > 0.92) & (y < 0.42)).astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    sky = cv2.GaussianBlur(np.cumprod(m, axis=0).astype(np.float32), (0, 0), 1.5)
    lum = src.mean(2)
    tophat = cv2.morphologyEx(lum, cv2.MORPH_TOPHAT, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
    warmth = src[:, :, 2] - src[:, :, 0]
    lights = np.clip((tophat - 0.07) / 0.12, 0, 1) * np.clip((warmth - 0.10) / 0.15, 0, 1) * (1 - sky) * (y > 0.18) * (y < 0.48)
    gray = src.mean(2, keepdims=True)
    night = (src * 0.35 + gray * 0.65) * np.array([0.62, 0.40, 0.30]) * 1.2
    sky_col = np.concatenate([0.30 - 0.12 * (y / 0.35), 0.13 - 0.05 * (y / 0.35), 0.06 - 0.02 * (y / 0.35)], 1)[:, None, :] * np.ones((1, W, 1))
    rng = np.random.default_rng(31)
    stars = np.zeros((H, W), np.float32)
    for _ in range(500):
        sx, sy = int(rng.random() * W), int(rng.random() * H * 0.33)
        cv2.circle(stars, (sx, sy), 0 if rng.random() < 0.85 else 1, float(0.35 + 0.65 * rng.random() ** 3), -1, lineType=cv2.LINE_AA)
    night = night * (1 - sky[..., None]) + (sky_col + stars[..., None]) * sky[..., None]
    glow = cv2.GaussianBlur(lights, (0, 0), 3) * 0.8 + cv2.GaussianBlur(lights, (0, 0), 8) * 0.4
    night = night + (lights[..., None] * 1.1 + glow[..., None]) * np.array([0.55, 0.80, 1.0])
    night = night + valley_town(H, W, sky)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for hx, spread in ((404, 1.0), (600, 0.8)):   # faros del auto en (380, 182, 391 x 256) dp, a 1,5x
        cx, cy, rx = (hx - 55 * spread) * 1.5, 446 * 1.5, 135 * spread * 1.5
        night += (np.exp(-(((xx - cx) / rx) ** 2 + ((yy - cy) / (rx * 0.3)) ** 2) * 2.2) * 0.32)[..., None] * np.array([0.80, 0.92, 1.0])
    return np.clip(night * 255, 0, 255).astype(np.uint8)


def valley_town(H, W, sky, seed=7):
    """Pueblo iluminado en el fondo del valle (v0.29.3): franja de luces que serpentea entre los
    cerros, más chicas y juntas a lo lejos, y casas sueltas en las laderas. Coordenadas a 1,5x."""
    rng = np.random.default_rng(seed)
    core = np.zeros((H, W, 3), np.float32)
    # Recorrido del valle, de cerca (izquierda, abajo) a lejos (derecha, arriba)
    path = np.array([(470, 300), (560, 290), (650, 280), (740, 268), (830, 258), (920, 250), (1010, 244), (1100, 240)], np.float32)
    t = np.linspace(0, 1, 400)
    xs = np.interp(t, np.linspace(0, 1, len(path)), path[:, 0])
    ys = np.interp(t, np.linspace(0, 1, len(path)), path[:, 1])
    palette = [(0.45, 0.75, 1.0), (0.55, 0.85, 1.0), (0.35, 0.62, 1.0), (0.85, 0.92, 1.0)]   # BGR cálidos y algún blanco
    # Barrios: grupos de luces a lo largo del valle, separados por zonas oscuras
    centers = [int(v) for v in rng.choice(np.arange(10, 390), 14, replace=False)]
    for _ in range(1500):
        i = int(np.clip(centers[rng.integers(len(centers))] + rng.normal(0, 14), 0, 399))
        far = i / 399
        width = 30 - 20 * far                       # el valle se angosta a lo lejos
        x = xs[i] + rng.normal(0, width * 1.3)
        y = ys[i] + rng.normal(0, width * 0.45) + 6 * np.sin(x / 37.0)
        c = np.array(palette[rng.integers(len(palette))]) * (0.35 + 0.45 * rng.random())
        r = 1.6 - 1.0 * far
        cv2.circle(core, (int(x), int(y)), 0 if r < 1.1 else 1, c.tolist(), -1, lineType=cv2.LINE_AA)
    for _ in range(260):                            # casas sueltas en las laderas
        x = 480 + rng.random() * 700
        y = 225 + rng.random() * 75
        c = np.array(palette[rng.integers(len(palette))]) * (0.5 + 0.4 * rng.random())
        cv2.circle(core, (int(x), int(y)), 0, c.tolist(), -1, lineType=cv2.LINE_AA)
    core *= (1 - sky)[..., None]
    glow = cv2.GaussianBlur(core, (0, 0), 2.0) * 0.9 + cv2.GaussianBlur(core, (0, 0), 7) * 0.35
    haze = cv2.GaussianBlur(core, (0, 0), 30) * 1.2     # resplandor tenue del pueblo sobre el valle
    return core + glow + haze


def rain_from_day(day):
    f = day.astype(np.float32) / 255
    gray = np.repeat(f.mean(2, keepdims=True), 3, 2)
    y = np.arange(f.shape[0])[:, None, None] / f.shape[0]
    sky = np.clip((0.33 - y) / 0.08, 0, 1)
    r = (gray * 0.65 + f * 0.35) * 0.72 * np.array([1.06, 1.0, 0.95])
    overcast = np.array([0.62, 0.60, 0.58]) * (0.9 + 0.1 * (1 - y))
    r = r * (1 - sky * 0.75) + overcast * sky * 0.75
    return np.clip(r * 255, 0, 255).astype(np.uint8)


if __name__ == "__main__":
    dia, atardecer, car_png, out = sys.argv[1:5]
    car = cv2.imread(car_png, cv2.IMREAD_UNCHANGED)
    res = {"dia": process(dia, "dia", car), "atardecer": process(atardecer, "atardecer", car)}
    res["noche"] = night_from_sunset(res["atardecer"], res["dia"])
    res["lluvia"] = rain_from_day(res["dia"])
    for k, v in res.items():
        # Se trabaja a 1536x900 (1,5x) y se guarda a 1280x750: alcanza para la pantalla de 1024x600
        # de la radio y ocupa menos memoria (v0.35.1)
        v = cv2.resize(v, (1280, 750), interpolation=cv2.INTER_AREA)
        cv2.imwrite(f"{out}/bg_{k}.jpg", v, [cv2.IMWRITE_JPEG_QUALITY, 90])
