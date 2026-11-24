package fr.namularbre.taczcombat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.namularbre.taczcombat.arena.ArenaMap;
import fr.namularbre.taczcombat.arena.ArenaStore;
import fr.namularbre.taczcombat.config.CombatRules;
import fr.namularbre.taczcombat.game.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/**
 * /melee join | leave
 * /melee start <map> | stop                          (admin)
 * /melee map create|edit|delete|info <nom> | list    (admin)
 * /melee rules [set <regle> <valeur>]                (admin)
 * /melee reload                                      (admin)
 */
public final class MeleeCommand {
    private static final int ADMIN = 2;

    private static final SuggestionProvider<CommandSourceStack> MAP_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ArenaStore.get().all().stream().map(m -> m.name), builder);
    private static final SuggestionProvider<CommandSourceStack> RULE_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CombatRules.RULES.keySet(), builder);

    private MeleeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("melee")
                .then(Commands.literal("join").executes(MeleeCommand::join))
                .then(Commands.literal("leave").executes(MeleeCommand::leave))
                .then(Commands.literal("start").requires(s -> s.hasPermission(ADMIN))
                        .then(Commands.argument("map", StringArgumentType.word()).suggests(MAP_NAMES)
                                .executes(MeleeCommand::start)))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(ADMIN))
                        .executes(MeleeCommand::stop))
                .then(Commands.literal("map").requires(s -> s.hasPermission(ADMIN))
                        .then(Commands.literal("list").executes(MeleeCommand::mapList))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word()).executes(MeleeCommand::mapCreate)))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(MAP_NAMES).executes(MeleeCommand::mapEdit)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(MAP_NAMES).executes(MeleeCommand::mapDelete)))
                        .then(Commands.literal("info")
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(MAP_NAMES).executes(MeleeCommand::mapInfo))))
                .then(Commands.literal("rules").requires(s -> s.hasPermission(ADMIN))
                        .executes(MeleeCommand::rulesShow)
                        .then(Commands.literal("set")
                                .then(Commands.argument("rule", StringArgumentType.word()).suggests(RULE_NAMES)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(MeleeCommand::rulesSet)))))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(ADMIN))
                        .executes(MeleeCommand::reload)));
    }

    // ---------------------------------------------------------------- joueurs

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.get().join(player)) {
            ok(ctx, "Tu es inscrit a la prochaine partie.");
        } else {
            ok(ctx, "Tu es deja inscrit.");
        }
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.get().leave(player)) {
            ok(ctx, "Tu n'es plus inscrit.");
        } else {
            fail(ctx, "Tu n'etais pas inscrit.");
        }
        return 1;
    }

    // ---------------------------------------------------------------- partie

    private static int start(CommandContext<CommandSourceStack> ctx) {
        ArenaMap map = findMap(ctx, StringArgumentType.getString(ctx, "map"));
        if (map == null) return 0;
        String error = GameManager.get().start(ctx.getSource().getServer(), map);
        if (error != null) {
            fail(ctx, error);
            return 0;
        }
        ok(ctx, "Partie lancee sur " + map.name + ".");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        if (GameManager.get().stop("Arretee par un admin")) {
            ok(ctx, "Partie arretee.");
            return 1;
        }
        fail(ctx, "Aucune partie en cours.");
        return 0;
    }

    // ---------------------------------------------------------------- maps

    private static int mapList(CommandContext<CommandSourceStack> ctx) {
        if (ArenaStore.get().all().isEmpty()) {
            ok(ctx, "Aucune map. Cree-en une avec /melee map create <nom>.");
            return 0;
        }
        for (ArenaMap map : ArenaStore.get().all()) {
            ChatFormatting color = map.isPlayable() ? ChatFormatting.GREEN : ChatFormatting.GOLD;
            String status = map.isPlayable() ? "prete" : "incomplete";
            ctx.getSource().sendSuccess(() -> Component.literal("- " + map.name + " (" + status + ", "
                    + map.corners.size() + "/2 bornes, " + map.spawns.size() + " spawns)").withStyle(color), false);
        }
        return ArenaStore.get().all().size();
    }

    private static int mapCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (ArenaStore.get().get(name) != null) {
            fail(ctx, "La map " + name + " existe deja. Utilise /melee map edit " + name + ".");
            return 0;
        }
        ArenaStore.get().create(name);
        ArenaStore.get().setEditing(player.getUUID(), name);
        ok(ctx, "Map " + name + " creee et en edition. Pose 2 bornes de zone a deux coins opposes, puis des points d'apparition.");
        return 1;
    }

    private static int mapEdit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ArenaMap map = findMap(ctx, StringArgumentType.getString(ctx, "name"));
        if (map == null) return 0;
        ArenaStore.get().setEditing(ctx.getSource().getPlayerOrException().getUUID(), map.name);
        ok(ctx, "Tu edites " + map.name + ". Les bornes et spawns que tu poses y seront rattaches.");
        return 1;
    }

    private static int mapDelete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        GameManager manager = GameManager.get();
        if (manager.isRunning() && manager.current().map().name.equals(name)) {
            fail(ctx, "Une partie est en cours sur cette map.");
            return 0;
        }
        if (!ArenaStore.get().delete(name)) {
            fail(ctx, "Map inconnue : " + name);
            return 0;
        }
        ok(ctx, "Map " + name + " supprimee. Les blocs poses restent en place, casse-les en creatif si besoin.");
        return 1;
    }

    private static int mapInfo(CommandContext<CommandSourceStack> ctx) {
        ArenaMap map = findMap(ctx, StringArgumentType.getString(ctx, "name"));
        if (map == null) return 0;
        StringBuilder sb = new StringBuilder("Map ").append(map.name).append(" (").append(map.dimension).append(")\n");
        for (int i = 0; i < map.corners.size(); i++) {
            sb.append("  Borne ").append(i + 1).append(" : ").append(map.corners.get(i)).append('\n');
        }
        AABB box = map.bounds();
        if (box != null) {
            sb.append(String.format("  Volume : %d x %d x %d%n", (int) box.getXsize(), (int) box.getYsize(), (int) box.getZsize()));
        }
        for (int i = 0; i < map.spawns.size(); i++) {
            sb.append("  Spawn ").append(i + 1).append(" : ").append(map.spawns.get(i).pos).append('\n');
        }
        sb.append(map.isPlayable() ? "  Prete a jouer." : "  Incomplete : il faut 2 bornes et au moins 1 spawn.");
        String text = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    // ---------------------------------------------------------------- regles

    private static int rulesShow(CommandContext<CommandSourceStack> ctx) {
        CombatRules rules = CombatRules.get();
        for (Map.Entry<String, CombatRules.Rule> e : CombatRules.RULES.entrySet()) {
            int value = e.getValue().getter().apply(rules);
            ctx.getSource().sendSuccess(() -> Component.literal(e.getKey() + " = " + value + "  (" + e.getValue().description() + ")"), false);
        }
        return 1;
    }

    private static int rulesSet(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "rule");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        CombatRules.Rule rule = CombatRules.RULES.get(name);
        if (rule == null) {
            fail(ctx, "Regle inconnue : " + name + ". Regles : " + String.join(", ", CombatRules.RULES.keySet()));
            return 0;
        }
        rule.setter().accept(CombatRules.get(), value);
        CombatRules.save();
        String note = GameManager.get().isRunning() ? " (s'applique des la prochaine partie)" : "";
        ok(ctx, name + " = " + value + note);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        if (GameManager.get().isRunning()) {
            fail(ctx, "Impossible de recharger pendant une partie.");
            return 0;
        }
        CombatRules.load();
        ArenaStore.get().load(ctx.getSource().getServer());
        ok(ctx, "Regles et maps rechargees.");
        return 1;
    }

    // ---------------------------------------------------------------- utilitaires

    private static ArenaMap findMap(CommandContext<CommandSourceStack> ctx, String name) {
        ArenaMap map = ArenaStore.get().get(name);
        if (map == null) fail(ctx, "Map inconnue : " + name);
        return map;
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    private static void fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
    }
}
