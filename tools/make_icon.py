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

HEART = "M54,77.6C49.3,74.4 49.6,70.4 51.9,70.4C53.1,70.4 53.8,71.3 54,72C54.2,71.3 54.9,70.4 56.1,70.4C58.4,70.4 58.7,74.4 54,77.6z"
HEAD = rrect(31, 32, 46, 34, 16)
VISOR = rrect(36, 38, 36, 23, 11)
BODY = "M45.5,66h17a5,5 0,0 1,5 5v4a9,9 0,0 1,-9 9h-9a9,9 0,0 1,-9 -9v-4a5,5 0,0 1,5 -5z"

# ---- background: nebula night, a tilted orbit with a planet, twinkling stars
b = grad("M0,0h108v108h-108z", "linear", 'android:startX="0" android:startY="0" android:endX="108" android:endY="108"',
         [(0, "#FF33207A"), (0.5, "#FF1A1450"), (1, "#FF0B0A26")])
b += grad(circ(22, 84, 38), "radial", 'android:centerX="22" android:centerY="84" android:gradientRadius="38"',
          [(0, "#66FF5FB0"), (1, "#00FF5FB0")])
b += grad(circ(88, 20, 40), "radial", 'android:centerX="88" android:centerY="20" android:gradientRadius="40"',
          [(0, "#5539E6FF"), (1, "#0039E6FF")])
b += grad(circ(54, 54, 36), "radial", 'android:centerX="54" android:centerY="52" android:gradientRadius="36"',
          [(0, "#807C6BFF"), (0.6, "#2A5B4BDB"), (1, "#005B4BDB")])
# a tilted orbit (an ellipse drawn as a rotated group) and its little planet
b += ('    <group android:rotation="-18" android:pivotX="54" android:pivotY="54">\n'
      + stroke(ell(54, 54, 41, 15), "#4039E6FF", 0.9)
      + fill(circ(13.5, 54, 2.6), "#FFFF8FC8")
      + fill(circ(12.7, 53.2, 0.9), "#AAFFFFFF")
      + '    </group>\n')
for (x, y, r, c) in [(18, 20, 2.4, "#FFFFFFFF"), (92, 86, 2.0, "#DDB7F0FF"), (86, 44, 1.3, "#CCFFFFFF"),
                     (16, 46, 1.4, "#CC9FF3FF"), (30, 96, 1.2, "#AAFFB7DD"), (72, 12, 1.1, "#AAFFFFFF")]:
    b += fill(star(x, y, r), c)
for (x, y, r, c) in [(40, 14, 0.6, "#99FFFFFF"), (98, 60, 0.7, "#8839E6FF"), (10, 70, 0.6, "#88FFFFFF"),
                     (62, 98, 0.6, "#88B7A8FF"), (96, 30, 0.5, "#88FFFFFF"), (24, 30, 0.5, "#77FFFFFF")]:
    b += fill(circ(x, y, r), c)

# ---- foreground: the robot, everything inside the 66-unit safe circle
f = ""
# antenna: halo, stem, a glowing star tip with its own sparkle
f += fill(circ(54, 25.2, 4.0), "#4439E6FF")
f += stroke("M54,29L54,32.5", "#FFC9BEFF", 2.2)
f += fill(star(54, 25.2, 3.4), "#FF39E6FF")
f += fill(star(54, 25.2, 1.6), "#FFFFFFFF")
# body behind the head: tiny round body with a glowing heart core
f += grad(BODY, "linear", 'android:startX="54" android:startY="66" android:endX="54" android:endY="84"',
          [(0, "#FFF2EFFF"), (1, "#FFC9C2F2")])
f += fill(circ(54, 74, 5.2), "#33FF5FB0")
f += fill(HEART, "#FFFF6FB5")
f += fill(circ(52.4, 72, 0.7), "#CCFFFFFF")
# arms: one resting, one waving, with little motion lines
f += stroke("M45,70.5L39.5,76.5", "#FF8B7CFF", 3.2)
f += fill(circ(38.6, 77.6, 2.7), "#FFF2EFFF")
f += stroke("M63,70.5L71.5,65.5", "#FF8B7CFF", 3.2)
f += fill(circ(73, 64.4, 2.8), "#FFF2EFFF")
f += stroke("M77.2,58.2q2.2,1.6 2.2,4.4", "#CC39E6FF", 1.1)
f += stroke("M79.7,55.6q3.4,2.6 3.2,7", "#8839E6FF", 1.0)
# headphone ear pods with glowing rings
for cx in (28.2, 79.8):
    f += grad(circ(cx, 49, 6), "radial", f'android:centerX="{cx}" android:centerY="47.5" android:gradientRadius="6.5"',
              [(0, "#FFA99CFF"), (1, "#FF6E5CE8")])
    f += stroke(circ(cx, 49, 3.4), "#FF39E6FF", 1.2)
    f += fill(circ(cx, 49, 1.3), "#FFBFF7FF")
# head: glossy white with a soft lilac shade and a rim of light
f += grad(HEAD, "linear", 'android:startX="40" android:startY="32" android:endX="66" android:endY="66"',
          [(0, "#FFFFFFFF"), (0.65, "#FFEAE6FF"), (1, "#FFC6BEF2")])
f += stroke("M37.5,41.5q2.5,-6 9.5,-7.4", "#FFFFFFFF", 1.8)
f += fill(circ(52.5, 34.6, 0.9), "#FFFFFFFF")
# visor: a deep screen with a soft inner glow
f += grad(VISOR, "linear", 'android:startX="54" android:startY="38" android:endX="54" android:endY="61"',
          [(0, "#FF2A2468"), (1, "#FF0D0C2B")])
f += grad(rrect(37.5, 39.5, 33, 20, 9.5), "radial", 'android:centerX="54" android:centerY="50" android:gradientRadius="18"',
          [(0, "#3339E6FF"), (1, "#0039E6FF")])
f += stroke("M40.5,42.5q4,-2.2 9,-2.4", "#5539E6FF", 0.9)
# eyes: big, shining, kawaii (glow, iris, darker centre, two highlights)
for cx in (46, 62):
    f += fill(circ(cx, 49.4, 6.4), "#4439E6FF")
    f += grad(ell(cx, 49.4, 5.0, 5.4), "radial", f'android:centerX="{cx}" android:centerY="51" android:gradientRadius="6"',
              [(0, "#FF9CF7FF"), (0.55, "#FF39E6FF"), (1, "#FF1A9FD6")])
    f += fill(ell(cx, 50.2, 2.5, 2.9), "#FF0E5A8C")
    f += fill(circ(cx + 1.6, 47.4, 1.75), "#FFFFFFFF")
    f += fill(circ(cx - 1.7, 52.0, 0.85), "#EEFFFFFF")
# cat mouth and blush with a twinkle
f += stroke("M50.6,56.2q1.7,1.9 3.4,0q1.7,1.9 3.4,0", "#FF39E6FF", 1.4)
for cx in (40.4, 67.6):
    f += fill(ell(cx, 56.3, 2.5, 1.5), "#AAFF7AB6")
f += fill(star(69.3, 54.8, 0.9), "#DDFFFFFF")

# ---- one colour, for themed icons
m = fill(star(54, 25.2, 3.4), "#FF000000")
m += stroke("M54,29L54,32.5", "#FF000000", 2.2)
m += fill(circ(28.2, 49, 6), "#FF000000") + fill(circ(79.8, 49, 6), "#FF000000")
m += fill(BODY, "#FF000000")
m += stroke("M45,70.5L39.5,76.5", "#FF000000", 3.2) + fill(circ(38.6, 77.6, 2.7), "#FF000000")
m += stroke("M63,70.5L71.5,65.5", "#FF000000", 3.2) + fill(circ(73, 64.4, 2.8), "#FF000000")
m += fill(HEAD + " " + VISOR, "#FF000000", ' android:fillType="evenOdd"')
for cx in (46, 62):
    m += fill(ell(cx, 49.4, 5.0, 5.4), "#FF000000")
m += stroke("M50.6,56.2q1.7,1.9 3.4,0q1.7,1.9 3.4,0", "#FF000000", 1.4)

open(f"{RES}/drawable/ic_launcher_background.xml", "w").write(vector(
    "Ibiko's icon, back layer: a nebula night (pink and cyan clouds), a tilted orbit with a tiny planet, twinkling stars.", b))
open(f"{RES}/drawable/ic_launcher_foreground.xml", "w").write(vector(
    "Ibiko's icon, front layer: a cute robot with a big glossy head, a star antenna, headphone ear pods, big shining"
    " eyes, a cat mouth and blush, a tiny body with a glowing heart, one arm waving. All inside the 66-unit safe circle.", f))
open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w").write(vector(
    "Ibiko's icon in one colour, for themed icons (Android 13+).", m,
    ns='xmlns:android="http://schemas.android.com/apk/res/android"'))
print("ok")
