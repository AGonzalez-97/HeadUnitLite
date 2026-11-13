"""Quita los reflejos de los árboles: vidrios nuevos (polarizado limpio) y capó con pintura lisa."""
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

if not Path("onix_car_raw.png").exists():
    shutil.copy("onix_car.png", "onix_car_raw.png")
car = Image.open("onix_car_raw.png").convert("RGBA")
W, H = car.size
a = np.asarray(car).astype(np.float32) / 255.0
rgb, alpha = a[:, :, :3].copy(), a[:, :, 3]


def mask_of(poly, feather=1.5):
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return np.asarray(m.filter(ImageFilter.GaussianBlur(feather))).astype(np.float32) / 255.0


def masked_blur(img, mask, radius):
    num = np.dstack([
        np.asarray(Image.fromarray((img[:, :, c] * mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))).astype(np.float32)
        for c in range(3)
    ])
    den = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))).astype(np.float32)
    return num / np.maximum(den, 1e-3)[..., None]


rng = np.random.default_rng(3)
grain = rng.normal(0, 0.012, (H, W, 1)).astype(np.float32)
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)

# ---- Capó: pintura blanca lisa, conservando el sombreado general ----
hood = [(75, 175), (100, 150), (150, 120), (200, 108), (584, 108), (575, 125), (560, 165),
        (585, 190), (370, 220), (350, 183), (80, 177)]
hm = mask_of(hood, 2.0)
smooth = masked_blur(rgb, (hm > 0.5).astype(np.float32), 22)
# brillo suave: más claro hacia el parabrisas, como luz de cielo
sheen = np.clip(1.0 - (yy - 108) / 140.0, 0, 1)[..., None] * 0.05
hood_rgb = np.clip(smooth + sheen + grain, 0, 1)
rgb = rgb * (1 - hm[..., None]) + hood_rgb * hm[..., None]

# ---- Vidrios: polarizado oscuro con reflejo de cielo arriba y una franja de brillo ----
def glass(poly, top_y, bot_y, base=(0.11, 0.13, 0.17), sky=(0.36, 0.42, 0.52)):
    m = mask_of(poly, 1.2)
    t = np.clip((yy - top_y) / max(bot_y - top_y, 1), 0, 1)[..., None]
    col = np.array(sky) * (1 - t) ** 1.3 + np.array(base) * (1 - (1 - t) ** 1.3)
    # reflejo del horizonte: una banda clara horizontal
    horizon = np.exp(-((t - 0.42) / 0.07) ** 2) * 0.07
    # franjas diagonales de brillo (reflejo del cielo en el vidrio curvo)
    diag = xx + 1.4 * yy
    streak = (np.exp(-((diag - 470) / 38.0) ** 2) * 0.10 + np.exp(-((diag - 560) / 14.0) ** 2) * 0.06)[..., None]
    # borde inferior oscuro (zona de los limpiaparabrisas)
    cowl = np.clip((t - 0.86) / 0.14, 0, 1) * 0.06
    col = np.clip(col + horizon + streak - cowl + grain * 0.6, 0, 1)
    return m, col


windshield = [(200, 108), (226, 90), (262, 62), (300, 38), (330, 26), (400, 20), (470, 14),
              (540, 12), (607, 16), (604, 40), (596, 80), (584, 108)]
front_side = [(627, 30), (660, 26), (677, 70), (660, 82), (630, 107), (620, 100)]
rear_side = [(680, 30), (725, 60), (735, 100), (718, 100), (685, 60)]

for poly, ty, by in [(windshield, 12, 108), (front_side, 26, 107), (rear_side, 30, 100)]:
    m, col = glass(poly, ty, by)
    rgb = rgb * (1 - m[..., None]) + col * m[..., None]

out = np.dstack([rgb, alpha])
Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), "RGBA").save("onix_car.png", optimize=True)

prev = Image.new("RGBA", (W, H), (120, 150, 190, 255))
prev.alpha_composite(Image.open("onix_car.png"))
prev.convert("RGB").save("prev_clean.png")
print("ok")
