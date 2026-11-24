package fr.namularbre.taczcombat.game;

import fr.namularbre.taczcombat.arena.ArenaMap;
import fr.namularbre.taczcombat.config.CombatRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** Une seule partie a la fois sur le serveur, plus la file des joueurs inscrits. */
public final class GameManager {
    private static final GameManager INSTANCE = new GameManager();

    private final Set<UUID> queue = new LinkedHashSet<>();
    private MeleeGame game;

    public static GameManager get() {
        return INSTANCE;
    }

    public MeleeGame current() {
        return game;
    }

    public boolean isRunning() {
        return game != null && !game.isFinished();
    }

    public boolean join(ServerPlayer player) {
        return queue.add(player.getUUID());
    }

    public boolean leave(ServerPlayer player) {
        boolean wasQueued = queue.remove(player.getUUID());
        if (isRunning() && game.isParticipant(player)) {
            game.removePlayer(player);
            return true;
        }
        return wasQueued;
    }

    public List<ServerPlayer> queuedPlayers(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : queue) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) players.add(player);
        }
        return players;
    }

    /** Lance une partie avec les joueurs inscrits. Renvoie un message d'erreur, ou null si tout va bien. */
    public String start(MinecraftServer server, ArenaMap map) {
        if (isRunning()) return "Une partie est deja en cours sur " + game.map().name + ".";
        if (!map.hasBounds()) return "La map " + map.name + " n'a pas ses deux bornes.";
        if (map.spawns.isEmpty()) return "La map " + map.name + " n'a aucun point d'apparition.";
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(map.dimension)));
        if (level == null) return "Dimension introuvable : " + map.dimension;
        List<ServerPlayer> players = queuedPlayers(server);
        if (players.isEmpty()) return "Aucun joueur inscrit. Les joueurs rejoignent avec /melee join.";

        game = new MeleeGame(server, level, map, CombatRules.get().copy());
        game.start(players);
        return null;
    }

    public boolean stop(String reason) {
        if (!isRunning()) return false;
        game.finish(reason);
        game = null;
        return true;
    }

    public void tick() {
        if (game == null) return;
        game.tick();
        if (game.isFinished()) game = null;
    }

    public void onLogout(ServerPlayer player) {
        queue.remove(player.getUUID());
        if (isRunning() && game.isParticipant(player)) {
            game.removePlayer(player);
        }
    }

    public void reset() {
        queue.clear();
        game = null;
    }
}
