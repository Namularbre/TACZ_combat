package fr.namularbre.taczcombat.loadout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.namularbre.taczcombat.TaczCombat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Armes, accessoires, munitions et objets interdits en partie, dans {@code config/tacz_combat/banned.json}.
 *
 * <p>Une entree est un identifiant, par exemple {@code tacz:ak47} (arme TACZ), {@code tacz:scope_acog_ta31}
 * (accessoire), {@code minecraft:ender_pearl} (objet). Un objet est interdit si son identifiant
 * est dans la liste, ou si son NBT en contient un (GunId, AttachmentId, AmmoId, accessoire monte sur une arme...).
 */
public final class BanList {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final BanList INSTANCE = new BanList();
    private static final int MAX_DEPTH = 8;
    /** Cles NBT TACZ qui identifient un objet, par ordre de priorite pour /melee ban hand. */
    private static final String[] TACZ_ID_KEYS = {"GunId", "AttachmentId", "AmmoId"};

    private final Set<String> banned = new TreeSet<>();

    public static BanList get() {
        return INSTANCE;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(TaczCombat.MOD_ID).resolve("banned.json");
    }

    public void load() {
        banned.clear();
        Path file = file();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> list = GSON.fromJson(reader, new TypeToken<List<String>>() {}.getType());
            if (list != null) list.stream().filter(Objects::nonNull).forEach(banned::add);
        } catch (Exception e) {
            TaczCombat.LOGGER.error("Impossible de lire {}", file, e);
        }
    }

    public void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(banned), writer);
            }
        } catch (IOException e) {
            TaczCombat.LOGGER.error("Impossible d'ecrire {}", file, e);
        }
    }

    public Set<String> all() {
        return Collections.unmodifiableSet(banned);
    }

    public boolean add(String id) {
        boolean added = banned.add(id);
        if (added) save();
        return added;
    }

    public boolean remove(String id) {
        boolean removed = banned.remove(id);
        if (removed) save();
        return removed;
    }

    /**
     * Identifiant a bannir pour l'objet tenu : l'arme / l'accessoire / la munition TACZ s'il y en a un,
     * sinon l'identifiant de l'objet.
     */
    public static String idOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (String key : TACZ_ID_KEYS) {
                if (tag.contains(key, Tag.TAG_STRING)) return tag.getString(key);
            }
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public boolean isBanned(ItemStack stack) {
        if (stack.isEmpty() || banned.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null && banned.contains(key.toString())) return true;
        CompoundTag tag = stack.getTag();
        return tag != null && containsBannedId(tag, 0);
    }

    /** Raison lisible : le premier identifiant interdit trouve dans l'objet. */
    public String bannedIdIn(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null && banned.contains(key.toString())) return key.toString();
        CompoundTag tag = stack.getTag();
        return tag == null ? null : findBannedId(tag, 0);
    }

    private boolean containsBannedId(CompoundTag tag, int depth) {
        return findBannedId(tag, depth) != null;
    }

    private String findBannedId(Tag tag, int depth) {
        if (depth > MAX_DEPTH) return null;
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag value = compound.get(key);
                if (value instanceof StringTag s && (key.equals("id") || key.endsWith("Id"))
                        && banned.contains(s.getAsString())) {
                    return s.getAsString();
                }
                String found = findBannedId(value, depth + 1);
                if (found != null) return found;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                String found = findBannedId(element, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Retire de l'inventaire tous les objets interdits. Renvoie les identifiants retires. */
    public List<String> purge(Inventory inventory) {
        List<String> removed = new ArrayList<>();
        if (banned.isEmpty()) return removed;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isBanned(stack)) {
                removed.add(bannedIdIn(stack));
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return removed;
    }
}
