"""Ibiko's icon and thinking animation, from the pilot's own art.

Inputs:  docs/brand/source/ibiko_icon_art.png  (the icon: black rounded square, two glowing eyes, circuit traces)
         a GIF of the eyes glancing (pass its path), for the in-chat thinking animation.
Outputs: the launcher icon's back layer (drawable-nodpi/ic_launcher_art.png: the face, 46 of the 108 adaptive units, on
         a teal background with room around it), the PNG icons in every density, the store image, and (with a GIF)
         res/raw/thinking_eyes.gif. The chat's live face is drawn in code (stage/LiveFace), not from the GIF.

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

# The face sits smaller on a coloured background, with room around it, so every launcher's mask (circle, squircle,
# rounded square) shows the whole face with its own curved corners, never cut. FACE is its side in the 108-unit
# adaptive canvas (the launcher shows the middle 72; a circle mask is 72 across: a 46-unit face, corners and all,
# sits well inside it with a ring of the background round it).
FACE = 46
BG_TOP, BG_BOTTOM = (16, 86, 100), (5, 34, 44)  # a deep teal that sets off the cyan eyes


def background(size):
    """The background colour: a soft diagonal teal gradient."""
    g = Image.new("RGBA", (size, size))
    px = g.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)) + (255,)
    return g


def face_on(bg, face_px):
    """The face centred on a background, with a soft shadow under it."""
    size = bg.size[0]
    from PIL import ImageFilter
    face = art.resize((face_px, face_px), Image.LANCZOS)
    off = (size - face_px) // 2
    shadow = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 140), (off, off + face_px // 24, off + face_px, off + face_px + face_px // 24), face.split()[3])
    shadow = shadow.filter(ImageFilter.GaussianBlur(face_px / 22))
    out = bg.copy()
    out.alpha_composite(shadow)
    out.alpha_composite(face, (off, off))
    return out


# the adaptive back layer: the background colour, the face in the middle
canvas = face_on(background(432), round(432 * FACE / 108))
canvas.convert("RGB").save(os.path.join(RES, "drawable-nodpi/ic_launcher_art.png"), optimize=True)
# the PNG icons (older launchers): a rounded square of the background colour with the face inside, and a circle
for dens, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    big = size * 4
    sq = face_on(background(big), round(big * 0.62))
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=big * 0.22, fill=255)
    sq.putalpha(mask)
    sq.resize((size, size), Image.LANCZOS).save(os.path.join(RES, f"mipmap-{dens}/ic_launcher.png"), optimize=True)
    rd = face_on(background(big), round(big * 0.56))
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    rd.putalpha(mask)
    rd.resize((size, size), Image.LANCZOS).save(os.path.join(RES, f"mipmap-{dens}/ic_launcher_round.png"), optimize=True)
face_on(background(512), 330).save(os.path.join(ROOT, "docs/brand/ibiko_icon_512.png"), optimize=True)

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
