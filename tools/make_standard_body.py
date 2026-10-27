#!/usr/bin/env python3
"""Bakes the standard human body the app builds Obi and Ivie from.

Source: the MakeHuman base mesh (hm08) and its macro targets, released by the
MakeHuman team as CC0 1.0 (public domain) in September 2020; every source file
carries that notice. Only the 'body' surface (one closed shell) and the joint
helper centroids are kept; MakeHuman's eyes, teeth, tongue, hair and clothes
helpers are dropped.

Each persona is the base plus the young male or female macro shape (the mean
of MakeHuman's three ethnic targets, its default), an amount of muscle and of
ideal proportions. The output is one binary resource shared by both personas:

  'DVSB' u32 version
  u32 nv, u32 nt, u16 tri[nt*3]               shared topology (quads split)
  u32 npersona, per persona: str name, f32 pos[nv*3]
  u32 njoint,   per joint:   str name, then f32 xyz per persona
  u8 bone0[nv], u8 bone1[nv], f32 weight0[nv]   MakeHuman's default skin, folded into DVINE's bones
  str notice
(little endian; str = u16 byte length + UTF-8)

Usage: make_standard_body.py <download-cache-dir> <out.bin>
Files are fetched from the MakeHuman repository when missing and must match
the pinned SHA-256 sums.
"""
import hashlib
import os
import struct
import sys
import urllib.request

BASE = "https://raw.githubusercontent.com/makehumancommunity/makehuman/master/makehuman/data/"
FILES = {
    "3dobjs/base.obj": "8e761e6624b8f54536409135d1636da63b32486a90d4897f84e121d144f6fb4c",
    "targets/macrodetails/caucasian-male-young.target": "70e228ba7164737dae664454394536fc5935fa48d333c1a97d77e2dc6eacc5f5",
    "targets/macrodetails/african-male-young.target": "894abc1fbb3d28543a51fef16f89d5d4bdf9aa2e1534413339811a3d47818b7d",
    "targets/macrodetails/asian-male-young.target": "ed2e8c191cb6b87b4a2d97c80486acb2604fa549316c7aa2a738a5d5a14334dc",
    "targets/macrodetails/caucasian-female-young.target": "118379f6e8ba9266247fdb8788a20e1df40a239f97ced0b9905bcbcc74f6e820",
    "targets/macrodetails/african-female-young.target": "92d61eeb3c164b421fd5a7c3537ee45e7e1a51de4d49bf312e19c3df2be1d8fc",
    "targets/macrodetails/asian-female-young.target": "095fe79694fa19e1fe98d93009ec116199bd524e081c640351a10eccf2cca1eb",
    "targets/macrodetails/universal-male-young-maxmuscle-averageweight.target": "c171ff9cc95e96273beb3e3b1969a01988c2d4982ebc6fe04bf81cf72ee3b295",
    "targets/macrodetails/universal-female-young-maxmuscle-averageweight.target": "2e56b09b44a8497b927585447fdaac5ea7293521d1f3a3df1059495873c53f4e",
    "targets/macrodetails/proportions/male-young-averagemuscle-averageweight-idealproportions.target": "7bbd790604c908417ff8ca11e566e3577508d92a4f397a2ace0f89f4cc3aa373",
    "targets/macrodetails/proportions/female-young-averagemuscle-averageweight-idealproportions.target": "0a3bd099b7a88150d56d68b69c07d3d3538cb57279e0e775d13f19d3dc3e339d",
    "rigs/default_weights.mhw": "0f3641d651ae3d00ad6b4ccee43142edb109d3bd909d27d9e4139ef1beed8625",
}
# DVINE body bones (DotBody / Skeleton order) and the MakeHuman default-rig bones that fold into each.
# MakeHuman's .L side is +X, the persona's left, as in DVINE. Fingers: two segments each, thumb first.
OUR_BONES = ["pelvis", "spine", "chest", "neck", "head", "arm.L", "fore.L", "hand.L", "arm.R", "fore.R", "hand.R",
             "thigh.L", "shin.L", "foot.L", "thigh.R", "shin.R", "foot.R"]
for side in "LR":
    for f in range(5):
        OUR_BONES += ["f%d-1.%s" % (f, side), "f%d-2.%s" % (f, side)]
CENTRAL = {"root": "pelvis", "pelvis": "pelvis", "spine05": "pelvis", "special04": "pelvis",
           "spine04": "spine", "spine03": "spine", "spine02": "chest", "spine01": "chest",
           "breast": "chest", "clavicle": "chest", "shoulder01": "chest",
           "neck01": "neck", "neck02": "neck", "neck03": "neck",
           "upperarm01": "arm", "upperarm02": "arm", "lowerarm01": "fore", "lowerarm02": "fore",
           "wrist": "hand", "metacarpal1": "hand", "metacarpal2": "hand", "metacarpal3": "hand", "metacarpal4": "hand",
           "upperleg01": "thigh", "upperleg02": "thigh", "lowerleg01": "shin", "lowerleg02": "shin", "foot": "foot"}


def our_bone(mh):
    """The DVINE bone a MakeHuman bone folds into (the head's many face bones all become the head)."""
    name, _, side = mh.partition(".")
    if name.startswith("finger"):
        n, seg = int(name[6]), int(name[8])
        if n == 1:
            return "f0-%d.%s" % (1 if seg == 1 else 2, side)
        return "f%d-%d.%s" % (n - 1, 1 if seg == 1 else 2, side)
    if name.startswith("toe"):
        return "foot." + side
    if name in CENTRAL:
        b = CENTRAL[name]
        return b + "." + side if side and b not in ("pelvis", "chest") else b
    if name in ("special05",):
        return "head"
    return "head"   # face, eye, jaw and tongue bones
# persona -> (sex, muscle target weight, ideal-proportions weight)
PERSONAS = [("male", "male", 0.45, 1.0), ("female", "female", 0.0, 0.8)]
NOTICE = ("Body surface derived from the MakeHuman base mesh hm08 and macro targets, "
          "released as CC0 1.0 by Data Collection AB, Joel Palmius, Jonas Hauquier and Manuel Bastioni "
          "(http://www.makehumancommunity.org).")


def fetch(cache, rel):
    path = os.path.join(cache, rel)
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with urllib.request.urlopen(BASE + rel, timeout=60) as r, open(path, "wb") as out:
            out.write(r.read())
    data = open(path, "rb").read()
    got = hashlib.sha256(data).hexdigest()
    if got != FILES[rel]:
        raise SystemExit("checksum mismatch for %s: %s" % (rel, got))
    return data.decode("utf-8")


def read_obj(text):
    verts, groups, group = [], {}, None
    for line in text.splitlines():
        if line.startswith("v "):
            verts.append([float(x) for x in line.split()[1:4]])
        elif line.startswith("g "):
            group = line.split()[1]
        elif line.startswith("f "):
            groups.setdefault(group, []).append([int(t.split("/")[0]) - 1 for t in line.split()[1:]])
    return verts, groups


def apply(verts, text, w):
    for line in text.splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        i, dx, dy, dz = line.split()
        v = verts[int(i)]
        v[0] += w * float(dx)
        v[1] += w * float(dy)
        v[2] += w * float(dz)


def put_str(out, s):
    b = s.encode("utf-8")
    out.write(struct.pack("<H", len(b)))
    out.write(b)


def main():
    cache, dest = sys.argv[1], sys.argv[2]
    base, groups = read_obj(fetch(cache, "3dobjs/base.obj"))
    body = groups["body"]
    used = sorted({i for f in body for i in f})
    remap = {v: k for k, v in enumerate(used)}
    tris = []
    for f in body:
        f = [remap[i] for i in f]
        for k in range(1, len(f) - 1):
            tris.append((f[0], f[k], f[k + 1]))
    joints = sorted(g for g in groups if g.startswith("joint-")) + ["joint-l-eyeball", "joint-l-eyeball-top",
                                                                    "joint-r-eyeball", "joint-r-eyeball-top"]
    import json
    mhw = json.loads(fetch(cache, "rigs/default_weights.mhw"))["weights"]
    acc = [dict() for _ in used]
    for mh, pairs in mhw.items():
        b = OUR_BONES.index(our_bone(mh))
        for i, w in pairs:
            if i in remap:
                d = acc[remap[i]]
                d[b] = d.get(b, 0.0) + w
    skin = []
    for d in acc:
        top = sorted(d.items(), key=lambda kv: -kv[1])[:2]
        if not top:
            raise SystemExit("a body vertex has no skin weight")
        b0, w0 = top[0]
        b1, w1 = top[1] if len(top) > 1 else (b0, 0.0)
        skin.append((b0, b1, w0 / (w0 + w1)))
    shapes = []
    for name, sex, muscle, ideal in PERSONAS:
        v = [list(p) for p in base]
        for e in ("caucasian", "african", "asian"):
            apply(v, fetch(cache, "targets/macrodetails/%s-%s-young.target" % (e, sex)), 1.0 / 3.0)
        apply(v, fetch(cache, "targets/macrodetails/universal-%s-young-maxmuscle-averageweight.target" % sex), muscle)
        apply(v, fetch(cache, "targets/macrodetails/proportions/%s-young-averagemuscle-averageweight-idealproportions.target"
                       % sex), ideal)
        centre = {}
        for j in joints:
            if j not in groups:
                continue
            ids = sorted({i for f in groups[j] for i in f})
            centre[j] = [sum(v[i][k] for i in ids) / len(ids) for k in range(3)]
        # the eyeballs: centre and top of MakeHuman's eye helpers (their size)
        for side in "lr":
            ids = sorted({i for f in groups["helper-%s-eye" % side] for i in f})
            centre["joint-%s-eyeball" % side] = [sum(v[i][k] for i in ids) / len(ids) for k in range(3)]
            top = max(ids, key=lambda i: v[i][1])
            centre["joint-%s-eyeball-top" % side] = list(v[top])
        shapes.append((name, [v[i] for i in used], centre))
    with open(dest, "wb") as out:
        out.write(b"DVSB")
        out.write(struct.pack("<III", 2, len(used), len(tris)))
        for t in tris:
            out.write(struct.pack("<HHH", *t))
        out.write(struct.pack("<I", len(shapes)))
        for name, pos, _ in shapes:
            put_str(out, name)
            for p in pos:
                out.write(struct.pack("<fff", *p))
        out.write(struct.pack("<I", len(joints)))
        for j in joints:
            put_str(out, j[len("joint-"):])
            for _, _, centre in shapes:
                out.write(struct.pack("<fff", *centre[j]))
        for k in range(len(used)):
            out.write(struct.pack("<B", skin[k][0]))
        for k in range(len(used)):
            out.write(struct.pack("<B", skin[k][1]))
        for k in range(len(used)):
            out.write(struct.pack("<f", skin[k][2]))
        put_str(out, NOTICE)
    print("%s: %d vertices, %d triangles, %d joints, %d bytes" % (dest, len(used), len(tris), len(joints), os.path.getsize(dest)))


if __name__ == "__main__":
    main()
