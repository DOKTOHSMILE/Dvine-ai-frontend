"""Build the offline English dictionary shard from WordNet 3.1.

Usage: python3 tools/make_dictionary.py <path-to-wordnet-dict-dir> [count]
Writes app/src/main/assets/knowledge/shards/dictionary_en.json.
WordNet 3.1 Copyright 2011 by Princeton University. All rights reserved.
See app/src/main/assets/knowledge/shards/WORDNET_LICENSE.txt.
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POS = {"noun": "n", "verb": "v", "adj": "a", "adv": "r"}
NAMES = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}


def read_data(path):
    syn = {}
    with open(path, encoding="latin-1") as fh:
        for line in fh:
            if line.startswith("  "):
                continue
            head, _, gloss = line.partition(" | ")
            parts = head.split()
            off, ssType, wcnt = parts[0], parts[2], int(parts[3], 16)
            words = [parts[4 + i * 2].replace("_", " ") for i in range(wcnt)]
            words = [re.sub(r"\(.*\)$", "", w) for w in words]
            definition = gloss.split(";")[0].strip().strip('"').strip()
            examples = re.findall(r'"([^"]+)"', gloss)
            syn[off] = (ssType, words, definition, examples[:1])
    return syn


def main():
    d = sys.argv[1]
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 15000
    data = {p: read_data(os.path.join(d, "data." + name)) for name, p in POS.items()}
    ranked = {}
    for name, p in POS.items():
        with open(os.path.join(d, "index." + name), encoding="latin-1") as fh:
            for line in fh:
                if line.startswith("  "):
                    continue
                parts = line.split()
                lemma = parts[0].replace("_", " ")
                synset_cnt = int(parts[2])
                p_cnt = int(parts[3])
                tagged = int(parts[5 + p_cnt])
                offsets = parts[6 + p_cnt:6 + p_cnt + synset_cnt]
                if lemma.count(" ") > 1 or not re.match(r"^[a-z][a-z' -]*$", lemma):
                    continue
                entry = ranked.setdefault(lemma, {"score": 0, "senses": []})
                entry["score"] += tagged
                entry["senses"].append((tagged, p, offsets))
    lemmas = sorted((l for l in ranked if ranked[l]["score"] > 0), key=lambda l: -ranked[l]["score"])[:count]
    words = []
    for lemma in sorted(lemmas):
        senses = sorted(ranked[lemma]["senses"], key=lambda s: -s[0])
        tagged, p, offsets = senses[0]
        ssType, syns, definition, ex = data[p][offsets[0]]
        synonyms = []
        for s in senses:
            for off in s[2][:2]:
                for w in data[s[1]][off][1]:
                    if w.lower() != lemma and w.lower() not in synonyms and len(synonyms) < 6:
                        synonyms.append(w.lower())
        meaning = definition.rstrip(".") + "."
        item = {"word": lemma, "meaning": meaning, "pos": NAMES.get(ssType, "")}
        if synonyms:
            item["synonyms"] = synonyms
        if ex:
            item["example"] = ex[0]
        words.append(item)
    shard = {
        "schemaVersion": 1,
        "version": 1,
        "language": "en",
        "source": "WordNet 3.1, Copyright 2011 by Princeton University",
        "words": words,
    }
    out = os.path.join(ROOT, "app", "src", "main", "assets", "knowledge", "shards", "dictionary_en.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(shard, fh, ensure_ascii=False, separators=(",", ":"))
    lic = os.path.join(os.path.dirname(out), "WORDNET_LICENSE.txt")
    with open(os.path.join(d, "data.noun"), encoding="latin-1") as fh, open(lic, "w") as o:
        for line in fh:
            if not line.startswith("  "):
                break
            o.write(re.sub(r"^\s+\d+\s?", "", line).rstrip() + "\n")
    print(out, len(words), "words", os.path.getsize(out), "bytes")


if __name__ == "__main__":
    main()
