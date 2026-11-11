"""Builds Ibiko's further built-in knowledge (dvine1/BuiltIn reads every file):

  ibiko1/src/main/assets/knowledge/country_more.tsv   calling code, internet domain, region, ISO codes, UN membership
      from mledoze/countries (ODbL 1.0, the same source as world_facts.tsv)
  ibiko1/src/main/assets/knowledge/elements_more.tsv  for the 118 elements: atomic mass, category, state at room
      temperature, discovery, melting and boiling points, density, electron configuration, group and period
      from Bowserinator/Periodic-Table-JSON (CC BY-SA 3.0; this file is shared under the same licence)
  ibiko1/src/main/assets/knowledge/local_facts.tsv    Nigeria's 36 states and the FCT with their capitals; the eight
      planets (order, diameter, year length, kind; NASA figures, public domain). Facts written by the project.

Run from the repository root:  python3 tools/build_more_facts.py   (downloads both sources; needs only Python)
One line a fact: thing <TAB> what about it <TAB> value.
"""
import json
import os
import urllib.request

OUT = "ibiko1/src/main/assets/knowledge"
COUNTRIES = "https://raw.githubusercontent.com/mledoze/countries/master/countries.json"
ELEMENTS = "https://raw.githubusercontent.com/Bowserinator/Periodic-Table-JSON/master/PeriodicTableJSON.json"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "dataset-builder/1.0"})
    return json.loads(urllib.request.urlopen(req, timeout=120).read())


def write(name, rows):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        for r in rows:
            f.write("\t".join(str(x).replace("\t", " ").strip() for x in r) + "\n")
    print(name, len(rows))


# the countries world_facts.tsv already knows, by the name it uses
known = set()
with open(os.path.join(OUT, "world_facts.tsv"), encoding="utf-8") as f:
    for line in f:
        known.add(line.split("\t")[0])

rows = []
for c in get(COUNTRIES):
    name = c["name"]["common"]
    if name not in known:
        continue
    idd = c.get("idd") or {}
    root, suf = idd.get("root", ""), idd.get("suffixes") or []
    if root:
        rows.append((name, "calling code", root + (suf[0] if len(suf) == 1 else "")))
    for t in (c.get("tld") or [])[:2]:
        rows.append((name, "internet domain", t))
    if c.get("subregion") or c.get("region"):
        rows.append((name, "region", c.get("subregion") or c.get("region")))
    rows.append((name, "iso code", c["cca2"] + " and " + c["cca3"]))
    rows.append((name, "un member", "yes" if c.get("unMember") else "no"))
write("country_more.tsv", rows)


def ordinal(n):
    return {1: "first", 2: "second", 3: "third", 4: "fourth", 5: "fifth", 6: "sixth", 7: "seventh", 8: "eighth"}[n]


rows = []
for e in get(ELEMENTS)["elements"]:
    if e["number"] > 118:
        continue
    n = e["name"].lower()
    if e.get("atomic_mass"):
        rows.append((n, "atomic mass", f"{e['atomic_mass']:.4g}"))
    cat = (e.get("category") or "").strip()
    if cat and not cat.startswith("unknown"):
        rows.append((n, "element category", cat))
    if e.get("phase"):
        rows.append((n, "phase", e["phase"].lower()))
    if e.get("discovered_by"):
        rows.append((n, "discovered by", e["discovered_by"]))
    if e.get("melt"):
        rows.append((n, "melting point", f"{e['melt'] - 273.15:.0f} °C ({e['melt']:g} K)"))
    if e.get("boil"):
        rows.append((n, "boiling point", f"{e['boil'] - 273.15:.0f} °C ({e['boil']:g} K)"))
    if e.get("density"):
        unit = "g/L" if (e.get("phase") or "").lower() == "gas" else "g/cm³"
        rows.append((n, "density", f"{e['density']:g} {unit}"))
    if e.get("electron_configuration_semantic"):
        rows.append((n, "electron configuration", e["electron_configuration_semantic"]))
    if e.get("period"):
        rows.append((n, "group and period", (f"group {e['group']}, " if e.get("group") else "") + f"period {e['period']}"))
write("elements_more.tsv", rows)

STATES = [("Abia", "Umuahia"), ("Adamawa", "Yola"), ("Akwa Ibom", "Uyo"), ("Anambra", "Awka"), ("Bauchi", "Bauchi"),
          ("Bayelsa", "Yenagoa"), ("Benue", "Makurdi"), ("Borno", "Maiduguri"), ("Cross River", "Calabar"), ("Delta", "Asaba"),
          ("Ebonyi", "Abakaliki"), ("Edo", "Benin City"), ("Ekiti", "Ado-Ekiti"), ("Enugu", "Enugu"), ("Gombe", "Gombe"),
          ("Imo", "Owerri"), ("Jigawa", "Dutse"), ("Kaduna", "Kaduna"), ("Kano", "Kano"), ("Katsina", "Katsina"),
          ("Kebbi", "Birnin Kebbi"), ("Kogi", "Lokoja"), ("Kwara", "Ilorin"), ("Lagos", "Ikeja"), ("Nasarawa", "Lafia"),
          ("Niger", "Minna"), ("Ogun", "Abeokuta"), ("Ondo", "Akure"), ("Osun", "Osogbo"), ("Oyo", "Ibadan"),
          ("Plateau", "Jos"), ("Rivers", "Port Harcourt"), ("Sokoto", "Sokoto"), ("Taraba", "Jalingo"), ("Yobe", "Damaturu"),
          ("Zamfara", "Gusau")]
rows = [(s + " State", "capital", c) for s, c in STATES]
rows.append(("Federal Capital Territory", "capital", "Abuja"))
rows.append(("FCT", "capital", "Abuja"))
for s, _ in STATES:
    rows.append((s + " State", "continent", "Africa"))
# the planets: (name, diameter km, year, kind); NASA planetary fact sheets
PLANETS = [("Mercury", "4,879", "88 Earth days", "a rocky planet"), ("Venus", "12,104", "225 Earth days", "a rocky planet"),
           ("Earth", "12,742", "365.25 days", "a rocky planet"), ("Mars", "6,779", "687 Earth days", "a rocky planet"),
           ("Jupiter", "139,820", "11.9 Earth years", "a gas giant"), ("Saturn", "116,460", "29.5 Earth years", "a gas giant"),
           ("Uranus", "50,724", "84 Earth years", "an ice giant"), ("Neptune", "49,244", "165 Earth years", "an ice giant")]
for i, (p, d, y, k) in enumerate(PLANETS, 1):
    rows += [(p, "order from the sun", ordinal(i)), (p, "diameter", d), (p, "orbital period", y), (p, "planet type", k)]
write("local_facts.tsv", rows)
