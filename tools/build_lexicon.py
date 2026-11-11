"""Builds Ibiko's offline dictionary, ibiko1/src/main/assets/knowledge/lexicon.tsv, from Open English WordNet 2024
(https://github.com/globalwordnet/english-wordnet, CC BY 4.0).

Run from the repository root:  python3 tools/build_lexicon.py [english-wordnet-2024.xml.gz]   (downloads it if not given)
One line a sense, sorted by word (dvine1/comprehend reads it by binary search, never all at once):
  word <TAB> part of speech <TAB> definition <TAB> synonyms <TAB> opposites <TAB> broader term
At most three senses for each word and part of speech, the most common first, as WordNet orders them.
"""
import gzip
import os
import sys
import urllib.request
import xml.etree.ElementTree as ET

URL = "https://github.com/globalwordnet/english-wordnet/releases/download/2024-edition/english-wordnet-2024.xml.gz"
OUT = "ibiko1/src/main/assets/knowledge/lexicon.tsv"
POS = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}
src = sys.argv[1] if len(sys.argv) > 1 else "/tmp/english-wordnet-2024.xml.gz"
if not os.path.exists(src):
    urllib.request.urlretrieve(URL, src)

entries = []          # (lemma, pos, [sense ids], [synset ids])
sense_lemma = {}      # sense id -> lemma
sense_ant = {}        # sense id -> [antonym sense ids]
synset_def, synset_members, synset_hyper = {}, {}, {}
lemma = pos = None
senses, synsets = [], []
for ev, el in ET.iterparse(gzip.open(src), events=("start", "end")):
    tag = el.tag
    if ev == "start" and tag == "LexicalEntry":
        senses, synsets = [], []
    elif ev == "end" and tag == "Lemma":
        lemma, pos = el.get("writtenForm"), el.get("partOfSpeech")
    elif ev == "end" and tag == "Sense":
        sid = el.get("id")
        senses.append(sid)
        synsets.append(el.get("synset"))
        sense_lemma[sid] = lemma
        ants = [r.get("target") for r in el.findall("SenseRelation") if r.get("relType") == "antonym"]
        if ants:
            sense_ant[sid] = ants
    elif ev == "end" and tag == "LexicalEntry":
        entries.append((lemma, pos, senses, synsets))
        el.clear()
    elif ev == "end" and tag == "Synset":
        sid = el.get("id")
        d = el.find("Definition")
        synset_def[sid] = (d.text or "").strip() if d is not None else ""
        synset_members[sid] = (el.get("members") or "").split()
        hyp = [r.get("target") for r in el.findall("SynsetRelation") if r.get("relType") in ("hypernym", "instance_hypernym")]
        if hyp:
            synset_hyper[sid] = hyp[0]
        el.clear()

# a synset's member entries -> their lemmas
entry_lemma = {}
for lem, p, ss, sy in entries:
    pass
member_lemma = {}
for lem, p, ss, sy in entries:
    for s, y in zip(ss, sy):
        member_lemma.setdefault(y, [])
        if lem not in member_lemma[y]:
            member_lemma[y].append(lem)


def clean(t):
    return (t or "").replace("\t", " ").replace("\n", " ").strip()


rows = []
for lem, p, ss, sy in entries:
    if not lem or not p or lem.startswith(".") or len(lem) > 40:
        continue
    for s, y in list(zip(ss, sy))[:3]:
        d = clean(synset_def.get(y, ""))
        if not d:
            continue
        if len(d) > 220:
            d = d[:217].rsplit(" ", 1)[0] + "…"
        syn = [m for m in member_lemma.get(y, []) if m.lower() != lem.lower()][:6]
        ant = [sense_lemma[a] for a in sense_ant.get(s, []) if a in sense_lemma][:3]
        h = synset_hyper.get(y)
        broader = (member_lemma.get(h) or [""])[0] if h else ""
        rows.append((lem.lower(), POS.get(p, p), d, ", ".join(syn), ", ".join(ant), broader))
rows.sort(key=lambda r: r[0].encode("utf-8"))  # byte order: the app's binary search compares bytes
with open(OUT, "w", encoding="utf-8") as f:
    for r in rows:
        f.write("\t".join(clean(x) for x in r) + "\n")
print(OUT, len(rows), "senses,", len({r[0] for r in rows}), "words")
