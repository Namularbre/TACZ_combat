package fr.namularbre.taczcombat.game;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Etat d'un joueur pendant une partie, et ce qu'il faut lui rendre a la fin. */
class Participant {
    enum State { ALIVE, DEAD }

    final UUID id;
    final String name;

    // Etat d'avant la partie, restaure a la fin
    final GameType previousGameMode;
    final ResourceKey<Level> previousDimension;
    final Vec3 previousPosition;
    final float previousYaw;
    final float previousPitch;

    State state = State.DEAD;
    int kills;
    int deaths;
    /** Tick de la partie auquel le joueur reapparait (si DEAD). */
    int respawnAtTick;
    /** Tick de la partie jusqu'auquel le joueur est invulnerable. */
    int protectedUntilTick;
    /** Derniere position connue a l'interieur de la zone. */
    Vec3 lastSafePosition;
    BlockPos lastSpawn;

    Participant(UUID id, String name, GameType previousGameMode, ResourceKey<Level> previousDimension,
                Vec3 previousPosition, float previousYaw, float previousPitch) {
        this.id = id;
        this.name = name;
        this.previousGameMode = previousGameMode;
        this.previousDimension = previousDimension;
        this.previousPosition = previousPosition;
        this.previousYaw = previousYaw;
        this.previousPitch = previousPitch;
    }
}
