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

## Bûcheron : mini-jeu d'abattage

1. Dans une **zone de bûcheronnage**, hache en main, **accroupi + clic droit** sur le tronc d'un arbre naturel.
2. Une barre apparaît : un curseur fait des allers-retours. **Clic gauche** quand il est dans la zone :
   - zone **dorée** = PARFAIT (+2, combo, chance de bûche bonus)
   - zone **verte** = Bien (+1)
   - à côté = Raté (usure de la hache en plus)
3. Le curseur ne revient jamais à gauche : à chaque coup réussi la zone se déplace et le curseur accélère.
   Un arbre classique se coupe en 2 à 3 coups (environ 2 secondes).
4. Barre pleine → **TIMBER !** l'arbre tombe bloc par bloc, les feuilles orphelines tombent vite.
5. Il reste une **souche** : clic droit dessus avec une pousse pour **replanter**.

Accroupi + clic droit pendant le mini-jeu = abandonner. S'éloigner, changer d'objet ou attendre 20 s annule aussi.
Casser les bûches d'un arbre naturel à la main reste possible mais plus lent (`manualLogBreakSpeed`).
Les constructions (bûches sans feuilles naturelles) ne sont pas concernées.

### Écologie
Chaque arbre abattu = +1 dette, chaque replantation sur une souche = −1.
À partir de 3 (configurable), la forêt est « épuisée » : plus de bonus et 2 coups de plus.

## Zones (admin, op 2)

Deux types de zones, propres à chaque dimension :

- **bucheron** : le mini-jeu n'est possible que dedans (`requireZone = true` par défaut).
  Les protections (claims) y sont ignorées pour l'abattage (`zonesBypassProtection`).
  Avec la **repousse** (activée par défaut), une souche oubliée est replantée automatiquement après 10 min.
- **verger** : dès qu'un joueur est dans la zone, les fruits y poussent **5× plus vite**.

### Créer une zone avec la baguette (comme WorldEdit)

1. `/recolte baguette` → tu reçois la **Baguette de zone**.
2. **Clic gauche** sur un bloc = point 1, **clic droit** sur un autre bloc = point 2
   (le contour s'affiche en particules).
3. `/recolte zone creer <nom> bucheron` ou `/recolte zone creer <nom> verger`.

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
```

## Configuration

Fichier : `config/minenorth_harvest-common.toml`, généré au premier démarrage du serveur et rechargé à chaud
(les valeurs utiles au client lui sont renvoyées automatiquement).
Tout est réglable : vitesse de pousse, nombre de fruits, largeur des zones, vitesse du curseur, zones, souches, écologie…

## Arclight / protections
Avant l'abattage et pour chaque bûche, le mod envoie un `BlockEvent.BreakEvent` Forge : les mods de claim
qui l'écoutent bloquent l'abattage. Avec des plugins Bukkit (WorldGuard, GriefPrevention…) sous Arclight,
**tester dans une zone protégée** avant la mise en prod.
