package fr.namularbre.taczcombat.game;

import fr.namularbre.taczcombat.TaczCombat;
import fr.namularbre.taczcombat.arena.ArenaStore;
import fr.namularbre.taczcombat.command.MeleeCommand;
import fr.namularbre.taczcombat.compat.TaczGlassGuard;
import fr.namularbre.taczcombat.config.CombatRules;
import fr.namularbre.taczcombat.loadout.BanList;
import fr.namularbre.taczcombat.loadout.ClassStore;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczCombat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GameEvents {
    private GameEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MeleeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CombatRules.load();
        TaczGlassGuard.apply();
        ClassStore.get().load();
        BanList.get().load();
        ArenaStore.get().load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GameManager.get().stop("Arret du serveur");
        GameManager.get().reset();
        ArenaStore.get().unload();
        TaczGlassGuard.restore();
    }

    private static int glassGuardTicks;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            GameManager.get().tick();
            // La config TACZ peut etre rechargee depuis son fichier : on reapplique la regle toutes les 5 s.
            if (++glassGuardTicks >= 100) {
                glassGuardTicks = 0;
                TaczGlassGuard.apply();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GameManager.get().onLogout(player);
        }
    }

    /** Une mort en partie ne tue pas vraiment : pas de drop, le joueur passe spectateur puis reapparait. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        MeleeGame game = GameManager.get().current();
        if (game == null || game.isFinished() || !game.isParticipant(victim)) return;

        Entity source = event.getSource().getEntity();
        ServerPlayer killer = source instanceof ServerPlayer sp ? sp : null;
        if (game.onDeath(victim, killer)) {
            event.setCanceled(true);
        }
    }

    /** Protection au spawn, et perte de la protection quand le joueur frappe. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        MeleeGame game = GameManager.get().current();
        if (game == null || game.isFinished()) return;

        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            game.onPlayerAttack(attacker);
        }
        if (event.getEntity() instanceof ServerPlayer target && game.isInvulnerable(target)) {
            event.setCanceled(true);
        }
    }

    /** Pas de jet d'objet en partie : l'objet revient dans l'inventaire. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !isInGame(player)) return;
        event.setCanceled(true);
        player.getInventory().add(event.getEntity().getItem().copy());
        player.inventoryMenu.broadcastChanges();
    }

    /** Pas de ramassage en partie : chacun joue avec sa classe. */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInGame(player)) {
            event.setCanceled(true);
        }
    }

    /** Un objet interdit ne peut pas etre utilise en partie (clic droit). */
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && isInGame(player)
                && BanList.get().isBanned(event.getItemStack())) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Objet interdit dans cette partie")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    private static boolean isInGame(ServerPlayer player) {
        MeleeGame game = GameManager.get().current();
        return game != null && !game.isFinished() && game.isParticipant(player);
    }

    /** Un projectile (balle TACZ comprise) tire par un joueur protege annule sa protection. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) return;
        MeleeGame game = GameManager.get().current();
        if (game == null || game.isFinished()) return;
        if (projectile.getOwner() instanceof ServerPlayer shooter) {
            game.onPlayerAttack(shooter);
        }
    }
}
