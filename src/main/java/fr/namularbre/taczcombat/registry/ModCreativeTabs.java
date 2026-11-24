package fr.namularbre.taczcombat.registry;

import fr.namularbre.taczcombat.TaczCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TaczCombat.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + TaczCombat.MOD_ID))
            .icon(() -> new ItemStack(ModItems.ZONE_MARKER.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.ZONE_MARKER.get());
                output.accept(ModItems.SPAWN_POINT.get());
            })
            .build());

    private ModCreativeTabs() {}
}
