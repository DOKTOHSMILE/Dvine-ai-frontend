"""Traces each body view on a character sheet (front, side, back) into its outline, for the app to shape the 3D
body to at start-up. GrabCut, seeded by the sheet's landmarks (a skeleton drawn through them is certainly the
figure; the panel's border is certainly background). Writes looks/<who>_outline.json: per view its box and, every
2 pixel rows, the runs [start, end] (inclusive, sheet pixels) where the figure is.

    python3 tools/sheets/outline.py app/src/main/assets obi [preview.png]
"""
import json, sys
import numpy as np, cv2
from PIL import Image

BONES = [('crown', 'chin'), ('chin', 'neck'), ('neck', 'chest'), ('chest', 'navel'), ('navel', 'hip_l'), ('navel', 'hip_r'),
         ('hip_l', 'knee_l'), ('hip_r', 'knee_r'), ('knee_l', 'ankle_l'), ('knee_r', 'ankle_r'), ('ankle_l', 'toe_l'),
         ('ankle_r', 'toe_r'), ('neck', 'shoulder_l'), ('neck', 'shoulder_r'), ('shoulder_l', 'elbow_l'), ('shoulder_r', 'elbow_r'),
         ('elbow_l', 'wrist_l'), ('elbow_r', 'wrist_r'), ('wrist_l', 'index_l'), ('wrist_r', 'index_r'), ('eye_l', 'eye_r'),
         ('ear_l', 'ear_r'), ('crown', 'nape'), ('nape', 'shoulder_l'), ('nape', 'shoulder_r'), ('head_back', 'nape'),
         ('crown', 'head_back'), ('crown', 'nose'), ('nose', 'chin'), ('ear_l', 'head_back'), ('nape', 'hip_l'),
         ('shoulder_l', 'hip_l'), ('shoulder_r', 'hip_r'), ('chest', 'shoulder_l'), ('navel', 'shoulder_l'), ('hip_l', 'navel')]


def outline(img, box, marks, solid=False):
    x0, y0, x1, y1 = box
    p = img[y0:y1, x0:x1].copy()
    h, w = p.shape[:2]
    mask = np.full((h, w), cv2.GC_PR_BGD, np.uint8)
    band = np.zeros((h, w), np.uint8)
    for a, b in BONES:
        if a in marks and b in marks:
            pa = (int(marks[a][0] - x0), int(marks[a][1] - y0))
            pb = (int(marks[b][0] - x0), int(marks[b][1] - y0))
            cv2.line(band, pa, pb, 1, 16)
    mask[band > 0] = cv2.GC_PR_FGD
    for a, b in BONES:
        if a in marks and b in marks:
            cv2.line(mask, (int(marks[a][0] - x0), int(marks[a][1] - y0)), (int(marks[b][0] - x0), int(marks[b][1] - y0)), cv2.GC_FGD, 4)
    def P(name):
        return np.array(marks[name], np.float32) - np.array([x0, y0], np.float32) if name in marks else None
    # the head: certainly the figure near its middle, probably out to the hair
    crown, chin, el, er = P('crown'), P('chin'), P('ear_l'), P('ear_r')
    if crown is not None and chin is not None:
        hh = float(chin[1] - crown[1])
        hw = float(abs(el[0] - er[0])) if el is not None and er is not None else hh * 0.8
        c = (crown + chin) / 2
        cv2.ellipse(mask, (int(c[0]), int(c[1] - hh * 0.12)), (int(hw * 0.75), int(hh * 0.72)), 0, 0, 360, cv2.GC_PR_FGD, -1)
        cv2.ellipse(mask, (int(c[0]), int(c[1])), (int(hw * 0.32), int(hh * 0.36)), 0, 0, 360, cv2.GC_FGD, -1)
    # the gaps: between the legs below the crotch, and between each arm and the body
    for l, r in (('knee_l', 'knee_r'), ('ankle_l', 'ankle_r')):
        a, b = P(l), P(r)
        if a is not None and b is not None:
            mid = (a + b) / 2
            gap = abs(a[0] - b[0])
            cv2.line(mask, (int(mid[0]), int(mid[1] - gap * 0.6)), (int(mid[0]), int(mid[1] + gap * 0.5)), cv2.GC_BGD, 3)
    hl, hr, kl, kr = P('hip_l'), P('hip_r'), P('knee_l'), P('knee_r')
    if hl is not None and hr is not None and kl is not None and kr is not None:
        top = (hl + hr) / 2 + ((kl + kr) / 2 - (hl + hr) / 2) * 0.45
        bot = (P('ankle_l') + P('ankle_r')) / 2 if P('ankle_l') is not None and P('ankle_r') is not None else (kl + kr) / 2
        cv2.line(mask, (int(top[0]), int(top[1])), (int(bot[0]), int(bot[1] + 30)), cv2.GC_BGD, 3)
    for side in ('l', 'r'):
        e, w_, h = P('elbow_' + side), P('wrist_' + side), P('hip_' + side)
        if e is None or w_ is None or h is None:
            continue
        for t in np.linspace(0.15, 0.85, 8):
            arm = e + (w_ - e) * t
            body_x = h[0] + (arm[0] - h[0]) * 0.18
            x = arm[0] + (body_x - arm[0]) * 0.55
            cv2.circle(mask, (int(x), int(arm[1])), 1, cv2.GC_BGD, -1)
    mask[:, :3] = mask[:, -3:] = mask[:2, :] = cv2.GC_BGD
    bgd, fgd = np.zeros((1, 65)), np.zeros((1, 65))
    cv2.grabCut(p, mask, None, bgd, fgd, 8, cv2.GC_INIT_WITH_MASK)
    m = ((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD)).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(m, 8)
    if n > 1:
        m = (lab == 1 + np.argmax(st[1:, cv2.CC_STAT_AREA])).astype(np.uint8)
    inv = 1 - m
    n2, lab2, st2, _ = cv2.connectedComponentsWithStats(inv, 4)
    for i in range(1, n2):
        x, y, ww, hh = st2[i, :4]
        enclosed = x > 0 and y > 0 and x + ww < m.shape[1] and y + hh < m.shape[0]
        # pin-holes only: the gaps between legs and arms stay open (a profile has none: every enclosed hole goes)
        if st2[i, cv2.CC_STAT_AREA] < 250 or solid and enclosed:
            m[lab2 == i] = 1
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    return m


def runs(m, x0, y0):
    rows = {}
    for y in range(0, m.shape[0], 2):
        r = m[y]
        d = np.diff(np.concatenate([[0], r, [0]]))
        s, e = np.where(d == 1)[0], np.where(d == -1)[0] - 1
        segs = [[int(a + x0), int(b + x0)] for a, b in zip(s, e) if b - a >= 1]
        if segs:
            rows[str(y + y0)] = segs
    return rows


def main(assets, who, preview=None):
    cal = json.load(open(f'{assets}/looks/{who}.json'))
    img = cv2.cvtColor(np.asarray(Image.open(f'{assets}/{cal["sheet"]}').convert('RGB')), cv2.COLOR_RGB2BGR)
    out, shots = {}, []
    for view in ('front', 'side', 'back'):
        box = cal['boxes'][view]
        m = outline(img, box, cal['views'][view], view == 'side')
        out[view] = {'box': box, 'rows': runs(m, box[0], box[1])}
        p = cv2.cvtColor(img[box[1]:box[3], box[0]:box[2]], cv2.COLOR_BGR2RGB)
        vis = (p * 0.45).astype(np.uint8)
        edge = m - cv2.erode(m, np.ones((3, 3), np.uint8))
        vis[m > 0] = (p[m > 0] * 0.8).astype(np.uint8)
        vis[edge > 0] = [80, 255, 190]
        shots.append(Image.fromarray(vis))
    json.dump(out, open(f'{assets}/looks/{who}_outline.json', 'w'), separators=(',', ':'))
    if preview:
        W = sum(s.width for s in shots)
        sheet = Image.new('RGB', (W, max(s.height for s in shots)))
        x = 0
        for s in shots:
            sheet.paste(s, (x, 0))
            x += s.width
        sheet.save(preview)


if __name__ == '__main__':
    main(*sys.argv[1:])
