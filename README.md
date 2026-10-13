# MineNorth Récolte (`minenorth_harvest`) — Forge 1.20.1

Mod de récolte RP pour MineNorthRP. Tout le monde peut cueillir et abattre (pas de métier). À installer **sur le serveur ET sur les clients** (HUD + réseau).

## Compiler

```
./gradlew build        (Windows : gradlew.bat build)
```
Le jar se trouve dans `build/libs/minenorth_harvest-1.20.1-1.0.0.jar`. Java 17 requis.

## Arbres fruitiers

| Arbre | Tronc | Fruit |
|---|---|---|
| Pommier | chêne | Pomme (vanilla) |
| Oranger | jungle | Orange |
| Citronnier | bouleau | Citron |
| Cerisier fruitier | cerisier | Cerises |
| Poirier | sapin | Poire |

- Le feuillage passe par 4 stades : **feuilles → fleurs → fruits verts → fruits mûrs**.
- **Clic droit** sur un feuillage mûr = cueillette (son + particules), puis le cycle recommence.
- Un fruit mûr oublié finit par **tomber tout seul** au sol.
- La poudre d'os fait mûrir les fruits (désactivable).
- Les feuillages donnent des pousses fruitières comme des feuilles normales.
- Recette pour démarrer : *pousse vanilla du bon bois + 2 fruits* → pousse fruitière
  (les pousses peuvent aussi être vendues dans tes shops, ou données via `/give`).

## Boulangerie : du blé à la baguette

| Étape | Comment | Résultat |
|---|---|---|
| Moudre | **Moulin** : blé en main, **maintenir clic droit** (8 tours ≈ 1,5 s) | 3 blé → 1 **Baguette** (recette par défaut de la config ; farine/pâte/tartes restent possibles) |
| Pétrir | Établi : 3 farine + 1 seau d'eau (le seau est rendu) | 3 **Pâte à pain** |
| Cuire | Four, fumoir ou feu de camp | **Baguette** |
| Pâtisser | Établi : farine + sucre + œuf + 2 fruits | **Tarte** aux pommes / à l'orange / au citron / aux cerises / aux poires |

- Moulin : recette *meule + 3 pierres lisses* (supprime `data/minenorth_harvest/recipes/mill.json` si tu préfères le vendre ou le donner).
- Section `[mill]` de la config : tours par produit, délai anti auto-clic, et **recettes du moulin**
  (`"minecraft:wheat*3=minenorth_harvest:flour*1"` ; ajoute des lignes pour moudre autre chose).
- Le pain vanilla (3 blé) existe toujours : pour la revente, fais racheter farine, baguettes et tartes dans tes shops.

## Bûcheron : la furie de la hache

1. Dans une **zone de bûcheronnage**, hache en main, **accroupi + clic droit** sur le tronc d'un arbre naturel.
2. Une jauge apparaît : **martelez le clic gauche** !
   - chaque coup rapporte 1 point ;
   - frappez vite (moins de 8 ticks entre deux coups) pour monter le **combo** : 1, puis 2, puis 3 points par coup ;
   - à partir de x6 c'est la **FURIE** : flammes, étincelles, son de plus en plus aigu, chance de bûche bonus ;
   - si vous ralentissez, la jauge **se vide**.
3. Jauge pleine → **TIMBER !** l'arbre tombe bloc par bloc, les feuilles orphelines tombent vite.
4. L'arbre abattu rend **toujours une pousse** de son essence, à ramasser au pied de la souche.
5. Il reste une **souche** : clic droit dessus avec une pousse pour **replanter**.

Un petit arbre = environ 10 points (3 à 5 secondes de furie), les gros arbres jusqu'à 24.
Réglages dans `[lumberjack]` : `rageBasePoints`, `rageLogsPerPoint`, `rageMaxPoints`, `rageComboWindowTicks`, `rageDecayDelayTicks`, `rageDecayIntervalTicks`.
Accroupi + clic droit (après avoir relâché le clic) = abandonner. S'éloigner, changer d'objet ou attendre 20 s annule aussi.
Casser les bûches à la main reste possible mais plus lent (`manualLogBreakSpeed`). Les constructions (bûches sans feuilles naturelles) ne sont pas concernées.

### Pétrole
- **Gisement de pétrole** : minerai noir, à la pioche (pierre minimum). Donne 1 à 3 **Pétrole brut** (Fortune ok, Silk Touch = le bloc).
  Il ne génère **pas** naturellement : les gisements n'existent que dans les **zones pétrole** (voir plus bas).
- Le brut est un combustible de four (2400 ticks, plus que le charbon).
- Très inflammable : feu, lave ou briquet à côté d'un gisement = **explosion**, qui peut s'enchaîner sur les gisements voisins.

### Raffinerie
- **Raffinerie** : comme le moulin, objet en main + **maintenir clic droit** (12 tours). 3 **Pétrole brut** → 2 **Essence** (combustible de four, 6400 ticks).
- Section `[refinery]` de la config : tours par produit et recettes (même format que le moulin).
- Recette : 5 lingots de fer, 1 lingot de cuivre, 2 seaux, 1 haut fourneau (supprime `recipes/refinery.json` pour la vendre ou la donner).

### Scierie (3D animée, multibloc 2 × 3 × 2)
- **Grande machine** : elle occupe **2 de large, 3 de long et 2 de haut** (il faut la place pour la poser, sinon rien ne se passe). Le bloc que vous visez en la posant est le centre ; elle s'orientera face à vous. La casser (n'importe quelle partie) la retire en entier.
- Clic droit avec des **bûches** : elles se posent sur le **chariot** (jusqu'à 16). La scie démarre toute seule : le tronc glisse sous la **lame qui tourne**, sciure et bruit de scie, et ressort en **planches** à l'avant.
- **Clic droit à main nue** : récupérer les planches (accroupi : reprendre les bûches non sciées).
- 1 bûche → **6 planches** (4 à l'établi), toutes essences vanilla + bambou. 15 % de **coup de chance** (planches doublées).
- Section `[sawmill]` : `sawTicks` (80 = 4 s par bûche), `luckChance`, `recipes`. Recette : fer, planches, bâtons.

### Mine et four à minerai (3D animé)
- Zone **mine** (`/recolte zone creer <nom> mine`) : la roche se remplit de minerais (liste pondérée `[mine] ores`, variantes deepslate auto, `densite` réglable, `generer` pour regénérer). Un minerai miné **repousse** après 600 s.
- **Four à minerai** : clic droit avec du **minerai brut** (fer, cuivre, or) puis du **combustible** (charbon, bois…). Il s'allume (flammes, fumée de cheminée, lueur). **Clic droit à main nue = coup de soufflet** : il chauffe plus vite.
- Posez un **Seau de coulée** (fer + argile) **devant le bec** : le métal fondu **coule** en jet lumineux dans le seau. Sans seau, le métal attend dans le four.
- Le seau est brûlant puis refroidit (`coolTicks`, 5 s) : **clic droit avec un seau d'eau = trempe** (vapeur, instantané). Une fois solide, clic droit pour prendre les **lingots**. 2 minerais bruts → 3 lingots, **coup de chance** 10 % = lingots doublés.
- Section `[foundry]` : `smeltTicks`, `coolTicks`, `bellowsBoostTicks`, `bellowsMaxTicks`, `luckChance`, `recipes`.

### Écologie
Chaque arbre abattu = +1 dette, chaque replantation sur une souche = −1.
À partir de 3 (configurable), la forêt est « épuisée » : plus de bonus et 2 coups de plus.

## Zones (admin, op 2)

Deux types de zones, propres à chaque dimension :

- **bucheron** : le mini-jeu n'est possible que dedans (`requireZone = true` par défaut).
  Les protections (claims) y sont ignorées pour l'abattage (`zonesBypassProtection`).
  Avec la **repousse** (activée par défaut), une souche oubliée est replantée automatiquement après 10 min.
- **verger** : dès qu'un joueur est dans la zone, les fruits y poussent **5× plus vite**.
- **petrole** : la roche de la zone se remplit de gisements (`densite`, 6 % par défaut). Un gisement miné **réapparaît** après `oilRegenDelaySeconds` (900 s, désactivable avec `/recolte zone repousse <nom> false`).
  `/recolte zone generer <nom>` (re)génère, `/recolte zone densite <nom> <0-1>` règle la part de roche.  Les chunks de la zone sont chargés automatiquement pour la génération.
- **chasse** : des animaux apparaissent autour des joueurs présents (port de `chasse.sk`). Hauteur ignorée.

### Créer une zone avec la baguette (comme WorldEdit)

1. `/recolte baguette` → tu reçois la **Baguette de zone**.
2. **Clic gauche** sur un bloc = point 1, **clic droit** sur un autre bloc = point 2
   (le contour s'affiche en particules).
3. `/recolte zone creer <nom> bucheron`, `... verger`, `... chasse` ou `... petrole`.

Pense à prendre la hauteur : clique un bloc au sol d'un coin, et un bloc en hauteur au coin opposé
(au-dessus de la cime des arbres).

### Autres commandes

```
/recolte zone liste
/recolte zone info <nom>
/recolte zone afficher <nom>                 (contour en particules pendant 15 s)
/recolte zone redefinir <nom>                (nouvelles limites = sélection actuelle de la baguette)
/recolte zone supprimer <nom>
/recolte zone repousse <nom> <true|false>    (zones bûcheron)
/recolte zone vitesse <nom> <multiplicateur> (zones verger, 0 = valeur de la config)
/recolte zone creer <nom> <type> <x1 y1 z1> <x2 y2 z2>   (si tu préfères les coordonnées)
/recolte ecoreset <joueurs>                  (remet la dette écologique à 0)
/recolte chasse reload                       (recharge les récompenses de chasse)
```

## Chasse (port de chasse.sk)

- Entrée dans une zone de chasse : message `[Chasse]` + titre « Zone de Chasse ». Sortie : message.
- Toutes les 10 s, pour chaque joueur en zone : s'il y a moins de 6 animaux à 20 blocs, un animal apparaît
  entre 6 et 20 blocs de lui, **sur de l'herbe** et **dans la zone** (vache, cochon, mouton, poule, lapin, ours polaire).
- Réglages dans `config/minenorth_harvest-common.toml`, section `[hunting]` : intervalle, rayons, max d'animaux,
  liste des animaux avec poids (`"minecraft:cow=3"`), blocs de sol autorisés, titre.
- **Récompenses** dans `config/minenorth_harvest-chasse.json` (créé au 1er démarrage avec celles du script) :
  objets nommés (codes `&`), quantité, chance optionnelle, message. Ajoute n'importe quel mob par son id.
  - **L'animal ne lâche rien au sol** : le butin va directement dans l'inventaire du chasseur, sans orbes d'XP.
  - `clearVanillaDrops` (true) : aucun drop au sol. `noExperience` (true) : pas d'XP.
  - `dropIfInventoryFull` (false) : inventaire plein = butin perdu avec un message ; `true` = posé aux pieds du joueur.
  - `onlyInHuntingZones` : `false` = partout comme le script, `true` = seulement dans les zones de chasse.
  - Un animal configuré tué sans joueur (lave, cactus…) ne lâche rien non plus.


## Configuration

Fichier : `config/minenorth_harvest-common.toml`, généré au premier démarrage du serveur et rechargé à chaud
(les valeurs utiles au client lui sont renvoyées automatiquement).
Tout est réglable : vitesse de pousse, nombre de fruits, largeur des zones, vitesse du curseur, zones, souches, écologie…

## Arclight / protections
Avant l'abattage et pour chaque bûche, le mod envoie un `BlockEvent.BreakEvent` Forge : les mods de claim
qui l'écoutent bloquent l'abattage. Avec des plugins Bukkit (WorldGuard, GriefPrevention…) sous Arclight,
**tester dans une zone protégée** avant la mise en prod.

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).
