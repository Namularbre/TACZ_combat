package fr.namularbre.taczcombat.game;

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

import java.util.List;

/** Menu coffre du vote de map (cote serveur uniquement). */
public class MapVoteMenu extends ChestMenu {
    private final List<String> mapBySlot;

    private MapVoteMenu(MenuType<?> type, int id, Inventory playerInventory, SimpleContainer container, int rows,
                        List<String> mapBySlot) {
        super(type, id, playerInventory, container, rows);
        this.mapBySlot = mapBySlot;
    }

    static void open(ServerPlayer player, MapVote vote) {
        List<String> maps = vote.options();
        int rows = Math.min(6, Math.max(1, (maps.size() + 8) / 9));
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        String mine = vote.voteOf(player.getUUID());
        SimpleContainer container = new SimpleContainer(rows * 9);
        List<String> shown = maps.subList(0, Math.min(maps.size(), rows * 9));
        for (int i = 0; i < shown.size(); i++) {
            String map = shown.get(i);
            boolean selected = map.equals(mine);
            ItemStack icon = new ItemStack(selected ? Items.FILLED_MAP : Items.MAP);
            icon.setHoverName(Component.literal(map)
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                    .withStyle(s -> s.withItalic(false)));
            int votes = vote.count(map);
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(votes + " vote(s)")
                    .withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false)))));
            if (selected) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Ton choix")
                        .withStyle(ChatFormatting.GREEN).withStyle(s -> s.withItalic(false)))));
            }
            CompoundTag display = icon.getOrCreateTagElement("display");
            display.put("Lore", lore);
            container.setItem(i, icon);
        }
        final int menuRows = rows;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new MapVoteMenu(type, id, inventory, container, menuRows, shown),
                Component.literal("Vote : prochaine map")));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player instanceof ServerPlayer serverPlayer && slotId >= 0 && slotId < mapBySlot.size()) {
            serverPlayer.closeContainer();
            GameManager.get().vote(serverPlayer, mapBySlot.get(slotId));
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
