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

## Bûcheron : mini-jeu d'abattage

1. Dans une **zone de bûcheronnage**, hache en main, **accroupi + clic droit** sur le tronc d'un arbre naturel.
2. Une barre apparaît : un curseur fait des allers-retours. **Clic gauche** quand il est dans la zone :
   - zone **dorée** = PARFAIT (+2, combo, chance de bûche bonus)
   - zone **verte** = Bien (+1)
   - à côté = Raté (usure de la hache en plus)
3. Le curseur ne revient jamais à gauche : à chaque coup réussi la zone se déplace et le curseur accélère.
   Un arbre classique se coupe en 2 à 3 coups (environ 2 secondes).
4. Barre pleine → **TIMBER !** l'arbre tombe bloc par bloc, les feuilles orphelines tombent vite.
5. L'arbre abattu rend **toujours une pousse** de son essence (pommier, oranger… ou vanilla), à ramasser au pied de la souche.
6. Il reste une **souche** : clic droit dessus avec une pousse pour **replanter**.

Accroupi + clic droit pendant le mini-jeu = abandonner. S'éloigner, changer d'objet ou attendre 20 s annule aussi.
Casser les bûches d'un arbre naturel à la main reste possible mais plus lent (`manualLogBreakSpeed`).
Les constructions (bûches sans feuilles naturelles) ne sont pas concernées.

### Pétrole
- **Gisement de pétrole** : minerai noir, à la pioche (pierre minimum). Donne 1 à 3 **Pétrole brut** (Fortune ok, Silk Touch = le bloc).
  Il ne génère **pas** naturellement : les gisements n'existent que dans les **zones pétrole** (voir plus bas).
- Le brut est un combustible de four (2400 ticks, plus que le charbon).
- Très inflammable : feu, lave ou briquet à côté d'un gisement = **explosion**, qui peut s'enchaîner sur les gisements voisins.

### Raffinerie
- **Raffinerie** : comme le moulin, objet en main + **maintenir clic droit** (12 tours). 3 **Pétrole brut** → 2 **Essence** (combustible de four, 6400 ticks).
- Section `[refinery]` de la config : tours par produit et recettes (même format que le moulin).
- Recette : 5 lingots de fer, 1 lingot de cuivre, 2 seaux, 1 haut fourneau (supprime `recipes/refinery.json` pour la vendre ou la donner).

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
