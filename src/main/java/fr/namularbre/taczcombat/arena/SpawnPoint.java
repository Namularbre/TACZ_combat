package fr.namularbre.taczcombat.arena;

/** Point d'apparition : position du bloc et orientation du joueur a l'apparition. */
public class SpawnPoint {
    public Pos pos;
    public float yaw;

    public SpawnPoint() {}

    public SpawnPoint(Pos pos, float yaw) {
        this.pos = pos;
        this.yaw = yaw;
    }
}
