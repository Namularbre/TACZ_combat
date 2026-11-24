package fr.namularbre.taczcombat.block;

import fr.namularbre.taczcombat.arena.ArenaMap;
import fr.namularbre.taczcombat.arena.ArenaStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Logique commune aux blocs de configuration (bornes et spawns). */
final class ArenaBlockHelper {
    private ArenaBlockHelper() {}

    static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }

    /**
     * Renvoie la map que le joueur est en train d'editer, ou null si le bloc ne peut pas etre rattache.
     * Dans ce cas le bloc pose est retire et le joueur est prevenu.
     */
    static ArenaMap resolveMap(Level level, BlockPos pos, LivingEntity placer) {
        if (!(level instanceof ServerLevel) || !(placer instanceof ServerPlayer player)) {
            return null;
        }
        ArenaMap map = ArenaStore.get().getEditing(player.getUUID());
        String dimension = dimensionOf(level);
        if (map == null) {
            player.sendSystemMessage(Component.literal(
                    "Aucune map en edition. Utilise /melee map create <nom> ou /melee map edit <nom>.")
                    .withStyle(ChatFormatting.RED));
            level.removeBlock(pos, false);
            return null;
        }
        if (map.dimension != null && !map.dimension.equals(dimension)) {
            player.sendSystemMessage(Component.literal(
                    "La map " + map.name + " est dans la dimension " + map.dimension + ".")
                    .withStyle(ChatFormatting.RED));
            level.removeBlock(pos, false);
            return null;
        }
        map.dimension = dimension;
        return map;
    }

    static void onRemoved(Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            ArenaStore.get().removeAt(dimensionOf(level), pos);
        }
    }
}
