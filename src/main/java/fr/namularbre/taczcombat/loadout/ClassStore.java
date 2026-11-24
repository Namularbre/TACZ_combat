package fr.namularbre.taczcombat.loadout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.namularbre.taczcombat.TaczCombat;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Classes jouables, dans {@code config/tacz_combat/classes.json}. */
public final class ClassStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ClassStore INSTANCE = new ClassStore();

    private final Map<String, CombatClass> classes = new LinkedHashMap<>();

    public static ClassStore get() {
        return INSTANCE;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(TaczCombat.MOD_ID).resolve("classes.json");
    }

    public void load() {
        classes.clear();
        Path file = file();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<CombatClass> list = GSON.fromJson(reader, new TypeToken<List<CombatClass>>() {}.getType());
            if (list != null) {
                for (CombatClass c : list) {
                    if (c == null || c.name == null) continue;
                    if (c.items == null) c.items = new ArrayList<>();
                    classes.put(c.name, c);
                }
            }
            TaczCombat.LOGGER.info("{} classe(s) chargee(s)", classes.size());
        } catch (Exception e) {
            TaczCombat.LOGGER.error("Impossible de lire {}", file, e);
        }
    }

    public void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(classes.values()), writer);
            }
        } catch (IOException e) {
            TaczCombat.LOGGER.error("Impossible d'ecrire {}", file, e);
        }
    }

    public boolean isEmpty() {
        return classes.isEmpty();
    }

    public Collection<CombatClass> all() {
        return Collections.unmodifiableCollection(classes.values());
    }

    public CombatClass get(String name) {
        return name == null ? null : classes.get(name);
    }

    public CombatClass first() {
        return classes.isEmpty() ? null : classes.values().iterator().next();
    }

    public void put(CombatClass c) {
        classes.put(c.name, c);
        save();
    }

    public boolean delete(String name) {
        boolean removed = classes.remove(name) != null;
        if (removed) save();
        return removed;
    }
}
