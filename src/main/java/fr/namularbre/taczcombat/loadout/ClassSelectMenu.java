package fr.namularbre.taczcombat.loadout;

import fr.namularbre.taczcombat.game.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu de choix de classe, sous forme de coffre (cote serveur uniquement, aucun code client).
 * Cliquer sur une icone choisit la classe ; rien ne peut etre deplace.
 */
public class ClassSelectMenu extends ChestMenu {
    private final List<String> classBySlot;

    private ClassSelectMenu(MenuType<?> type, int id, Inventory playerInventory, SimpleContainer container, int rows,
                            List<String> classBySlot) {
        super(type, id, playerInventory, container, rows);
        this.classBySlot = classBySlot;
    }

    public static void open(ServerPlayer player) {
        List<CombatClass> classes = new ArrayList<>(ClassStore.get().all());
        if (classes.isEmpty()) {
            player.sendSystemMessage(Component.literal("Aucune classe definie.").withStyle(ChatFormatting.RED));
            return;
        }
        int rows = Math.min(6, Math.max(1, (classes.size() + 8) / 9));
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        String current = GameManager.get().chosenClass(player);
        SimpleContainer container = new SimpleContainer(rows * 9);
        List<String> classBySlot = new ArrayList<>();
        for (int i = 0; i < classes.size() && i < rows * 9; i++) {
            CombatClass c = classes.get(i);
            container.setItem(i, iconFor(c, c.name.equals(current)));
            classBySlot.add(c.name);
        }
        final int menuRows = rows;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new ClassSelectMenu(type, id, inventory, container, menuRows, classBySlot),
                Component.literal("Choisis ta classe")));
    }

    private static ItemStack iconFor(CombatClass c, boolean selected) {
        ItemStack icon = c.icon();
        if (icon.isEmpty()) icon = new ItemStack(Items.IRON_SWORD);
        icon.setCount(1);
        icon.setHoverName(Component.literal(c.name)
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                .withStyle(s -> s.withItalic(false)));

        ListTag lore = new ListTag();
        if (selected) lore.add(loreLine(Component.literal("Classe actuelle").withStyle(ChatFormatting.GREEN)));
        for (ItemStack stack : c.stacks()) {
            String line = (stack.getCount() > 1 ? stack.getCount() + " x " : "") + stack.getHoverName().getString();
            lore.add(loreLine(Component.literal("- " + line).withStyle(ChatFormatting.GRAY)));
        }
        CompoundTag display = icon.getOrCreateTagElement("display");
        display.put("Lore", lore);
        return icon;
    }

    private static StringTag loreLine(Component component) {
        return StringTag.valueOf(Component.Serializer.toJson(component.copy().withStyle(s -> s.withItalic(false))));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Aucun deplacement d'objet : on ne fait que lire la case cliquee.
        if (player instanceof ServerPlayer serverPlayer && slotId >= 0 && slotId < classBySlot.size()) {
            String name = classBySlot.get(slotId);
            serverPlayer.closeContainer();
            GameManager.get().chooseClass(serverPlayer, name);
            return;
        }
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
