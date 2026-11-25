# TACZ Combat

Mod Forge 1.20.1 de **mêlée générale** (chacun pour soi) pour [TACZ](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero).

Les maps sont des zones du monde délimitées par des bornes. Les admins y posent des points d'apparition et règlent la partie par commandes ou en JSON.

## Prérequis

- Java 17 (Gradle le sélectionne tout seul via `gradle/gradle-daemon-jvm.properties`, il doit juste être installé)
- Minecraft 1.20.1, Forge 47.x
- TACZ 1.1.x ou 1.2.x (chargé automatiquement en dev depuis Modrinth, version dans `gradle.properties`)

## Télécharger

Le jar est publié dans les [Releases](https://github.com/Namularbre/TACZ_combat/releases). Pose-le dans `mods/` avec TACZ.

Pour publier une version : `git tag v0.2.0 && git push origin v0.2.0`. La CI compile le mod en version `0.2.0` et crée la release avec le jar. Un tag avec un suffixe (`v0.2.0-beta.1`) donne une pré-release.

## Lancer en dev

```
./gradlew runClient   # client avec TACZ
./gradlew build       # jar dans build/libs/
```

## Créer une map

1. `/melee map create usine` : crée la map et la met en édition.
2. En créatif, pose deux **bornes de zone** à deux coins opposés du volume (l'une en bas, l'autre en haut). Une troisième remplace la plus ancienne.
3. Pose des **points d'apparition**. La flèche indique la direction dans laquelle le joueur regardera.
4. `/melee map info usine` pour vérifier.

Casser une borne ou un spawn (en créatif) le retire de la map.

## Créer une classe

1. Vide ton inventaire, puis équipe-le comme la classe doit l'être : armes TACZ avec leurs accessoires (monte-les à l'établi TACZ), munitions, grenades, armure, main secondaire. La position dans l'inventaire est conservée.
2. `/melee class save assaut` : enregistre ton inventaire comme classe `assaut`. Refaire la commande met la classe à jour.
3. `/melee class list` pour vérifier, `/melee class give assaut` pour récupérer ses objets, `/melee class delete assaut` pour la supprimer.

Les classes sont dans `config/tacz_combat/classes.json` (un objet par emplacement, au format SNBT).

Dès qu'au moins une classe existe, les parties se jouent avec les classes : l'inventaire de chaque joueur est mis de côté au début et rendu à la fin. Un menu de choix s'ouvre à chaque apparition. Choisir pendant la protection de spawn équipe la classe tout de suite, sinon elle s'applique à la réapparition suivante. `/melee class` rouvre le menu, `/melee class pick <classe>` choisit sans menu. On ne peut ni jeter ni ramasser d'objets en partie.

## Interdire des armes et gadgets

- `/melee ban hand` : interdit ce que tu tiens en main. Pour une arme TACZ, c'est le modèle (`GunId`, par ex. `tacz:ak47`) qui est interdit ; pour un accessoire son `AttachmentId`, pour une munition son `AmmoId`, sinon l'objet lui-même.
- `/melee ban add <id>` / `/melee ban remove <id>` / `/melee ban list`.
- Liste dans `config/tacz_combat/banned.json`.

Un objet est interdit si son identifiant ou un identifiant de son NBT est dans la liste : une arme qui porte un accessoire interdit est donc interdite aussi. En partie, les objets interdits sont retirés de l'inventaire (au spawn et chaque seconde) et ne peuvent pas être utilisés. `/melee class save` refuse un inventaire qui en contient.

## Jouer

| Commande | Qui | Effet |
|---|---|---|
| `/melee join` / `/melee leave` | tous | s'inscrire / quitter |
| `/melee vote` / `/melee vote <map>` | tous | voter pour la prochaine map (menu ou direct) |
| `/melee start` | admin | lance un vote de map parmi les maps jouables |
| `/melee start <map>` | admin | choisit la map et lance la partie tout de suite |
| `/melee stop` | admin | arrête la partie ou le vote, sans relancer de vote automatique |
| `/melee map list\|create\|edit\|delete\|info` | admin | gestion des maps |
| `/melee class` / `/melee class pick <classe>` | tous | choisir sa classe |
| `/melee class save\|delete\|give <classe>` / `list` | admin | gestion des classes |
| `/melee ban add\|remove <id>` / `hand` / `list` | admin | armes et gadgets interdits |
| `/melee rules` / `/melee rules set <règle> <valeur>` | admin | règles |
| `/melee reload` | admin | relit les fichiers JSON |

Pendant une partie : mode aventure, mort sans perte d'objets puis réapparition sur le spawn le plus éloigné des adversaires, protection annulée dès qu'on tire ou frappe, retour automatique dans la zone si on en sort. À la fin, chacun retrouve sa position et son mode de jeu.

## Enchaîner les maps

- **Vote** : `/melee start` sans map ouvre un menu de vote à tous les inscrits (30 s). La map qui a le plus de voix gagne, une égalité est tirée au sort, et si personne ne vote la map est choisie au hasard. Voter inscrit automatiquement à la partie.
- **Choix admin** : `/melee start <map>` lance directement la map, et annule un vote en cours.
- **Enchaînement** : avec `autoVote` à 1, un nouveau vote se lance 10 s après chaque partie tant qu'il reste des inscrits. `/melee stop` coupe l'enchaînement.

## Fichiers

- `config/tacz_combat/rules.json` : `scoreLimit` (30), `timeLimitSeconds` (600), `respawnDelaySeconds` (5), `spawnProtectionSeconds` (3), `voteSeconds` (30), `autoVote` (1), `nextVoteDelaySeconds` (10), `destroyGlass` (0). 0 = illimité pour le score et le temps.
- `<monde>/tacz_combat/maps.json` : maps, bornes et spawns.

## Vitres et balles

Par défaut, le mod empêche les balles TACZ de casser les vitres (`destroyGlass` = 0). Pour les laisser casser : `/melee rules set destroyGlass true`, et `false` pour revenir. Le changement s'applique tout de suite, même en pleine partie.

La règle pilote l'option `DestroyGlass` de TACZ. Cette option est dans la config **common** de TACZ, `config/tacz-common.toml` à la racine du serveur, section `[ammo]`, et pas dans `saves/<monde>/serverconfig/tacz-server.toml`. Tant que `destroyGlass` vaut 0, le mod la force à `false` et la remet comme avant à l'arrêt du serveur. Les explosions des munitions explosives relèvent d'une autre option de TACZ, `ExplosiveAmmoDestroysBlock`.

## Feuille de route

- [x] Jalon 1 : bornes, spawns, partie minimale, règles
- [x] Jalon 2 : classes (armes TACZ, accessoires, équipement) et menu de choix
- [x] Jalon 3 : armes et gadgets bannis
- [x] Jalon 4 : vote des joueurs / choix admin de la map suivante
