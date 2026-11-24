package fr.namularbre.taczcombat.registry;

import fr.namularbre.taczcombat.TaczCombat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TaczCombat.MOD_ID);

    public static final RegistryObject<Item> ZONE_MARKER = ITEMS.register("zone_marker",
            () -> new BlockItem(ModBlocks.ZONE_MARKER.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPAWN_POINT = ITEMS.register("spawn_point",
            () -> new BlockItem(ModBlocks.SPAWN_POINT.get(), new Item.Properties()));

    private ModItems() {}
}
