# TACZ Combat

Mod Forge 1.20.1 de **mêlée générale** (chacun pour soi) pour [TACZ](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero).

Les maps sont des zones du monde délimitées par des bornes. Les admins y posent des points d'apparition et règlent la partie par commandes ou en JSON.

## Prérequis

- Java 17 (Gradle le sélectionne tout seul via `gradle/gradle-daemon-jvm.properties`, il doit juste être installé)
- Minecraft 1.20.1, Forge 47.x
- TACZ 1.1.x ou 1.2.x (chargé automatiquement en dev depuis Modrinth, version dans `gradle.properties`)

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

## Jouer

| Commande | Qui | Effet |
|---|---|---|
| `/melee join` / `/melee leave` | tous | s'inscrire / quitter |
| `/melee start <map>` | admin | lance la partie avec les inscrits |
| `/melee stop` | admin | arrête la partie |
| `/melee map list\|create\|edit\|delete\|info` | admin | gestion des maps |
| `/melee class` / `/melee class pick <classe>` | tous | choisir sa classe |
| `/melee class save\|delete\|give <classe>` / `list` | admin | gestion des classes |
| `/melee rules` / `/melee rules set <règle> <valeur>` | admin | règles |
| `/melee reload` | admin | relit les fichiers JSON |

Pendant une partie : mode aventure, mort sans perte d'objets puis réapparition sur le spawn le plus éloigné des adversaires, protection annulée dès qu'on tire ou frappe, retour automatique dans la zone si on en sort. À la fin, chacun retrouve sa position et son mode de jeu.

## Fichiers

- `config/tacz_combat/rules.json` : `scoreLimit` (30), `timeLimitSeconds` (600), `respawnDelaySeconds` (5), `spawnProtectionSeconds` (3). 0 = illimité pour le score et le temps.
- `<monde>/tacz_combat/maps.json` : maps, bornes et spawns.

## Feuille de route

- [x] Jalon 1 : bornes, spawns, partie minimale, règles
- [x] Jalon 2 : classes (armes TACZ, accessoires, équipement) et menu de choix
- [ ] Jalon 3 : armes et gadgets bannis
- [ ] Jalon 4 : vote des joueurs / choix admin de la map suivante
