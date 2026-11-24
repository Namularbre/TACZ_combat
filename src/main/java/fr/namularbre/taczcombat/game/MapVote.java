package fr.namularbre.taczcombat.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.*;

/** Vote des joueurs inscrits pour la prochaine map. */
public class MapVote {
    private final List<String> options;
    private final Map<UUID, String> votes = new HashMap<>();
    private final int durationTicks;
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Vote de la map"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
    private int ticksLeft;

    MapVote(List<String> options, int seconds) {
        this.options = List.copyOf(options);
        this.durationTicks = Math.max(20, seconds * 20);
        this.ticksLeft = durationTicks;
    }

    public List<String> options() {
        return options;
    }

    public String voteOf(UUID player) {
        return votes.get(player);
    }

    public int count(String map) {
        return (int) votes.values().stream().filter(map::equals).count();
    }

    boolean vote(UUID player, String map) {
        if (!options.contains(map)) return false;
        votes.put(player, map);
        return true;
    }

    void show(ServerPlayer player) {
        bar.addPlayer(player);
    }

    void hide(ServerPlayer player) {
        bar.removePlayer(player);
    }

    /** Avance d'un tick. Renvoie true quand le vote est termine. */
    boolean tick() {
        ticksLeft--;
        if (ticksLeft % 20 == 0) {
            bar.setName(Component.literal("Vote de la map - " + (ticksLeft / 20) + " s - /melee vote"));
            bar.setProgress(Math.max(0, ticksLeft) / (float) durationTicks);
        }
        return ticksLeft <= 0;
    }

    /** Map gagnante : le plus de voix, egalite tiree au sort, et au hasard si personne n'a vote. */
    String winner(Random random) {
        int best = -1;
        List<String> top = new ArrayList<>();
        for (String map : options) {
            int c = count(map);
            if (c > best) {
                best = c;
                top.clear();
            }
            if (c == best) top.add(map);
        }
        return top.get(random.nextInt(top.size()));
    }

    void close() {
        bar.removeAllPlayers();
    }
}
