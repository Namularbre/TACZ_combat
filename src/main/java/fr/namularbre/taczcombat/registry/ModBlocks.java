package fr.namularbre.taczcombat.registry;

import fr.namularbre.taczcombat.TaczCombat;
import fr.namularbre.taczcombat.block.SpawnPointBlock;
import fr.namularbre.taczcombat.block.ZoneMarkerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TaczCombat.MOD_ID);

    // Incassables en survie / aventure : seuls les admins en creatif les retirent.
    public static final RegistryObject<Block> ZONE_MARKER = BLOCKS.register("zone_marker",
            () -> new ZoneMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.METAL)
                    .noLootTable()));

    public static final RegistryObject<Block> SPAWN_POINT = BLOCKS.register("spawn_point",
            () -> new SpawnPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.METAL)
                    .noLootTable()));

    private ModBlocks() {}
}
