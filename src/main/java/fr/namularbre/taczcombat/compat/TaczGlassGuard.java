package fr.namularbre.taczcombat.compat;

import fr.namularbre.taczcombat.TaczCombat;
import fr.namularbre.taczcombat.config.CombatRules;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Applique la regle {@code destroyGlass} a TACZ.
 *
 * <p>TACZ casse les vitres touchees par une balle si son option {@code DestroyGlass} est vraie.
 * Cette option est dans la config COMMON de TACZ ({@code config/tacz-common.toml}, section {@code [ammo]}),
 * pas dans {@code serverconfig/tacz-server.toml}. On force sa valeur depuis la regle du mod, ce qui marche
 * quel que soit le fichier modifie a la main.</p>
 *
 * <p>Pas de dependance de compilation vers TACZ : le champ {@code AmmoConfig.DESTROY_GLASS} est lu par reflexion.
 * Il existe dans TACZ 1.1.x et 1.2.x pour 1.20.1.</p>
 */
public final class TaczGlassGuard {
    private static final String AMMO_CONFIG = "com.tacz.guns.config.common.AmmoConfig";

    private static ForgeConfigSpec.BooleanValue destroyGlass;
    private static boolean lookedUp;
    /** Valeur de TACZ avant qu'on la force, pour la remettre si la regle repasse a 1 ou a l'arret. */
    private static Boolean original;

    private TaczGlassGuard() {}

    private static ForgeConfigSpec.BooleanValue option() {
        if (!lookedUp) {
            lookedUp = true;
            try {
                Object value = Class.forName(AMMO_CONFIG).getField("DESTROY_GLASS").get(null);
                if (value instanceof ForgeConfigSpec.BooleanValue b) {
                    destroyGlass = b;
                } else {
                    TaczCombat.LOGGER.warn("TACZ : AmmoConfig.DESTROY_GLASS introuvable, la regle destroyGlass sera sans effet");
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                TaczCombat.LOGGER.warn("TACZ : option DestroyGlass introuvable, la regle destroyGlass sera sans effet", e);
            }
        }
        return destroyGlass;
    }

    /** Aligne TACZ sur la regle. A appeler au demarrage, apres un changement de regle, et regulierement. */
    public static void apply() {
        ForgeConfigSpec.BooleanValue opt = option();
        if (opt == null) return;
        try {
            boolean allowed = CombatRules.get().destroyGlass != 0;
            boolean current = opt.get();
            if (!allowed) {
                if (current) {
                    if (original == null) original = true;
                    opt.set(false);
                    TaczCombat.LOGGER.info("TACZ : casse des vitres par les balles desactivee (regle destroyGlass = 0)");
                }
            } else if (original != null) {
                if (current != original) opt.set(original);
                original = null;
            }
        } catch (IllegalStateException | NullPointerException e) {
            // Config TACZ pas encore chargee : on reessaiera au prochain appel.
        }
    }

    /** Remet la valeur d'origine de TACZ, pour ne pas laisser son fichier modifie apres l'arret. */
    public static void restore() {
        ForgeConfigSpec.BooleanValue opt = option();
        if (opt == null || original == null) return;
        try {
            opt.set(original);
        } catch (IllegalStateException | NullPointerException ignored) {
            // rien a remettre
        }
        original = null;
    }

    /** Etat effectif cote TACZ, ou null si TACZ n'expose pas l'option. */
    public static Boolean taczValue() {
        ForgeConfigSpec.BooleanValue opt = option();
        if (opt == null) return null;
        try {
            return opt.get();
        } catch (IllegalStateException | NullPointerException e) {
            return null;
        }
    }
}
