package fr.namularbre.taczcombat.loadout;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.namularbre.taczcombat.TaczCombat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Une classe jouable : le contenu exact d'un inventaire (barre d'action, sac, armure, main secondaire).
 * Chaque objet est stocke en SNBT, ce qui conserve tout le NBT de TACZ :
 * arme (GunId), accessoires montes (lunette, silencieux, chargeur...), munitions, mode de tir.
 */
public class CombatClass {
    public String name;
    /** Une entree par emplacement, au format de {@link Inventory#save} (avec la cle "Slot"). */
    public List<String> items = new ArrayList<>();

    public CombatClass() {}

    public CombatClass(String name) {
        this.name = name;
    }

    /** Copie l'inventaire du joueur dans la classe. */
    public static CombatClass fromInventory(String name, Inventory inventory) {
        CombatClass c = new CombatClass(name);
        ListTag list = inventory.save(new ListTag());
        for (int i = 0; i < list.size(); i++) {
            c.items.add(list.getCompound(i).toString());
        }
        return c;
    }

    /** Remplace l'inventaire du joueur par celui de la classe. */
    public void applyTo(Inventory inventory) {
        inventory.load(toListTag());
        inventory.setChanged();
    }

    public ListTag toListTag() {
        ListTag list = new ListTag();
        for (String snbt : items) {
            try {
                list.add(TagParser.parseTag(snbt));
            } catch (CommandSyntaxException e) {
                TaczCombat.LOGGER.error("Objet invalide dans la classe {} : {}", name, snbt, e);
            }
        }
        return list;
    }

    /** Objets de la classe, dans l'ordre des emplacements (barre d'action d'abord). */
    public List<ItemStack> stacks() {
        List<ItemStack> stacks = new ArrayList<>();
        ListTag list = toListTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemStack stack = ItemStack.of(tag);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    /** Icone du menu : le premier objet de la classe (en general l'arme principale). */
    public ItemStack icon() {
        List<ItemStack> stacks = stacks();
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0).copy();
    }
}
