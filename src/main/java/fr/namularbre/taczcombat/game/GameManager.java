package fr.namularbre.taczcombat.game;

import fr.namularbre.taczcombat.arena.ArenaMap;
import fr.namularbre.taczcombat.arena.ArenaStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.server.ServerLifecycleHooks;
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
    /** Classe preferee de chaque joueur, reprise au debut de chaque partie. */
    private final Map<UUID, String> preferredClass = new HashMap<>();
    private MeleeGame game;
    private MapVote vote;
    private final Random random = new Random();
    /** Compte a rebours avant le vote automatique (en ticks), -1 si aucun n'est prevu. */
    private int autoVoteCountdown = -1;

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
        boolean added = queue.add(player.getUUID());
        if (added && vote != null) {
            vote.show(player);
            MapVoteMenu.open(player, vote);
        }
        return added;
    }

    public boolean leave(ServerPlayer player) {
        boolean wasQueued = queue.remove(player.getUUID());
        if (vote != null) vote.hide(player);
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

        cancelVote();
        autoVoteCountdown = -1;
        game = new MeleeGame(server, level, map, CombatRules.get().copy());
        game.start(players);
        return null;
    }

    /** Arret par un admin : termine la partie ou le vote en cours, sans relancer de vote automatique. */
    public boolean stop(String reason) {
        boolean stopped = autoVoteCountdown > 0;
        autoVoteCountdown = -1;
        stopped |= cancelVote();
        if (isRunning()) {
            game.finish(reason);
            stopped = true;
        }
        game = null;
        return stopped;
    }

    public void tick() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        if (game != null) {
            game.tick();
            if (game.isFinished()) {
                game = null;
                scheduleAutoVote(server);
            }
        }

        if (autoVoteCountdown > 0 && --autoVoteCountdown == 0) {
            autoVoteCountdown = -1;
            String error = startVote(server);
            if (error != null) broadcast(server, Component.literal(error).withStyle(ChatFormatting.RED));
        }

        if (vote != null && vote.tick()) {
            MapVote finished = vote;
            vote = null;
            finished.close();
            String winner = finished.winner(random);
            broadcast(server, Component.literal("La map " + winner + " l'emporte (" + finished.count(winner) + " vote(s)) !")
                    .withStyle(ChatFormatting.GOLD));
            ArenaMap map = ArenaStore.get().get(winner);
            String error = map == null ? "La map " + winner + " n'existe plus." : start(server, map);
            if (error != null) broadcast(server, Component.literal(error).withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- vote de map

    public MapVote currentVote() {
        return vote;
    }

    /** Lance un vote parmi les maps jouables. Renvoie un message d'erreur, ou null si tout va bien. */
    public String startVote(MinecraftServer server) {
        if (isRunning()) return "Une partie est deja en cours.";
        if (vote != null) return "Un vote est deja en cours.";
        List<String> playable = new ArrayList<>();
        for (ArenaMap map : ArenaStore.get().all()) {
            if (map.isPlayable()) playable.add(map.name);
        }
        if (playable.isEmpty()) return "Aucune map jouable (il faut 2 bornes et au moins 1 spawn).";
        List<ServerPlayer> players = queuedPlayers(server);
        if (players.isEmpty()) return "Aucun joueur inscrit. Les joueurs rejoignent avec /melee join.";

        autoVoteCountdown = -1;
        vote = new MapVote(playable, CombatRules.get().voteSeconds);
        for (ServerPlayer player : players) {
            vote.show(player);
            MapVoteMenu.open(player, vote);
        }
        broadcast(server, Component.literal("Vote de la prochaine map ! Clique sur une map ou tape /melee vote.")
                .withStyle(ChatFormatting.GREEN));
        return null;
    }

    /** Vote d'un joueur. Il s'inscrit automatiquement a la partie s'il ne l'etait pas. */
    public void vote(ServerPlayer player, String mapName) {
        if (vote == null) {
            player.sendSystemMessage(Component.literal("Aucun vote en cours.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!vote.vote(player.getUUID(), mapName)) {
            player.sendSystemMessage(Component.literal("Cette map n'est pas dans le vote.").withStyle(ChatFormatting.RED));
            return;
        }
        if (queue.add(player.getUUID())) vote.show(player);
        player.sendSystemMessage(Component.literal("Vote pour " + mapName + " enregistre.").withStyle(ChatFormatting.GREEN));
    }

    public void openVoteMenu(ServerPlayer player) {
        if (vote == null) {
            player.sendSystemMessage(Component.literal("Aucun vote en cours.").withStyle(ChatFormatting.RED));
            return;
        }
        MapVoteMenu.open(player, vote);
    }

    private boolean cancelVote() {
        if (vote == null) return false;
        vote.close();
        vote = null;
        return true;
    }

    private void scheduleAutoVote(MinecraftServer server) {
        CombatRules rules = CombatRules.get();
        if (rules.autoVote != 1 || queuedPlayers(server).isEmpty()) return;
        autoVoteCountdown = Math.max(1, rules.nextVoteDelaySeconds * 20);
        broadcast(server, Component.literal("Prochain vote de map dans " + rules.nextVoteDelaySeconds + " s.")
                .withStyle(ChatFormatting.YELLOW));
    }

    private void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : queuedPlayers(server)) player.sendSystemMessage(message);
    }

    /** Classe actuelle du joueur : celle de la partie en cours, sinon sa preference, sinon la premiere. */
    public String chosenClass(ServerPlayer player) {
        if (isRunning() && game.isParticipant(player)) {
            String inGame = game.chosenClassOf(player);
            if (inGame != null) return inGame;
        }
        String preferred = preferredClass.get(player.getUUID());
        if (fr.namularbre.taczcombat.loadout.ClassStore.get().get(preferred) != null) return preferred;
        var first = fr.namularbre.taczcombat.loadout.ClassStore.get().first();
        return first == null ? null : first.name;
    }

    public void chooseClass(ServerPlayer player, String className) {
        preferredClass.put(player.getUUID(), className);
        if (isRunning() && game.isParticipant(player)) {
            game.chooseClass(player, className);
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Classe " + className + " choisie pour la prochaine partie.")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }

    public void onLogout(ServerPlayer player) {
        queue.remove(player.getUUID());
        if (vote != null) vote.hide(player);
        if (isRunning() && game.isParticipant(player)) {
            game.removePlayer(player);
        }
    }

    public void reset() {
        queue.clear();
        preferredClass.clear();
        cancelVote();
        autoVoteCountdown = -1;
        game = null;
    }
}
