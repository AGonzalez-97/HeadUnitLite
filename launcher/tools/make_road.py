"""Genera road_ground.png (1024x600): llanura con pasto y una ruta en perspectiva bajo el auto.
Arriba del horizonte es transparente, así se ve la foto de los cerros. En la app se tiñe según el ambiente."""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1024, 600
HORIZON = 300
VP = np.array([1040.0, 325.0])
# Ruta paralela al costado del auto; el auto va en el carril izquierdo de la imagen (su mano derecha)
CENTER_B = np.array([476.0, 600.0])
LB, RB = np.array([-900.0, 600.0]), np.array([1852.0, 600.0])
rng = np.random.default_rng(11)

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
depth = np.clip((yy - HORIZON) / (H - HORIZON), 0, 1)  # 0 lejos, 1 cerca

# ---- Terreno: pasto seco/verde con ruido en varias escalas ----
def noise(scale):
    n = rng.random((H // scale + 2, W // scale + 2)).astype(np.float32)
    return np.asarray(Image.fromarray((n * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)).astype(np.float32) / 255

n = 0.5 * noise(24) + 0.3 * noise(8) + 0.2 * noise(2)
base_far = np.array([96, 108, 74], np.float32)
base_near = np.array([46, 54, 34], np.float32)
ground = base_far * (1 - depth[..., None]) + base_near * depth[..., None]
ground *= (0.82 + 0.36 * n)[..., None]
# pasto: rayitas verticales más marcadas cerca
blades = noise(1)
ground *= (0.9 + 0.2 * blades * depth)[..., None]

# ---- Ruta: geometría por coordenadas (t = posición entre punto de fuga y borde inferior) ----
def side_x(p_bottom, y):
    t = (y - VP[1]) / (p_bottom[1] - VP[1])
    return VP[0] + (p_bottom[0] - VP[0]) * t

lx = side_x(LB, yy)
rx = side_x(RB, yy)
width = np.maximum(rx - lx, 1e-3)
u = (xx - lx) / width  # 0 borde izquierdo, 1 borde derecho
on_road = (yy > VP[1]) & (u >= 0) & (u <= 1)
shoulder = (yy > VP[1]) & (((u > -0.035) & (u < 0)) | ((u > 1) & (u < 1.035)))

# asfalto con grano
asphalt_far = np.array([118, 120, 126], np.float32)
asphalt_near = np.array([58, 60, 66], np.float32)
asphalt = asphalt_far * (1 - depth[..., None]) + asphalt_near * depth[..., None]
asphalt *= (0.9 + 0.2 * noise(1))[..., None]
# huellas de neumáticos (un poco más oscuras) en cada carril
for c in (0.17, 0.33, 0.67, 0.83):
    asphalt *= (1 - 0.08 * np.exp(-((u - c) / 0.035) ** 2))[..., None]

dirt = np.array([120, 104, 78], np.float32) * (1 - 0.45 * depth[..., None]) * (0.85 + 0.3 * n[..., None])

img = ground.copy()
img[shoulder] = dirt[shoulder]
img[on_road] = asphalt[on_road]

# líneas de borde blancas
edge_w = 0.012 + 0.0 * depth
edge = on_road & ((u < 0.010) | (u > 0.990))
img[edge] = np.array([226, 228, 232], np.float32) * (0.85 + 0.15 * n[edge][..., None])

# ---- Alfa: transparente arriba, fundido en el horizonte ----
alpha = np.clip((yy - HORIZON) / 36.0, 0, 1)
alpha[on_road | shoulder] = np.clip((yy[on_road | shoulder] - VP[1]) / 8.0, 0, 1)
out = np.dstack([np.clip(img, 0, 255), alpha * 255]).astype(np.uint8)
im = Image.fromarray(out, "RGBA").filter(ImageFilter.GaussianBlur(0.6))
im.save("road_ground.png", optimize=True)

# vista previa con el fondo de día y el auto
bg = Image.open("bg_dia.jpg").convert("RGBA")
bg.alpha_composite(im)
sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(sh).ellipse([300, 424, 760, 480], fill=(0, 0, 0, 210))
bg.alpha_composite(sh.filter(ImageFilter.GaussianBlur(14)))
car = Image.open("onix_car.png").resize((470, 307), Image.LANCZOS)
pd = ImageDraw.Draw(bg)
for k in range(1, 40):
    z0, z1 = k / 900.0, (k + 0.5) / 900.0
    y0, y1 = VP[1] + 1 / z1, VP[1] + 1 / z0
    if y0 > 600: continue
    y1 = min(y1, 600)
    def cx(y): return VP[0] + (CENTER_B[0] - VP[0]) * (y - VP[1]) / (600 - VP[1])
    w = max(1, int(1 + 7 * (y1 - VP[1]) / 275))
    pd.line([(cx(y0), y0), (cx(y1), y1)], fill=(238, 190, 60, 255), width=w)
bg.alpha_composite(car, (289, 166))
bg.convert("RGB").save("prev_road.png")
print("ok")
