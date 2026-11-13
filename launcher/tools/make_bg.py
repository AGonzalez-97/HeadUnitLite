"""Genera los 4 fondos de 1024x600 a partir de las fotos: día, atardecer, noche y lluvia."""
import random

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

W, H = 1024, 600
A = "/mnt/user-data/attachments/006f2bbe-1370-5ef5-9600-ee317589ed30/"


def fit(path):
    im = Image.open(path).convert("RGB")
    r = W / H
    if im.width / im.height > r:
        nw = int(im.height * r)
        x = (im.width - nw) // 2
        im = im.crop((x, 0, x + nw, im.height))
    else:
        nh = int(im.width / r)
        im = im.crop((0, 0, im.width, nh))
    return im.resize((W, H), Image.LANCZOS)


def grade(im, mul, add=(0, 0, 0), sat=1.0, contrast=1.0):
    im = ImageEnhance.Color(im).enhance(sat)
    im = ImageEnhance.Contrast(im).enhance(contrast)
    a = np.asarray(im).astype(np.float32)
    a = a * np.array(mul, dtype=np.float32) + np.array(add, dtype=np.float32)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def vgrad(color_top, color_bot, a_top, a_bot):
    g = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(g)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(color_top[i] + (color_bot[i] - color_top[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c + (int(a_top + (a_bot - a_top) * t),))
    return g


base = fit(A + "719f02a1-image.jpg")
dusk = fit(A + "4c50a3a2-image.jpg")

# Día
dia = ImageEnhance.Contrast(base).enhance(1.05)
dia.save("bg_dia.jpg", quality=86)

# Atardecer: la foto dorada con un poco más de calidez
atardecer = grade(dusk, (1.06, 0.98, 0.9), sat=1.1)
atardecer.save("bg_atardecer.jpg", quality=86)

# Noche: azul oscuro, nieve iluminada por la luna, cielo estrellado
src = np.asarray(base).astype(np.int32)
raw = (src[:, :, 2] > 150) & (src[:, :, 2] > src[:, :, 0] + 40) & (src[:, :, 0] < 140)
# El cielo es solo la franja continua desde el borde superior de cada columna
stops = np.array([np.argmin(raw[:, x]) if not raw[:, x].all() else H for x in range(W)])
# suaviza los saltos del borde (mínimo en una ventana de 15 columnas)
stops = np.array([stops[max(0, x - 7):x + 8].min() for x in range(W)])
sky = np.zeros_like(raw)
for x in range(W):
    sky[:stops[x], x] = True
noche = grade(base, (0.34, 0.4, 0.62), add=(2, 4, 14), sat=0.3, contrast=1.1)
na = np.asarray(noche).copy()
na[sky] = (6, 9, 30)
noche = Image.fromarray(na).convert("RGBA")
noche.alpha_composite(vgrad((4, 6, 22), (2, 3, 10), 40, 90))
stars = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sd = ImageDraw.Draw(stars)
rnd = random.Random(7)
placed = 0
while placed < 70:
    x, y = rnd.randrange(W), rnd.randrange(0, 140)
    if not sky[y, x]:
        continue
    placed += 1
    r = rnd.choice([0.6, 0.8, 1.0, 1.4])
    a = rnd.randrange(120, 255)
    sd.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))
noche.alpha_composite(stars.filter(ImageFilter.GaussianBlur(0.4)))
noche.convert("RGB").save("bg_noche.jpg", quality=86)

# Lluvia: gris azulado, sin contraste, con niebla sobre los cerros
lluvia = grade(base, (0.62, 0.66, 0.72), add=(18, 20, 26), sat=0.25, contrast=0.75).convert("RGBA")
lluvia.alpha_composite(vgrad((150, 158, 170), (60, 66, 76), 150, 30))
lluvia = lluvia.filter(ImageFilter.GaussianBlur(1.2))
lluvia.convert("RGB").save("bg_lluvia.jpg", quality=86)
print("ok")
