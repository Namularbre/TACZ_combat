package fr.namularbre.taczcombat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.namularbre.taczcombat.TaczCombat;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Regles de partie, dans {@code config/tacz_combat/rules.json}.
 * Modifiables avec {@code /melee rules set <regle> <valeur>} ou a la main puis {@code /melee reload}.
 */
public class CombatRules {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CombatRules current = new CombatRules();

    /** Kills pour gagner. 0 = pas de limite. */
    public int scoreLimit = 30;
    /** Duree de la partie en secondes. 0 = pas de limite. */
    public int timeLimitSeconds = 600;
    /** Delai avant reapparition, en secondes. */
    public int respawnDelaySeconds = 5;
    /** Protection apres apparition, en secondes. Annulee des que le joueur tire ou frappe. */
    public int spawnProtectionSeconds = 3;
    /** Duree du vote de map, en secondes. */
    public int voteSeconds = 30;
    /** 1 = un vote de map se lance tout seul apres chaque partie, 0 = non. */
    public int autoVote = 1;
    /** Pause entre la fin d'une partie et le vote automatique, en secondes. */
    public int nextVoteDelaySeconds = 10;

    /** Regles modifiables par commande : nom -> (lecture, ecriture). */
    public static final Map<String, Rule> RULES = new LinkedHashMap<>();

    public record Rule(Function<CombatRules, Integer> getter, BiConsumer<CombatRules, Integer> setter, String description) {}

    static {
        RULES.put("scoreLimit", new Rule(r -> r.scoreLimit, (r, v) -> r.scoreLimit = v, "kills pour gagner (0 = illimite)"));
        RULES.put("timeLimit", new Rule(r -> r.timeLimitSeconds, (r, v) -> r.timeLimitSeconds = v, "duree en secondes (0 = illimite)"));
        RULES.put("respawnDelay", new Rule(r -> r.respawnDelaySeconds, (r, v) -> r.respawnDelaySeconds = v, "delai de reapparition en secondes"));
        RULES.put("voteDuration", new Rule(r -> r.voteSeconds, (r, v) -> r.voteSeconds = v, "duree du vote de map en secondes"));
        RULES.put("autoVote", new Rule(r -> r.autoVote, (r, v) -> r.autoVote = v, "1 = vote automatique apres chaque partie"));
        RULES.put("nextVoteDelay", new Rule(r -> r.nextVoteDelaySeconds, (r, v) -> r.nextVoteDelaySeconds = v, "pause avant le vote automatique en secondes"));
        RULES.put("spawnProtection", new Rule(r -> r.spawnProtectionSeconds, (r, v) -> r.spawnProtectionSeconds = v, "protection au spawn en secondes"));
    }

    public static CombatRules get() {
        return current;
    }

    /** Copie figee des regles, prise au lancement d'une partie. */
    public CombatRules copy() {
        return GSON.fromJson(GSON.toJson(this), CombatRules.class);
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(TaczCombat.MOD_ID).resolve("rules.json");
    }

    public static void load() {
        Path file = file();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                CombatRules loaded = GSON.fromJson(reader, CombatRules.class);
                if (loaded != null) current = loaded;
            } catch (Exception e) {
                TaczCombat.LOGGER.error("Impossible de lire {}, regles par defaut utilisees", file, e);
            }
        }
        save(); // ecrit le fichier avec les valeurs par defaut s'il manquait des champs
    }

    public static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }
        } catch (IOException e) {
            TaczCombat.LOGGER.error("Impossible d'ecrire {}", file, e);
        }
    }
}
