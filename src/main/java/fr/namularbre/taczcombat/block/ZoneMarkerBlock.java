package fr.namularbre.taczcombat.block;

import fr.namularbre.taczcombat.arena.ArenaMap;
import fr.namularbre.taczcombat.arena.ArenaStore;
import fr.namularbre.taczcombat.arena.Pos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Borne de zone. Deux bornes posees a deux coins opposes delimitent le volume 3D de la map en edition.
 * Poser une troisieme borne remplace la plus ancienne.
 */
public class ZoneMarkerBlock extends Block {
    public ZoneMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ArenaMap map = ArenaBlockHelper.resolveMap(level, pos, placer);
        if (map == null) return;

        Pos replaced = null;
        if (map.corners.size() >= ArenaMap.MAX_CORNERS) {
            replaced = map.corners.remove(0);
        }
        map.corners.add(new Pos(pos));
        ArenaStore.get().save();

        if (replaced != null && level.getBlockState(replaced.toBlockPos()).is(this)) {
            level.removeBlock(replaced.toBlockPos(), false);
        }

        if (map.hasBounds()) {
            AABB box = map.bounds();
            placer.sendSystemMessage(Component.literal(String.format(
                    "Zone de %s definie : %d x %d x %d blocs.", map.name,
                    (int) box.getXsize(), (int) box.getYsize(), (int) box.getZsize()))
                    .withStyle(ChatFormatting.GREEN));
        } else {
            placer.sendSystemMessage(Component.literal(
                    "Borne 1/2 posee pour " + map.name + ". Pose la seconde au coin oppose (en hauteur aussi).")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            ArenaBlockHelper.onRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
