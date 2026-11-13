"""Capa de faros encendidos: la luz sale de adentro de cada óptica, usando la forma real del faro en la foto."""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

car = Image.open("onix_car.png").convert("RGBA")
W, H = car.size
o = np.asarray(car).astype(np.float32) / 255.0
rgb, alpha = o[:, :, :3], o[:, :, 3]

# Forma del vidrio de cada faro (coordenadas en la imagen recortada del auto)
LENSES = [
    {   # faro derecho de la foto (el que se ve entero)
        "poly": [(327, 296), (345, 262), (368, 222), (427, 210), (487, 200), (533, 189), (585, 192),
                 (587, 197), (567, 233), (540, 260), (513, 278), (467, 290), (400, 294)],
        "bulb": (428, 252), "reach": 95, "core": 13,
    },
    {   # faro izquierdo (se ve de costado)
        "poly": [(42, 186), (52, 200), (52, 240), (44, 262), (36, 266), (28, 250), (30, 215), (36, 195)],
        "bulb": (41, 232), "reach": 40, "core": 7,
    },
]

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
out_rgb = np.zeros((H, W, 3), np.float32)
out_a = np.zeros((H, W), np.float32)
bloom_src = Image.new("L", (W, H), 0)
warm = np.array([1.0, 0.97, 0.9], np.float32)

for L in LENSES:
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).polygon(L["poly"], fill=255)
    m = m.filter(ImageFilter.GaussianBlur(1.2))
    mask = np.asarray(m).astype(np.float32) / 255.0 * alpha

    bx, by = L["bulb"]
    d = np.sqrt((xx - bx) ** 2 + (yy - by) ** 2)
    # Iluminación interna: fuerte cerca de la lámpara, el resto del vidrio también brilla
    g = np.clip(0.36 + 0.68 * np.exp(-(d / L["reach"]) ** 2), 0, 1)
    lit = 1.0 - (1.0 - rgb) * (1.0 - g[..., None] * warm)
    # Punto caliente de la lámpara
    core = np.exp(-(d / L["core"]) ** 2)[..., None]
    lit = lit * (1 - core) + np.array([1.0, 1.0, 0.98]) * core

    out_rgb = out_rgb * (1 - mask[..., None]) + lit * mask[..., None]
    out_a = np.maximum(out_a, mask)
    bloom_src = Image.fromarray(np.maximum(np.asarray(bloom_src), (mask * 255).astype(np.uint8)))

# Halo suave que se escapa del faro (bloom)
bloom1 = np.asarray(bloom_src.filter(ImageFilter.GaussianBlur(10))).astype(np.float32) / 255.0
bloom2 = np.asarray(bloom_src.filter(ImageFilter.GaussianBlur(28))).astype(np.float32) / 255.0
bloom = np.clip(bloom1 * 0.45 + bloom2 * 0.35, 0, 1) * (1 - out_a)
bloom_rgb = np.array([1.0, 0.95, 0.85], np.float32)

final_a = out_a + bloom
final_rgb = (out_rgb * out_a[..., None] + bloom_rgb * bloom[..., None]) / np.maximum(final_a, 1e-4)[..., None]
img = np.dstack([np.clip(final_rgb, 0, 1), np.clip(final_a, 0, 1)])
Image.fromarray((img * 255).astype(np.uint8), "RGBA").save("onix_glow.png", optimize=True)

# Vista previa de noche: auto tintado + faros
tint = np.array([0x6E, 0x7A, 0xA2], np.float32) / 255.0
night = np.dstack([rgb * tint, alpha])
base = Image.new("RGBA", (W, H), (8, 10, 30, 255))
base.alpha_composite(Image.fromarray((night * 255).astype(np.uint8), "RGBA"))
base.alpha_composite(Image.open("onix_glow.png"))
base.convert("RGB").save("prev_lights.png")
print("ok")
