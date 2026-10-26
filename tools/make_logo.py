"""Generate the DVINE logo: halftone sphere on a dot field, DVINE across the equator.

Run: python3 tools/make_logo.py   (needs Pillow + numpy). Output is reproducible.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = 2048
SS = 2  # supersample


def halftone(size):
    n = size * SS
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    u = (xx / n) * 2 - 1
    v = (yy / n) * 2 - 1
    img = np.zeros((n, n), np.float32)
    # square dot field behind the sphere
    field = (np.abs(u) < 0.83) & (np.abs(v) < 0.83)
    pitch = 0.0215
    fu = (u / pitch) % 1.0 - 0.5
    fv = (v / pitch) % 1.0 - 0.5
    grid = (np.abs(fu) < 0.34) & (np.abs(fv) < 0.34)
    img[field] = 1.0
    img[field & grid] = 0.0
    # sphere: map pixel to sphere surface angles, dots in lon/lat space
    R = 0.79
    r2 = (u * u + v * v) / (R * R)
    inside = r2 < 1.0
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    lon = np.arctan2(u / R, z)
    lat = np.arcsin(np.clip(v / R, -1, 1))
    k = 34 / np.pi
    du = (lon * k) % 1.0 - 0.5
    dv = (lat * k) % 1.0 - 0.5
    rad = 0.30 * (0.35 + 0.65 * z)  # dots shrink toward the rim
    dots = du * du + dv * dv < rad * rad
    img[inside] = 1.0
    img[inside & dots] = 0.0
    out = Image.fromarray((img * 255).astype(np.uint8), "L")
    return out.resize((size, size), Image.LANCZOS)


def font(px):
    for path in ["/usr/share/fonts/opentype/inter/InterDisplay-Black.otf",
                 "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf",
                 "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]:
        if os.path.exists(path):
            return ImageFont.truetype(path, px)
    return ImageFont.load_default()


def letters(base, size):
    img = base.convert("RGB")
    f = font(int(size * 0.17))
    word = "DVINE"
    gap = size * 0.155
    cx, cy = size / 2, size * 0.515
    xs = [cx + (i - 2) * gap for i in range(5)]
    def layer(fill, stroke_w, offset=(0, 0), stroke_fill=None):
        lay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        for ch, x in zip(word, xs):
            d.text((x + offset[0], cy + offset[1]), ch, font=f, anchor="mm", fill=fill,
                   stroke_width=stroke_w, stroke_fill=stroke_fill or fill)
        return lay
    sw = int(size * 0.016)
    shadow = layer((150, 150, 150, 255), sw, (int(size * 0.012), int(size * 0.012)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(size * 0.002))
    img.paste(shadow, (0, 0), shadow)
    outline = layer((255, 255, 255, 255), sw)
    img.paste(outline, (0, 0), outline)
    core = layer((0, 0, 0, 255), int(size * 0.004), stroke_fill=(0, 0, 0, 255))
    img.paste(core, (0, 0), core)
    return img


def main():
    logo = letters(halftone(S), S)
    brand = os.path.join(ROOT, "docs", "brand")
    os.makedirs(brand, exist_ok=True)
    logo.save(os.path.join(brand, "dvine_logo_2048.png"))
    dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    mask_full = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask_full).ellipse((0, 0, S - 1, S - 1), fill=255)
    for name, px in dens.items():
        d = os.path.join(ROOT, "app", "src", "main", "res", "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        sq = logo.resize((px, px), Image.LANCZOS)
        sq.save(os.path.join(d, "ic_launcher.png"))
        rnd = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        rnd.paste(sq, (0, 0), mask_full.resize((px, px), Image.LANCZOS))
        rnd.save(os.path.join(d, "ic_launcher_round.png"))
    print("ok")


if __name__ == "__main__":
    main()
