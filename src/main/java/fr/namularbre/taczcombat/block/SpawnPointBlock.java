package fr.namularbre.taczcombat.block;

import fr.namularbre.taczcombat.arena.ArenaMap;
import fr.namularbre.taczcombat.arena.ArenaStore;
import fr.namularbre.taczcombat.arena.Pos;
import fr.namularbre.taczcombat.arena.SpawnPoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Point d'apparition. Le joueur reapparait sur le bloc, tourne dans la direction
 * ou regardait l'admin quand il l'a pose (indiquee par la fleche).
 */
public class SpawnPointBlock extends HorizontalDirectionalBlock {
    public SpawnPointBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ArenaMap map = ArenaBlockHelper.resolveMap(level, pos, placer);
        if (map == null) return;

        float yaw = state.getValue(FACING).toYRot();
        map.spawns.add(new SpawnPoint(new Pos(pos), yaw));
        ArenaStore.get().save();

        String warning = map.contains(pos.above().getCenter()) || !map.hasBounds() ? "" : " Attention : il est hors de la zone !";
        placer.sendSystemMessage(Component.literal(
                "Spawn #" + map.spawns.size() + " ajoute a " + map.name + "." + warning)
                .withStyle(warning.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GOLD));
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
