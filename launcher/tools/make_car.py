"""Arma las imágenes del auto para la app: auto recortado con patente AAA y capa de luces."""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

S = 2  # se trabaja al doble de resolución
car = Image.open("onix_cut.png").convert("RGBA")
car = car.resize((car.width * S, car.height * S), Image.LANCZOS)
car = car.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))

# ---- Patente AAA (estilo Mercosur: franja azul + fondo blanco) ----
PW, PH = 600, 240
plate = Image.new("RGBA", (PW, PH), (238, 240, 243, 255))
d = ImageDraw.Draw(plate)
d.rectangle([0, 0, PW, 52], fill=(28, 62, 160, 255))
d.rectangle([0, 0, PW - 1, PH - 1], outline=(40, 44, 52, 255), width=6)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf", 170)
tw = d.textlength("AAA", font=font)
d.text(((PW - tw) / 2, 48), "AAA", font=font, fill=(18, 20, 26, 255))

# Esquinas de la patente en la foto original (TL, TR, BR, BL), escaladas
quad = [(46, 238), (112, 246), (111, 273), (46, 264)]
quad = [(x * S, y * S) for x, y in quad]


def find_coeffs(dst, src):
    m = []
    for (x, y), (u, v) in zip(dst, src):
        m.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        m.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    a = np.array(m, dtype=float)
    b = np.array(src, dtype=float).reshape(8)
    return np.linalg.solve(a, b).tolist()


src = [(0, 0), (PW, 0), (PW, PH), (0, PH)]
coeffs = find_coeffs(quad, src)
warped = plate.transform(car.size, Image.PERSPECTIVE, coeffs, Image.BICUBIC)
# un poco de suavizado para que no quede más nítida que la foto
warped = warped.filter(ImageFilter.GaussianBlur(0.8))
# leve sombreado para integrarla con la luz de la foto
shade = Image.new("RGBA", car.size, (0, 0, 0, 0))
car.alpha_composite(warped)

# ---- Recorte final ----
bbox = car.getbbox()
car = car.crop(bbox)
car.save("onix_car.png", optimize=True)

# ---- Capa de luces (solo el brillo, fondo transparente) ----
ox, oy = bbox[0], bbox[1]
glow = Image.new("RGBA", car.size, (0, 0, 0, 0))


def blob(cx, cy, rx, ry, color, alpha):
    layer = Image.new("RGBA", car.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(
        [cx - rx - ox, cy - ry - oy, cx + rx - ox, cy + ry - oy], fill=color + (alpha,)
    )
    return layer


# Faro derecho (el grande, a la derecha de la foto) y faro izquierdo (parcial)
lights = [
    # (cx, cy, rx, ry) en coordenadas x2 de la foto original
    (430, 395, 90, 34),
    (48, 372, 22, 34),
]
for cx, cy, rx, ry in lights:
    halo = blob(cx, cy, rx * 1.9, ry * 2.4, (255, 236, 190), 120).filter(ImageFilter.GaussianBlur(34))
    core = blob(cx, cy, rx * 0.9, ry * 0.8, (255, 252, 240), 235).filter(ImageFilter.GaussianBlur(10))
    hot = blob(cx + rx * 0.15, cy, rx * 0.35, ry * 0.35, (255, 255, 255), 255).filter(ImageFilter.GaussianBlur(6))
    glow.alpha_composite(halo)
    glow.alpha_composite(core)
    glow.alpha_composite(hot)

# Luz de posición en la parrilla inferior (antiniebla apagado: no se agrega)
glow.save("onix_glow.png", optimize=True)
print("car", car.size, "bbox", bbox)
