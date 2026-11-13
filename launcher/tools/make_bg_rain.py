"""Fondos de lluvia para atardecer y noche (v0.39.0), derivados de los fondos ya hechos
(bg_atardecer.jpg y bg_noche.jpg), para que la lluvia siga la hora del día y tenga la misma ruta.

- Atardecer con lluvia: cielo cubierto gris, con un resto de luz cálida baja en el horizonte;
  colores apagados.
- Noche con lluvia: sin estrellas (cielo nublado), más oscura y fría; las luces del valle se
  conservan pero difusas por la llovizna.

Uso: python3 tools/make_bg_rain.py carpeta_drawable   (lee y escribe en esa carpeta)
Salida: bg_lluvia_atardecer.jpg y bg_lluvia_noche.jpg de 1280x750.
"""
import sys
import cv2
import numpy as np


def _rows(h):
    return np.arange(h)[:, None, None] / h


def rain_from_sunset(img):
    f = img.astype(np.float32) / 255
    y = _rows(f.shape[0])
    gray = np.repeat(f.mean(2, keepdims=True), 3, 2)
    sky = np.clip((0.30 - y) / 0.08, 0, 1)                      # el cielo está arriba del 25 % aprox.
    r = (gray * 0.6 + f * 0.4) * 0.62 * np.array([1.0, 0.98, 1.04])   # BGR: apagado y algo frío
    overcast = np.array([0.42, 0.40, 0.42]) * (0.85 + 0.15 * (1 - y))
    glow = np.exp(-((y - 0.215) / 0.035) ** 2) * np.array([0.06, 0.13, 0.26])  # resto del sol, cálido
    r = r * (1 - sky * 0.8) + (overcast + glow) * sky * 0.8
    return np.clip(r * 255, 0, 255).astype(np.uint8)


def rain_from_night(img):
    f = img.astype(np.float32) / 255
    y = _rows(f.shape[0])
    sky = np.clip((0.30 - y) / 0.08, 0, 1)
    base = cv2.GaussianBlur(f, (0, 0), 1.2)                        # llovizna: todo un poco difuso
    lights = np.clip(f - cv2.GaussianBlur(f, (0, 0), 6), 0, 1)     # puntos de luz del valle
    lights *= (1 - sky)
    halo = cv2.GaussianBlur(lights, (0, 0), 4) * 2.2              # cada luz se abre con la lluvia
    r = base * 0.78 * np.array([1.04, 1.0, 0.96]) + halo + lights * 0.5
    clouds = np.array([0.20, 0.17, 0.16]) * (0.8 + 0.2 * (1 - y))   # nubes bajas iluminadas por el pueblo
    r = r * (1 - sky * 0.9) + clouds * sky * 0.9
    return np.clip(r * 255, 0, 255).astype(np.uint8)


if __name__ == "__main__":
    d = sys.argv[1]
    cv2.imwrite(f"{d}/bg_lluvia_atardecer.jpg", rain_from_sunset(cv2.imread(f"{d}/bg_atardecer.jpg")),
                [cv2.IMWRITE_JPEG_QUALITY, 90])
    cv2.imwrite(f"{d}/bg_lluvia_noche.jpg", rain_from_night(cv2.imread(f"{d}/bg_noche.jpg")),
                [cv2.IMWRITE_JPEG_QUALITY, 90])
