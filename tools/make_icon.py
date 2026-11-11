"""Ibiko's icon and thinking animation, from the pilot's own art.

Inputs:  docs/brand/source/ibiko_icon_art.png  (the icon: black rounded square, two glowing eyes, circuit traces)
         a GIF of the eyes glancing (pass its path), for the in-chat thinking animation.
Outputs: the launcher icon's back layer (drawable-nodpi/ic_launcher_art.png, the art inside the 72-unit visible area of
         the 108-unit adaptive canvas), the PNG icons in every density, the store image, and res/raw/thinking_eyes.gif.

Run: python3 tools/make_icon.py <app/src/main/res> [path/to/thinking.gif]   (needs Pillow)
"""
import os
import sys
from PIL import Image, ImageDraw

RES = sys.argv[1]
GIF = sys.argv[2] if len(sys.argv) > 2 else None
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "docs/brand/source/ibiko_icon_art.png")


def square_art():
    im = Image.open(ART).convert("RGBA")
    box = im.split()[3].point(lambda v: 255 if v > 200 else 0).getbbox()
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    side = max(box[2] - box[0], box[3] - box[1])
    return im.crop((round(cx - side / 2), round(cy - side / 2), round(cx + side / 2), round(cy + side / 2)))


art = square_art()

# the adaptive back layer: black, the art filling the middle 72 of 108 units (what a launcher shows)
canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 255))
inner = art.resize((288, 288), Image.LANCZOS)
canvas.alpha_composite(inner, (72, 72))
canvas.convert("RGB").save(os.path.join(RES, "drawable-nodpi/ic_launcher_art.png"), optimize=True)

# the PNG icons: the art as it is (its own rounded square), and cut to a circle for round launchers
for dens, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    a = art.resize((size, size), Image.LANCZOS)
    a.save(os.path.join(RES, f"mipmap-{dens}/ic_launcher.png"), optimize=True)
    big = art.resize((size * 4, size * 4), Image.LANCZOS)
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big.size[0] - 1, big.size[1] - 1), fill=255)
    black = Image.new("RGBA", big.size, (0, 0, 0, 255))
    black.alpha_composite(big)
    black.putalpha(mask)
    black.resize((size, size), Image.LANCZOS).save(os.path.join(RES, f"mipmap-{dens}/ic_launcher_round.png"), optimize=True)
art.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "docs/brand/ibiko_icon_512.png"), optimize=True)

# the thinking animation: every frame, cropped to the art and made small for the chat, the same timing
if GIF:
    g = Image.open(GIF)
    frames, times = [], []
    g.seek(0)
    box = g.convert("RGBA").split()[3].point(lambda v: 255 if v > 200 else 0).getbbox()
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    side = max(box[2] - box[0], box[3] - box[1])
    crop = (round(cx - side / 2), round(cy - side / 2), round(cx + side / 2), round(cy + side / 2))
    for i in range(g.n_frames):
        g.seek(i)
        f = g.convert("RGBA").crop(crop).resize((120, 120), Image.LANCZOS)
        frames.append(f)
        times.append(g.info.get("duration", 170))
    # one shared palette of 255 colours, the last index kept for the clear corners outside the rounded square
    sheet = Image.new("RGB", (120 * len(frames), 120))
    for i, f in enumerate(frames):
        sheet.paste(f.convert("RGB"), (120 * i, 0))
    palette = sheet.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    out = []
    for f in frames:
        q = f.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE)
        clear = f.split()[3].point(lambda v: 255 if v < 128 else 0)
        q.paste(255, mask=clear)
        out.append(q)
    out[0].save(os.path.join(RES, "raw/thinking_eyes.gif"), save_all=True, append_images=out[1:], duration=times, loop=0,
                transparency=255, disposal=2)
print("ok")
