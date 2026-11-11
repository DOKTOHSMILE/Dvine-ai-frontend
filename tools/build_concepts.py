"""Builds Ibiko's common-sense links, downloads/knowledge/concepts-<version>.tsv.gz, from ConceptNet 5.7
(https://conceptnet.io, CC BY-SA 4.0; this file is shared under the same licence).

Run from the repository root:  python3 tools/build_concepts.py [filtered.tsv]
Without an argument it streams the 500 MB dump and keeps only English links between short concepts, of the relations
that carry common sense (what a thing is, is for, can do, has, is part of, where it is, what it causes or needs, why
people do things), weight 1 or more. Then: the strongest 12 links of each relation for each concept (8 for IsA; 40
parts and possessions), the
reverse of PartOf added as HasPart, sorted by concept for ibiko1/comprehend's binary search.
The file is not in the app: the app downloads it on first start ("Downloading app files", ai.ibiko.main.AppFiles)
and unpacks it to brain/knowledge/concepts.tsv. After a rebuild, set AppFiles.CONCEPTS_VERSION and CONCEPTS_SHA256
(printed below) to the new file.
One line a link:  concept <TAB> relation <TAB> other end
"""
import gzip
import io
import json
import sys
import urllib.request
from collections import defaultdict

URL = "https://s3.amazonaws.com/conceptnet/downloads/2019/edges/conceptnet-assertions-5.7.0.csv.gz"
VERSION = "2026-11-11"
OUT = "downloads/knowledge/concepts-" + VERSION + ".tsv.gz"
REL = {"IsA", "PartOf", "HasA", "UsedFor", "CapableOf", "AtLocation", "Causes", "HasPrerequisite", "HasProperty",
       "MotivatedByGoal", "Desires", "CreatedBy", "MadeOf", "ReceivesAction", "HasSubevent", "CausesDesire", "LocatedNear",
       "HasFirstSubevent", "HasLastSubevent"}


def filtered():
    if len(sys.argv) > 1:
        for line in open(sys.argv[1], encoding="utf-8"):
            a, rel, b, w = line.rstrip("\n").split("\t")
            yield a, rel, b, float(w)
        return
    stream = gzip.GzipFile(fileobj=urllib.request.urlopen(URL))
    for raw in io.TextIOWrapper(stream, encoding="utf-8"):
        p = raw.rstrip("\n").split("\t")
        if len(p) < 5:
            continue
        rel = p[1][3:]
        if rel not in REL or not p[2].startswith("/c/en/") or not p[3].startswith("/c/en/"):
            continue
        w = json.loads(p[4]).get("weight", 1.0)
        a, b = p[2].split("/")[3].replace("_", " "), p[3].split("/")[3].replace("_", " ")
        if w >= 1.0 and a != b and len(a.split()) <= 3 and len(b.split()) <= 4:
            yield a, rel, b, w


best = defaultdict(list)
for a, rel, b, w in filtered():
    best[(a, rel)].append((w, b))
    if rel == "PartOf":
        best[(b, "HasPart")].append((w, a))
rows = []
for (a, rel), ends in best.items():
    keep = 8 if rel == "IsA" else 40 if rel in ("HasPart", "HasA", "PartOf") else 12  # a car has dozens of parts
    seen = set()
    for w, b in sorted(ends, key=lambda x: -x[0]):
        if b in seen:
            continue
        seen.add(b)
        rows.append((a, rel, b))
        if len(seen) >= keep:
            break
rows.sort(key=lambda r: (r[0].encode("utf-8"), r[1]))
import hashlib
with open(OUT, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0, filename="") as f:
    for r in rows:
        f.write(("\t".join(x.replace("\t", " ") for x in r) + "\n").encode("utf-8"))
print(OUT, len(rows), "links,", len({r[0] for r in rows}), "concepts")
print("sha256", hashlib.sha256(open(OUT, "rb").read()).hexdigest())
