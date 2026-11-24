package fr.namularbre.taczcombat.game;

import fr.namularbre.taczcombat.arena.ArenaMap;
import fr.namularbre.taczcombat.arena.SpawnPoint;
import fr.namularbre.taczcombat.config.CombatRules;
import fr.namularbre.taczcombat.loadout.BanList;
import fr.namularbre.taczcombat.loadout.ClassSelectMenu;
import fr.namularbre.taczcombat.loadout.ClassStore;
import fr.namularbre.taczcombat.loadout.CombatClass;
import net.minecraft.nbt.ListTag;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.*;

/** Une partie de melee generale (chacun pour soi) sur une map. */
public class MeleeGame {
    private static final String OBJECTIVE = "tacz_combat_kills";

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ArenaMap map;
    private final CombatRules rules;
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final ServerBossEvent timerBar;
    private final Random random = new Random();
    /** Partie avec classes : l'inventaire est remplace par la classe choisie, puis rendu a la fin. */
    private final boolean useClasses;
    private Objective objective;
    private int tick;
    private boolean finished;

    MeleeGame(MinecraftServer server, ServerLevel level, ArenaMap map, CombatRules rules) {
        this.server = server;
        this.level = level;
        this.map = map;
        this.rules = rules;
        this.useClasses = !ClassStore.get().isEmpty();
        this.timerBar = new ServerBossEvent(Component.literal(map.name), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    }

    public ArenaMap map() {
        return map;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isParticipant(ServerPlayer player) {
        return participants.containsKey(player.getUUID());
    }

    // ---------------------------------------------------------------- cycle de vie

    void start(Collection<ServerPlayer> players) {
        ServerScoreboard scoreboard = server.getScoreboard();
        Objective old = scoreboard.getObjective(OBJECTIVE);
        if (old != null) scoreboard.removeObjective(old);
        objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                Component.literal("Melee - " + map.name).withStyle(ChatFormatting.GOLD), ObjectiveCriteria.RenderType.INTEGER);
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);

        for (ServerPlayer player : players) {
            Participant p = new Participant(player.getUUID(), player.getScoreboardName(),
                    player.gameMode.getGameModeForPlayer(), player.level().dimension(),
                    player.position(), player.getYRot(), player.getXRot());
            participants.put(p.id, p);
            if (useClasses) {
                p.savedInventory = player.getInventory().save(new ListTag());
                p.chosenClass = GameManager.get().chosenClass(player);
            }
            scoreboard.getOrCreatePlayerScore(p.name, objective).setScore(0);
            timerBar.addPlayer(player);
            respawn(player, p);
        }
        String goal = rules.scoreLimit > 0 ? "premier a " + rules.scoreLimit + " kills" : "le plus de kills";
        broadcastTitle(Component.literal("Melee generale !").withStyle(ChatFormatting.RED),
                Component.literal(map.name + " - " + goal));
        updateTimerBar();
    }

    void tick() {
        if (finished) return;
        tick++;

        for (Participant p : participants.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(p.id);
            if (player == null) continue;

            if (p.state == Participant.State.DEAD) {
                if (tick >= p.respawnAtTick) {
                    respawn(player, p);
                } else if ((p.respawnAtTick - tick) % 20 == 0) {
                    int seconds = (p.respawnAtTick - tick) / 20;
                    player.displayClientMessage(Component.literal("Reapparition dans " + seconds + " s")
                            .withStyle(ChatFormatting.GRAY), true);
                }
                continue;
            }

            keepInsideZone(player, p);
            if (tick % 20 == 0) purgeBanned(player);
            if (p.protectedUntilTick > 0 && tick == p.protectedUntilTick) {
                endProtection(player, p);
            }
        }

        if (tick % 20 == 0) updateTimerBar();
        if (rules.timeLimitSeconds > 0 && tick >= rules.timeLimitSeconds * 20) {
            finish("Temps ecoule");
        }
    }

    /** Termine la partie, annonce le gagnant et rend a chacun son etat d'avant. */
    void finish(String reason) {
        if (finished) return;
        finished = true;

        List<Participant> ranking = new ArrayList<>(participants.values());
        ranking.sort(Comparator.comparingInt((Participant p) -> p.kills).reversed()
                .thenComparingInt(p -> p.deaths));
        Component title = ranking.isEmpty()
                ? Component.literal("Fin de la partie")
                : Component.literal(ranking.get(0).name + " gagne !").withStyle(ChatFormatting.GOLD);
        Component subtitle = Component.literal(reason);
        broadcastTitle(title, subtitle);

        broadcast(Component.literal("=== Resultats " + map.name + " ===").withStyle(ChatFormatting.GOLD));
        int rank = 1;
        for (Participant p : ranking) {
            broadcast(Component.literal(rank++ + ". " + p.name + " - " + p.kills + " kills / " + p.deaths + " morts"));
        }

        for (Participant p : new ArrayList<>(participants.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(p.id);
            if (player != null) restore(player, p);
        }
        participants.clear();
        cleanupDisplay();
    }

    private void cleanupDisplay() {
        timerBar.removeAllPlayers();
        ServerScoreboard scoreboard = server.getScoreboard();
        Objective current = scoreboard.getObjective(OBJECTIVE);
        if (current != null) scoreboard.removeObjective(current);
    }

    /** Retire un joueur de la partie (deconnexion ou /melee leave) et le remet dans son etat d'avant. */
    void removePlayer(ServerPlayer player) {
        Participant p = participants.remove(player.getUUID());
        if (p == null) return;
        restore(player, p);
        timerBar.removePlayer(player);
        broadcast(Component.literal(p.name + " a quitte la partie.").withStyle(ChatFormatting.GRAY));
        if (participants.isEmpty()) finish("Plus aucun joueur");
    }

    // ---------------------------------------------------------------- evenements

    /**
     * Appele a la place de la mort d'un participant. Renvoie true si la mort a ete prise en charge
     * (l'evenement vanilla doit alors etre annule : pas de drop, pas d'ecran de mort).
     */
    boolean onDeath(ServerPlayer victim, ServerPlayer killer) {
        Participant v = participants.get(victim.getUUID());
        if (v == null) return false;
        if (v.state == Participant.State.DEAD) return true;

        v.state = Participant.State.DEAD;
        v.deaths++;
        v.respawnAtTick = tick + Math.max(1, rules.respawnDelaySeconds * 20);
        v.protectedUntilTick = 0;
        victim.setHealth(victim.getMaxHealth());
        victim.clearFire();
        victim.removeAllEffects();
        victim.setGameMode(GameType.SPECTATOR);
        sendTitle(victim, Component.literal("Mort").withStyle(ChatFormatting.DARK_RED),
                Component.literal(killer != null && killer != victim ? "Tue par " + killer.getScoreboardName() : ""));

        Participant k = killer == null ? null : participants.get(killer.getUUID());
        if (k != null && k != v) {
            k.kills++;
            server.getScoreboard().getOrCreatePlayerScore(k.name, objective).setScore(k.kills);
            broadcast(Component.literal(k.name).withStyle(ChatFormatting.RED)
                    .append(Component.literal(" a elimine ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(v.name).withStyle(ChatFormatting.WHITE)));
            if (rules.scoreLimit > 0 && k.kills >= rules.scoreLimit) {
                finish(k.name + " atteint " + rules.scoreLimit + " kills");
            }
        } else {
            broadcast(Component.literal(v.name + " est mort.").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    /** True si le joueur ne doit pas prendre de degats (protection au spawn ou en attente de reapparition). */
    boolean isInvulnerable(ServerPlayer player) {
        Participant p = participants.get(player.getUUID());
        if (p == null) return false;
        return p.state == Participant.State.DEAD || tick < p.protectedUntilTick;
    }

    /** Le joueur a tire ou frappe : il perd sa protection de spawn. */
    void onPlayerAttack(ServerPlayer player) {
        Participant p = participants.get(player.getUUID());
        if (p != null && p.state == Participant.State.ALIVE && tick < p.protectedUntilTick) {
            p.protectedUntilTick = tick;
            endProtection(player, p);
        }
    }

    // ---------------------------------------------------------------- internes

    private void respawn(ServerPlayer player, Participant p) {
        SpawnPoint spawn = pickSpawn(p);
        BlockPos pos = spawn.pos.toBlockPos();
        p.lastSpawn = pos;
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, spawn.yaw, 0);
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.clearFire();
        player.removeAllEffects();
        player.fallDistance = 0;
        p.state = Participant.State.ALIVE;
        p.lastSafePosition = player.position();
        if (useClasses) {
            applyClass(player, p);
            ClassSelectMenu.open(player);
        }
        if (rules.spawnProtectionSeconds > 0) {
            p.protectedUntilTick = tick + rules.spawnProtectionSeconds * 20;
            player.displayClientMessage(Component.literal("Protege " + rules.spawnProtectionSeconds + " s (annule si tu tires)")
                    .withStyle(ChatFormatting.AQUA), true);
        } else {
            p.protectedUntilTick = 0;
        }
    }

    public String chosenClassOf(ServerPlayer player) {
        Participant p = participants.get(player.getUUID());
        return p == null ? null : p.chosenClass;
    }

    /**
     * Change la classe d'un joueur. Appliquee tout de suite s'il est encore protege
     * (il vient d'apparaitre et n'a pas tire), sinon a sa prochaine reapparition.
     */
    void chooseClass(ServerPlayer player, String className) {
        Participant p = participants.get(player.getUUID());
        if (p == null || !useClasses) return;
        p.chosenClass = className;
        if (p.state == Participant.State.ALIVE && tick < p.protectedUntilTick) {
            applyClass(player, p);
            player.displayClientMessage(Component.literal("Classe " + className + " equipee").withStyle(ChatFormatting.GREEN), true);
        } else {
            player.sendSystemMessage(Component.literal("Classe " + className + " a ta prochaine reapparition.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private void applyClass(ServerPlayer player, Participant p) {
        CombatClass c = ClassStore.get().get(p.chosenClass);
        if (c == null) {
            c = ClassStore.get().first();
            if (c == null) return;
            p.chosenClass = c.name;
        }
        c.applyTo(player.getInventory());
        purgeBanned(player);
        player.inventoryMenu.broadcastChanges();
    }

    /** Retire les objets interdits de l'inventaire du joueur et le previent. */
    private void purgeBanned(ServerPlayer player) {
        List<String> removed = BanList.get().purge(player.getInventory());
        if (!removed.isEmpty()) {
            player.inventoryMenu.broadcastChanges();
            player.sendSystemMessage(Component.literal("Objet(s) interdit(s) retire(s) : " + String.join(", ", removed))
                    .withStyle(ChatFormatting.RED));
        }
    }

    private void endProtection(ServerPlayer player, Participant p) {
        player.displayClientMessage(Component.literal("Protection terminee").withStyle(ChatFormatting.YELLOW), true);
    }

    /** Choisit le spawn le plus eloigne des adversaires vivants (au hasard parmi les meilleurs). */
    private SpawnPoint pickSpawn(Participant self) {
        List<Vec3> enemies = new ArrayList<>();
        for (Participant other : participants.values()) {
            if (other == self || other.state != Participant.State.ALIVE) continue;
            ServerPlayer op = server.getPlayerList().getPlayer(other.id);
            if (op != null) enemies.add(op.position());
        }
        List<SpawnPoint> spawns = new ArrayList<>(map.spawns);
        Collections.shuffle(spawns, random);
        if (enemies.isEmpty()) return spawns.get(0);

        SpawnPoint best = spawns.get(0);
        double bestDistance = -1;
        for (SpawnPoint spawn : spawns) {
            Vec3 at = Vec3.atCenterOf(spawn.pos.toBlockPos());
            double nearest = Double.MAX_VALUE;
            for (Vec3 enemy : enemies) nearest = Math.min(nearest, enemy.distanceToSqr(at));
            if (nearest > bestDistance) {
                bestDistance = nearest;
                best = spawn;
            }
        }
        return best;
    }

    /** Ramene le joueur dans la zone s'il en sort. */
    private void keepInsideZone(ServerPlayer player, Participant p) {
        if (player.level() != level) {
            respawn(player, p);
            return;
        }
        if (map.contains(player.position())) {
            p.lastSafePosition = player.position();
            return;
        }
        Vec3 back = p.lastSafePosition != null ? p.lastSafePosition : Vec3.atBottomCenterOf(p.lastSpawn.above());
        player.teleportTo(level, back.x, back.y, back.z, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.literal("Limite de la zone").withStyle(ChatFormatting.RED), true);
    }

    private void restore(ServerPlayer player, Participant p) {
        ServerLevel target = server.getLevel(p.previousDimension);
        if (target == null) target = server.overworld();
        if (player.containerMenu instanceof ClassSelectMenu) player.closeContainer();
        if (p.savedInventory != null) {
            player.getInventory().load(p.savedInventory);
            player.inventoryMenu.broadcastChanges();
        }
        player.setGameMode(p.previousGameMode);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.removeAllEffects();
        player.teleportTo(target, p.previousPosition.x, p.previousPosition.y, p.previousPosition.z, p.previousYaw, p.previousPitch);
    }

    private void updateTimerBar() {
        if (rules.timeLimitSeconds <= 0) {
            timerBar.setName(Component.literal(map.name + " - pas de limite de temps"));
            timerBar.setProgress(1.0F);
            return;
        }
        int remaining = Math.max(0, rules.timeLimitSeconds - tick / 20);
        timerBar.setName(Component.literal(String.format("%s - %d:%02d", map.name, remaining / 60, remaining % 60)));
        timerBar.setProgress(remaining / (float) rules.timeLimitSeconds);
    }

    private void broadcast(Component message) {
        for (UUID id : participants.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(message);
        }
    }

    private void broadcastTitle(Component title, Component subtitle) {
        for (UUID id : participants.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) sendTitle(player, title, subtitle);
        }
    }

    private static void sendTitle(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
