"""Vérifie que toute clé de traduction utilisée dans le code existe dans fr_fr.json ET en_us.json, et que les deux fichiers sont identiques
(tout le serveur est en français : un client en anglais ne doit jamais voir de clé brute). Usage : python tools/check_lang.py"""
import glob, json, os, pathlib, re, sys

W = pathlib.Path(__file__).resolve().parent.parent
J = W / "src" / "main" / "java"
L = W / "src" / "main" / "resources" / "assets" / "minenorth_harvest" / "lang"
fr = json.loads((L / "fr_fr.json").read_text(encoding="utf-8"))
en = json.loads((L / "en_us.json").read_text(encoding="utf-8"))

KEY = re.compile(r'"((?:message|command|item|block|gui|zone|key|entity|itemGroup|tooltip|screen|title|subtitle|death)(?:\.[a-z0-9_]+)+)(\.?)"')
used = set()
prefixes = set()
for f in glob.glob(str(J / "**" / "*.java"), recursive=True):
    s = open(f, encoding="utf-8").read()
    for m in KEY.finditer(s):
        if m.group(2) or re.match(r"\s*\+", s[m.end():m.end() + 8]):
            prefixes.add(m.group(1) + m.group(2))
        else:
            used.add(m.group(1))
    used.update(re.findall(r'translatable\(\s*"([^"]+)"', s))

used = {k for k in used if not k.endswith(".")}   # préfixes dynamiques : traités ci-dessous
zones = ["bucheron", "verger", "chasse", "petrole", "mine"]      # ZoneType : clés construites avec l'identifiant de la zone
for p in prefixes:
    if p.endswith("."):
        used.update(p + z for z in zones)
reg = open(J / "com" / "minenorth_harvest" / "registry" / "ModBlocks.java", encoding="utf-8").read()
used.update("block.minenorth_harvest." + b for b in re.findall(r'BLOCKS\.register\(\s*"([a-z0-9_]+)"', reg))
itm = open(J / "com" / "minenorth_harvest" / "registry" / "ModItems.java", encoding="utf-8").read()
blocks = set(re.findall(r'BLOCKS\.register\(\s*"([a-z0-9_]+)"', reg))
used.update("item.minenorth_harvest." + i for i in re.findall(r'ITEMS\.register\(\s*"([a-z0-9_]+)"', itm) if i not in blocks)

bad = False
for name, d in (("fr_fr", fr), ("en_us", en)):
    miss = sorted(k for k in used if k not in d)
    if miss:
        bad = True
        print("MANQUANTES dans", name, ":", miss)
if fr != en:
    bad = True
    print("fr_fr et en_us diffèrent :", sorted(k for k in set(fr) | set(en) if fr.get(k) != en.get(k)))
print(len(used), "clés utilisées,", len(fr), "clés dans le lang :", "ECHEC" if bad else "OK")
sys.exit(1 if bad else 0)
