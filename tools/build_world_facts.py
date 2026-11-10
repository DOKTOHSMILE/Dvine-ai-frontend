"""Builds app/src/main/assets/knowledge/world_facts.tsv from Wikidata (public domain, CC0).

Run from the repository root:  python3 tools/build_world_facts.py
It writes world_facts.tsv in the current folder; move it to app/src/main/assets/knowledge/.
One line a fact: thing <TAB> what about it <TAB> value. dvine1/BuiltIn.java reads it.
Every sovereign state (capital, continent, currency, official language, population, area) and every
chemical element (symbol, atomic number). Wikidata limits how fast it may be asked; if it answers
429, wait and run it again.
"""
import json, time, urllib.request, urllib.parse, sys
API="https://www.wikidata.org/w/api.php"
def call(params):
    params=dict(params, format="json")
    req=urllib.request.Request(API+"?"+urllib.parse.urlencode(params), headers={"User-Agent":"dataset-builder/1.0"})
    for i in range(4):
        try:
            return json.loads(urllib.request.urlopen(req, timeout=60).read())
        except Exception as e:
            print("retry", e, file=sys.stderr); time.sleep(5)
    raise SystemExit("api failed")
def search(stmt):
    ids=[]; off=0
    while True:
        r=call({"action":"query","list":"search","srsearch":"haswbstatement:"+stmt,"srlimit":"500","sroffset":str(off),"srnamespace":"0"})
        hits=r["query"]["search"]; ids+= [h["title"] for h in hits]
        if "continue" not in r: break
        off=r["continue"]["sroffset"]
    return ids
def entities(ids, props):
    out={}
    for i in range(0,len(ids),50):
        r=call({"action":"wbgetentities","ids":"|".join(ids[i:i+50]),"props":props,"languages":"en"})
        out.update(r.get("entities",{})); time.sleep(0.3)
    return out
def best(claims, p):
    cs=claims.get(p,[])
    pref=[c for c in cs if c.get("rank")=="preferred"] or [c for c in cs if c.get("rank")=="normal"]
    # drop claims with an end time (P582)
    live=[c for c in pref if "P582" not in c.get("qualifiers",{})] or pref
    return [c["mainsnak"]["datavalue"]["value"] for c in live if c["mainsnak"].get("datavalue")]
countries=search("P31=Q3624078"); print(len(countries),"states",file=sys.stderr)
ents=entities(countries,"labels|claims")
rows=[]; need=set()
for q,e in ents.items():
    name=e.get("labels",{}).get("en",{}).get("value"); c=e.get("claims",{})
    if not name or "P576" in c: continue
    rec={"name":name}
    for key,p in (("capital","P36"),("continent","P30"),("currency","P38"),("official language","P37")):
        v=[x["id"] for x in best(c,p) if isinstance(x,dict) and "id" in x]; rec[key]=v; need.update(v)
    for key,p in (("population","P1082"),("area","P2046")):
        v=best(c,p)
        if v and isinstance(v[0],dict) and "amount" in v[0]: rec[key]=v[0]["amount"].lstrip("+")
    rows.append(rec)
labels={q:e.get("labels",{}).get("en",{}).get("value") for q,e in entities(sorted(need),"labels").items()}
elements=search("P31=Q11344"); print(len(elements),"elements",file=sys.stderr)
el=entities(elements,"labels|claims")
lines=[]
for r in sorted(rows,key=lambda r:r["name"]):
    for key in ("capital","continent","currency","official language"):
        for q in r[key]:
            if labels.get(q): lines.append((r["name"],key,labels[q]))
    for key in ("population","area"):
        if key in r: lines.append((r["name"],key,r[key]))
for q,e in sorted(el.items(), key=lambda kv: kv[1].get("labels",{}).get("en",{}).get("value","")):
    name=e.get("labels",{}).get("en",{}).get("value"); c=e.get("claims",{})
    if not name: continue
    sym=best(c,"P246"); num=best(c,"P1086")
    if sym and isinstance(sym[0],str): lines.append((name,"symbol",sym[0]))
    if num and isinstance(num[0],dict): lines.append((name,"atomic number",num[0]["amount"].lstrip("+")))
with open("world_facts.tsv","w",encoding="utf-8") as f:
    for a,b,c in lines:
        if "\t" in a+c or "\n" in a+c: continue
        f.write(f"{a}\t{b}\t{c}\n")
print(len(lines),"facts",file=sys.stderr)
