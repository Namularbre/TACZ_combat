package fr.namularbre.taczcombat.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.namularbre.taczcombat.TaczCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Liste des maps du monde, sauvegardee dans {@code <monde>/tacz_combat/maps.json}.
 * Le fichier est editable a la main puis rechargeable avec {@code /melee reload}.
 */
public final class ArenaStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ArenaStore INSTANCE = new ArenaStore();

    private final Map<String, ArenaMap> maps = new LinkedHashMap<>();
    /** Map en cours d'edition par chaque admin : les bornes et spawns poses y sont rattaches. */
    private final Map<UUID, String> editing = new HashMap<>();
    private Path file;

    public static ArenaStore get() {
        return INSTANCE;
    }

    public void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(TaczCombat.MOD_ID).resolve("maps.json");
        maps.clear();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<ArenaMap> list = GSON.fromJson(reader, new TypeToken<List<ArenaMap>>() {}.getType());
            if (list != null) {
                for (ArenaMap map : list) {
                    if (map == null || map.name == null) continue;
                    if (map.corners == null) map.corners = new ArrayList<>();
                    if (map.spawns == null) map.spawns = new ArrayList<>();
                    maps.put(map.name, map);
                }
            }
            TaczCombat.LOGGER.info("{} map(s) de melee chargee(s)", maps.size());
        } catch (Exception e) {
            TaczCombat.LOGGER.error("Impossible de lire {}", file, e);
        }
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(maps.values()), writer);
            }
        } catch (IOException e) {
            TaczCombat.LOGGER.error("Impossible d'ecrire {}", file, e);
        }
    }

    public void unload() {
        maps.clear();
        editing.clear();
        file = null;
    }

    public Collection<ArenaMap> all() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public ArenaMap get(String name) {
        return maps.get(name);
    }

    public ArenaMap create(String name) {
        ArenaMap map = new ArenaMap(name);
        maps.put(name, map);
        save();
        return map;
    }

    public boolean delete(String name) {
        boolean removed = maps.remove(name) != null;
        if (removed) {
            editing.values().removeIf(name::equals);
            save();
        }
        return removed;
    }

    public void setEditing(UUID player, String mapName) {
        editing.put(player, mapName);
    }

    public ArenaMap getEditing(UUID player) {
        String name = editing.get(player);
        return name == null ? null : maps.get(name);
    }

    /** Retire de toutes les maps la borne ou le spawn situe a cette position. */
    public void removeAt(String dimension, BlockPos pos) {
        boolean changed = false;
        for (ArenaMap map : maps.values()) {
            if (dimension.equals(map.dimension)) {
                changed |= map.removeAt(pos);
            }
        }
        if (changed) save();
    }
}
