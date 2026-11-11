# Writes Ibiko's launcher icon layers (background, foreground, monochrome) as Android vectors.
# Run: python3 tools/make_icon.py app/src/main/res   then LauncherIconTest renders the PNGs into app/build/icons.
import sys
RES = sys.argv[1]
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:aapt="http://schemas.android.com/aapt"'

def circ(cx, cy, r):
    return f"M{cx},{cy}m-{r},0a{r},{r} 0,1 1,{2*r},0a{r},{r} 0,1 1,-{2*r},0"
def ell(cx, cy, rx, ry):
    return f"M{cx-rx},{cy}a{rx},{ry} 0,1 1,{2*rx},0a{rx},{ry} 0,1 1,-{2*rx},0"
def rrect(x, y, w, h, r):
    return (f"M{x+r},{y}h{w-2*r}a{r},{r} 0,0 1,{r},{r}v{h-2*r}a{r},{r} 0,0 1,-{r},{r}"
            f"h-{w-2*r}a{r},{r} 0,0 1,-{r},-{r}v-{h-2*r}a{r},{r} 0,0 1,{r},-{r}z")
def star(cx, cy, r, waist=0.0):
    return (f"M{cx},{cy-r}Q{cx},{cy} {cx+r},{cy}Q{cx},{cy} {cx},{cy+r}Q{cx},{cy} {cx-r},{cy}Q{cx},{cy} {cx},{cy-r}z")
def fill(d, color, extra=""):
    return f'    <path android:fillColor="{color}" android:pathData="{d}"{extra} />\n'
def stroke(d, color, w, extra=""):
    return (f'    <path android:fillColor="#00000000" android:strokeColor="{color}" android:strokeWidth="{w}"'
            f' android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{d}"{extra} />\n')
def grad(d, kind, attrs, stops):
    items = "".join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in stops)
    return (f'    <path android:pathData="{d}">\n        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="{kind}" {attrs}>\n{items}            </gradient>\n'
            f'        </aapt:attr>\n    </path>\n')
def vector(comment, body, ns=NS):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} -->\n<vector {ns}\n'
            '    android:width="108dp" android:height="108dp"\n    android:viewportWidth="108" android:viewportHeight="108">\n'
            + body + '</vector>\n')

import math

def arc(cx, cy, r, a0, a1):
    x0, y0 = cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M{x0:.2f},{y0:.2f}A{r},{r} 0,{large} 1,{x1:.2f},{y1:.2f}"

# one petal: a swirling lens from the centre out to its tip (rotated three times about the centre)
PETAL = "M54,54C43.5,48.5 44,33.5 56.5,25.5C62.5,36.5 61,47.5 54,54z"
PETALS = [  # rotation, gradient from the centre to the tip
    (0, "#E67C5CFF", "#E639E6FF"),
    (120, "#E6FF5FB0", "#E67C5CFF"),
    (240, "#E639E6FF", "#E62FD8A8"),
]

# ---- background: deep ink with a faint dot grain and a soft bloom of light behind the mark
b = grad("M0,0h108v108h-108z", "linear", 'android:startX="0" android:startY="0" android:endX="108" android:endY="108"',
         [(0, "#FF1C1838"), (1, "#FF0A0A16")])
grain = ""
for gy in range(6, 108, 8):
    for gx in range(6 + (4 if (gy // 8) % 2 else 0), 108, 8):
        d = math.hypot(gx - 54, gy - 54)
        if d > 20:
            grain += circ(gx, gy, 0.45)
b += fill(grain, "#14FFFFFF")
b += grad(circ(54, 54, 36), "radial", 'android:centerX="54" android:centerY="54" android:gradientRadius="36"',
          [(0, "#555B4BDB"), (0.7, "#145B4BDB"), (1, "#005B4BDB")])

# ---- foreground: three petals swirling round a spark, inside a broken orbit (all within the safe circle)
f = ""
for (a0, a1, c) in [(-80, 10, "#B37C5CFF"), (40, 130, "#B3FF5FB0"), (160, 250, "#B339E6FF")]:
    f += stroke(arc(54, 54, 30.5, a0, a1), c, 1.3)
for ang in (10, 130, 250):  # a bead at the end of each arc
    x, y = 54 + 30.5 * math.cos(math.radians(ang)), 54 + 30.5 * math.sin(math.radians(ang))
    f += fill(circ(round(x, 2), round(y, 2), 1.5), "#FFFFFFFF")
for rot, c0, c1 in PETALS:
    tip_x = 54 + 28.5 * math.sin(math.radians(rot + 5))
    tip_y = 54 - 28.5 * math.cos(math.radians(rot + 5))
    f += (f'    <group android:rotation="{rot}" android:pivotX="54" android:pivotY="54">\n'
          + grad(PETAL, "linear", 'android:startX="54" android:startY="54" android:endX="56" android:endY="26"',
                 [(0, c0), (1, c1)])
          + stroke("M54,54C47.5,47 48.5,36 56.5,25.5", "#40FFFFFF", 0.7)
          + '    </group>\n')
f += fill(circ(54, 54, 7.5), "#33FFFFFF")
f += fill(star(54, 54, 5.6), "#FFFFFFFF")
f += fill(circ(54, 54, 1.6), "#FFFFF4FB")

# ---- one colour, for themed icons
m = ""
for (a0, a1) in [(-80, 10), (40, 130), (160, 250)]:
    m += stroke(arc(54, 54, 30.5, a0, a1), "#FF000000", 1.3)
for rot, _, _ in PETALS:
    m += (f'    <group android:rotation="{rot}" android:pivotX="54" android:pivotY="54">\n'
          + fill(PETAL, "#FF000000") + '    </group>\n')

open(f"{RES}/drawable/ic_launcher_background.xml", "w").write(vector(
    "Ibiko's icon, back layer: deep ink with a faint dot grain and a soft bloom of light behind the mark.", b))
open(f"{RES}/drawable/ic_launcher_foreground.xml", "w").write(vector(
    "Ibiko's icon, front layer: Ibiko's own mark, three translucent petals of light swirling round a spark (they blend"
    " where they cross), inside a broken orbit with a bead at each end. All inside the 66-unit safe circle.", f))
open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w").write(vector(
    "Ibiko's icon in one colour, for themed icons (Android 13+).", m,
    ns='xmlns:android="http://schemas.android.com/apk/res/android"'))
print("ok")
