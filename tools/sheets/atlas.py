"""Builds a persona's look picture from its character cut-outs (transparent backgrounds): the front, side and back
views and the face close-up, each cropped to the figure, scaled to one height and packed side by side into one
picture (looks/<who>_atlas.webp, transparency kept: the app reads each view's outline from it). The logo on the
chest and the back is painted out with the suit around it.

    python3 tools/sheets/atlas.py <who> <out dir> front=<png>[:x0,x1] side=... back=... face=... [preview.png]
"""
import json, sys
import numpy as np, cv2
from PIL import Image

H = 1400          # figure height in the atlas, pixels
FACE_H = 900      # the face close-up's height


def crop_view(path, xr=None):
    im = np.asarray(Image.open(path).convert('RGBA')).copy()
    if xr:
        x0, x1 = xr
        im[:, :x0, 3] = 0
        im[:, x1:, 3] = 0
    a = im[..., 3] > 128
    n, lab, st, _ = cv2.connectedComponentsWithStats(a.astype(np.uint8), 8)
    k = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
    keep = lab == k
    keep = cv2.dilate(keep.astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
    im[~keep, 3] = 0
    ys, xs = np.where(im[..., 3] > 128)
    pad = 6
    y0, y1 = max(0, ys.min() - pad), min(im.shape[0], ys.max() + pad + 1)
    x0, x1 = max(0, xs.min() - pad), min(im.shape[1], xs.max() + pad + 1)
    return im[y0:y1, x0:x1]


def remove_logo(im):
    """The logo: the compact teal mark nearest the middle of the upper body. Painted over with the suit."""
    h, w = im.shape[:2]
    rgb = im[..., :3].astype(np.int32)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    teal = (g > 110) & (g > r + 45) & (b > 70) & (im[..., 3] > 200)
    box = np.zeros_like(teal)
    box[int(h * 0.13):int(h * 0.34), int(w * 0.3):int(w * 0.7)] = True
    m = (teal & box).astype(np.uint8)
    n, lab, st, cen = cv2.connectedComponentsWithStats(m, 8)
    best, ba = None, 0
    for i in range(1, n):
        x, y, ww, hh, area = st[i]
        if area < 60 or ww > w * 0.35 or hh > h * 0.12 or abs(cen[i][0] - w / 2) > w * 0.12:
            continue
        if area > ba:  # the biggest mark near the middle line
            ba, best = area, i
    if best is None:
        return im, None
    # the whole mark: the teal pieces whose middles lie close around the first one (its parts are separate shapes)
    bx, by, bw, bh = st[best][:4]
    cx, cy = bx + bw / 2, by + bh / 2
    reach = max(bw, bh) * 0.9
    x0, y0, x1, y1 = bx, by, bx + bw, by + bh
    for i in range(1, n):
        x, y, ww, hh, area = st[i]
        if area < 6 or ww > bw * 1.6 or hh > bh * 1.6:
            continue
        if abs(cen[i][0] - cx) < reach and abs(cen[i][1] - cy) < reach:
            x0, y0, x1, y1 = min(x0, x), min(y0, y), max(x1, x + ww), max(y1, y + hh)
    g = 5
    x0, y0, x1, y1 = max(0, x0 - g), max(0, y0 - g), min(w, x1 + g), min(h, y1 + g)
    # its pixels: the teal (and a glowing fringe) inside that box
    region = np.zeros((h, w), np.uint8)
    region[y0:y1, x0:x1] = 1
    lum = rgb.max(axis=2)
    soft = (g > 60) if False else ((rgb[..., 1] > rgb[..., 0] + 20) & (rgb[..., 1] > 55))
    mark = region & ((teal | soft).astype(np.uint8))
    mark = cv2.dilate(mark, np.ones((3, 3), np.uint8), iterations=2) & region
    # filled with the suit: the dark cloth from a ring around the box (no teal), smoothed, then the seams inpainted
    ring = np.zeros((h, w), np.uint8)
    rg = int(max(x1 - x0, y1 - y0) * 0.4) + 6
    ring[max(0, y0 - rg):min(h, y1 + rg), max(0, x0 - rg):min(w, x1 + rg)] = 1
    ring &= (1 - region) & (~teal).astype(np.uint8) & (lum < 110).astype(np.uint8) & (im[..., 3] > 200).astype(np.uint8)
    suit = np.median(rgb[ring > 0], axis=0) if ring.any() else np.array([18, 22, 30])
    # filled from the cloth around it: the teal first set to the suit's colour, then the whole mark inpainted
    # (the shading of the cloth flows in from its edges)
    out = im.copy()
    pre = out[..., :3].copy()
    pre[mark > 0] = suit.astype(np.uint8)
    bgr = cv2.cvtColor(pre, cv2.COLOR_RGB2BGR)
    fixed = cv2.inpaint(bgr, mark * 255, 15, cv2.INPAINT_TELEA)
    fixed = cv2.cvtColor(fixed, cv2.COLOR_BGR2RGB).astype(np.float32)
    soft = cv2.GaussianBlur(fixed, (0, 0), 2.5)
    fixed[mark > 0] = soft[mark > 0]
    out[..., :3] = np.clip(fixed, 0, 255).astype(np.uint8)
    # a faint grain so the patch has the cloth's texture
    noise = np.random.default_rng(3).normal(0, 1.6, (h, w, 3))
    patch = mark > 0
    out[..., :3][patch] = np.clip(out[..., :3][patch].astype(np.float32) + noise[patch], 0, 255).astype(np.uint8)
    return out, (int(x0), int(y0), int(x1), int(y1))


def main():
    who, out = sys.argv[1], sys.argv[2]
    specs, preview = {}, None
    for arg in sys.argv[3:]:
        if '=' not in arg:
            preview = arg
            continue
        k, v = arg.split('=', 1)
        xr = None
        if ':' in v:
            v, rng = v.split(':')
            xr = tuple(int(t) for t in rng.split(','))
        specs[k] = (v, xr)
    views, logos = {}, {}
    for k in ('front', 'side', 'back'):
        im = crop_view(*specs[k])
        if k in ('front', 'back'):
            im, logos[k] = remove_logo(im)
            im, _ = remove_logo(im)  # a second look picks up any piece of the mark left over
        s = H / im.shape[0]
        views[k] = cv2.resize(im, (int(round(im.shape[1] * s)), H), interpolation=cv2.INTER_AREA)
    face = crop_view(*specs['face'])
    s = FACE_H / face.shape[0]
    views['face'] = cv2.resize(face, (int(round(face.shape[1] * s)), FACE_H), interpolation=cv2.INTER_AREA)
    W = sum(views[k].shape[1] for k in ('front', 'side', 'back', 'face')) + 4 * 8
    atlas = np.zeros((H, W, 4), np.uint8)
    boxes, x = {}, 0
    for k in ('front', 'side', 'back', 'face'):
        v = views[k]
        atlas[:v.shape[0], x:x + v.shape[1]] = v
        boxes[k] = [x, 0, x + v.shape[1], v.shape[0]]
        x += v.shape[1] + 8
    Image.fromarray(atlas).save(f'{out}/{who}_atlas.webp', quality=92, method=6)
    json.dump({'width': W, 'height': H, 'boxes': boxes}, open(f'{out}/{who}_atlas_boxes.json', 'w'))
    if preview:
        bg = Image.new('RGBA', (W, H), (40, 44, 52, 255))
        bg.alpha_composite(Image.fromarray(atlas))
        bg.convert('RGB').resize((W // 2, H // 2)).save(preview)
    print(who, W, H, boxes, 'logos', logos)


if __name__ == '__main__':
    main()
