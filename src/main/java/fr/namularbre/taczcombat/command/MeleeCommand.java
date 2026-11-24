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
import fr.namularbre.taczcombat.loadout.BanList;
import fr.namularbre.taczcombat.loadout.ClassSelectMenu;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.world.InteractionHand;
import java.util.ArrayList;
import java.util.List;
import fr.namularbre.taczcombat.loadout.ClassStore;
import fr.namularbre.taczcombat.loadout.CombatClass;
import net.minecraft.world.item.ItemStack;
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
 * /melee vote [<map>]                               (tous : menu ou vote direct)
 * /melee start [<map>] | stop                        (admin : sans map = vote, avec map = choix admin)
 * /melee map create|edit|delete|info <nom> | list    (admin)
 * /melee class [pick <classe>]                        (tous : menu ou choix direct)
 * /melee class save|delete|give <classe> | list     (admin)
 * /melee ban add|remove <id> | hand | list            (admin)
 * /melee rules [set <regle> <valeur>]                (admin)
 * /melee reload                                      (admin)
 */
public final class MeleeCommand {
    private static final int ADMIN = 2;

    private static final SuggestionProvider<CommandSourceStack> MAP_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ArenaStore.get().all().stream().map(m -> m.name), builder);
    private static final SuggestionProvider<CommandSourceStack> VOTE_OPTIONS = (ctx, builder) -> {
        var vote = GameManager.get().currentVote();
        return SharedSuggestionProvider.suggest(vote == null ? List.<String>of() : vote.options(), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> CLASS_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ClassStore.get().all().stream().map(c -> c.name), builder);
    private static final SuggestionProvider<CommandSourceStack> BANNED_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(BanList.get().all(), builder);
    private static final SuggestionProvider<CommandSourceStack> RULE_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CombatRules.RULES.keySet(), builder);

    private MeleeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("melee")
                .then(Commands.literal("join").executes(MeleeCommand::join))
                .then(Commands.literal("leave").executes(MeleeCommand::leave))
                .then(Commands.literal("vote")
                        .executes(MeleeCommand::voteMenu)
                        .then(Commands.argument("map", StringArgumentType.word()).suggests(VOTE_OPTIONS).executes(MeleeCommand::voteFor)))
                .then(Commands.literal("start").requires(s -> s.hasPermission(ADMIN))
                        .executes(MeleeCommand::startVote)
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
                .then(Commands.literal("class")
                        .executes(MeleeCommand::classMenu)
                        .then(Commands.literal("pick")
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(CLASS_NAMES).executes(MeleeCommand::classPick)))
                        .then(Commands.literal("list").executes(MeleeCommand::classList))
                        .then(Commands.literal("save").requires(s -> s.hasPermission(ADMIN))
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(CLASS_NAMES).executes(MeleeCommand::classSave)))
                        .then(Commands.literal("delete").requires(s -> s.hasPermission(ADMIN))
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(CLASS_NAMES).executes(MeleeCommand::classDelete)))
                        .then(Commands.literal("give").requires(s -> s.hasPermission(ADMIN))
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(CLASS_NAMES).executes(MeleeCommand::classGive))))
                .then(Commands.literal("ban").requires(s -> s.hasPermission(ADMIN))
                        .then(Commands.literal("list").executes(MeleeCommand::banList))
                        .then(Commands.literal("hand").executes(MeleeCommand::banHand))
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(MeleeCommand::banAdd)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(BANNED_IDS).executes(MeleeCommand::banRemove))))
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
            ok(ctx, "Partie ou vote arrete. Pas de vote automatique : relance avec /melee start.");
            return 1;
        }
        fail(ctx, "Aucune partie ni vote en cours.");
        return 0;
    }

    private static int startVote(CommandContext<CommandSourceStack> ctx) {
        String error = GameManager.get().startVote(ctx.getSource().getServer());
        if (error != null) {
            fail(ctx, error);
            return 0;
        }
        ok(ctx, "Vote lance (" + CombatRules.get().voteSeconds + " s).");
        return 1;
    }

    private static int voteMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameManager.get().openVoteMenu(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int voteFor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameManager.get().vote(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "map"));
        return 1;
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

    // ---------------------------------------------------------------- classes

    private static int classMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClassSelectMenu.open(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int classPick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CombatClass c = findClass(ctx, StringArgumentType.getString(ctx, "name"));
        if (c == null) return 0;
        GameManager.get().chooseClass(ctx.getSource().getPlayerOrException(), c.name);
        return 1;
    }

    private static int classList(CommandContext<CommandSourceStack> ctx) {
        if (ClassStore.get().isEmpty()) {
            ok(ctx, "Aucune classe. Un admin en cree une avec /melee class save <nom>.");
            return 0;
        }
        for (CombatClass c : ClassStore.get().all()) {
            StringBuilder sb = new StringBuilder("- ").append(c.name).append(" : ");
            boolean firstItem = true;
            for (ItemStack stack : c.stacks()) {
                if (!firstItem) sb.append(", ");
                firstItem = false;
                if (stack.getCount() > 1) sb.append(stack.getCount()).append(" x ");
                sb.append(stack.getHoverName().getString());
            }
            String line = sb.toString();
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return ClassStore.get().all().size();
    }

    private static int classSave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.get().isRunning() && GameManager.get().current().isParticipant(player)) {
            fail(ctx, "Impossible pendant une partie : ton inventaire est celui de ta classe.");
            return 0;
        }
        List<String> bannedItems = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (BanList.get().isBanned(stack)) bannedItems.add(BanList.get().bannedIdIn(stack));
        }
        if (!bannedItems.isEmpty()) {
            fail(ctx, "Ton inventaire contient des objets interdits : " + String.join(", ", bannedItems)
                    + ". Retire-les ou utilise /melee ban remove.");
            return 0;
        }
        CombatClass c = CombatClass.fromInventory(name, player.getInventory());
        if (c.items.isEmpty()) {
            fail(ctx, "Ton inventaire est vide. Equipe d'abord les armes, munitions et armure de la classe.");
            return 0;
        }
        boolean existed = ClassStore.get().get(name) != null;
        ClassStore.get().put(c);
        ok(ctx, "Classe " + name + (existed ? " mise a jour" : " creee") + " depuis ton inventaire (" + c.items.size() + " emplacements).");
        return 1;
    }

    private static int classDelete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ClassStore.get().delete(name)) {
            fail(ctx, "Classe inconnue : " + name);
            return 0;
        }
        ok(ctx, "Classe " + name + " supprimee.");
        return 1;
    }

    private static int classGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CombatClass c = findClass(ctx, StringArgumentType.getString(ctx, "name"));
        if (c == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.get().isRunning() && GameManager.get().current().isParticipant(player)) {
            fail(ctx, "Impossible pendant une partie.");
            return 0;
        }
        for (ItemStack stack : c.stacks()) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        player.inventoryMenu.broadcastChanges();
        ok(ctx, "Objets de la classe " + c.name + " ajoutes a ton inventaire.");
        return 1;
    }

    private static CombatClass findClass(CommandContext<CommandSourceStack> ctx, String name) {
        CombatClass c = ClassStore.get().get(name);
        if (c == null) fail(ctx, "Classe inconnue : " + name);
        return c;
    }

    // ---------------------------------------------------------------- bannissements

    private static int banList(CommandContext<CommandSourceStack> ctx) {
        if (BanList.get().all().isEmpty()) {
            ok(ctx, "Rien n'est interdit.");
            return 0;
        }
        ok(ctx, "Interdits : " + String.join(", ", BanList.get().all()));
        return BanList.get().all().size();
    }

    private static int banHand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = ctx.getSource().getPlayerOrException().getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.isEmpty()) {
            fail(ctx, "Tiens en main l'arme, l'accessoire ou l'objet a interdire.");
            return 0;
        }
        return ban(ctx, BanList.idOf(stack));
    }

    private static int banAdd(CommandContext<CommandSourceStack> ctx) {
        return ban(ctx, ResourceLocationArgument.getId(ctx, "id").toString());
    }

    private static int ban(CommandContext<CommandSourceStack> ctx, String id) {
        if (!BanList.get().add(id)) {
            fail(ctx, id + " est deja interdit.");
            return 0;
        }
        long affected = ClassStore.get().all().stream()
                .filter(c -> c.stacks().stream().anyMatch(BanList.get()::isBanned)).count();
        String note = affected > 0 ? " " + affected + " classe(s) le contiennent : il leur sera retire en partie." : "";
        ok(ctx, id + " est interdit." + note);
        return 1;
    }

    private static int banRemove(CommandContext<CommandSourceStack> ctx) {
        String id = ResourceLocationArgument.getId(ctx, "id").toString();
        if (!BanList.get().remove(id)) {
            fail(ctx, id + " n'etait pas interdit.");
            return 0;
        }
        ok(ctx, id + " est de nouveau autorise.");
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
        ClassStore.get().load();
        BanList.get().load();
        ArenaStore.get().load(ctx.getSource().getServer());
        ok(ctx, "Regles, classes, interdits et maps recharges.");
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
