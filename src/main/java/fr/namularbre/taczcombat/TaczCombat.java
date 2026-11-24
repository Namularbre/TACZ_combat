package fr.namularbre.taczcombat;

import com.mojang.logging.LogUtils;
import fr.namularbre.taczcombat.registry.ModBlocks;
import fr.namularbre.taczcombat.registry.ModCreativeTabs;
import fr.namularbre.taczcombat.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TaczCombat.MOD_ID)
public class TaczCombat {
    public static final String MOD_ID = "tacz_combat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczCombat() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        // Les evenements serveur (commandes, partie) sont enregistres via @Mod.EventBusSubscriber.
    }
}
