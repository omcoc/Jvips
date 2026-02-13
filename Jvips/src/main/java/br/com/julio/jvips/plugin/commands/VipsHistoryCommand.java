package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.VipExpiryService;
import br.com.julio.jvips.core.config.PluginConfig;
import br.com.julio.jvips.core.model.PlayerVipState;
import br.com.julio.jvips.core.model.VipHistoryEntry;
import br.com.julio.jvips.core.storage.HistoryStore;
import br.com.julio.jvips.core.text.JvipsTextParser;
import br.com.julio.jvips.core.util.DateTimeFormatUtil;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class VipsHistoryCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final OptionalArg<String> playerArg;
    private final OptionalArg<Integer> pageArg;

    public VipsHistoryCommand(JvipsPlugin plugin) {
        super("history", "Shows VIP history (yours or another player's).");
        this.plugin = plugin;

        this.playerArg = withOptionalArg("player", "Player name (requires jvips.admin)", ArgTypes.STRING);
        this.pageArg = withOptionalArg("page", "Page number", ArgTypes.INTEGER);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        String uuid;
        String playerName;
        CommandSender sender = ctx.sender();

        if (playerArg.provided(ctx)) {
            if (!sender.hasPermission("jvips.admin")) {
                Map<String, String> vars = new HashMap<>();
                vars.put("perm", "jvips.admin");
                ctx.sendMessage(JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.noPermissionAdmin", vars)
                ));
                return;
            }

            String targetName = playerArg.get(ctx);

            // Resolver UUID pelo nome via players.json (lastKnownName)
            String[] resolved = resolvePlayerByName(targetName);
            if (resolved == null) {
                Map<String, String> vars = new HashMap<>();
                vars.put("player", targetName);
                ctx.sendMessage(JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("admin.history.empty", vars)
                ));
                return;
            }

            uuid = resolved[0];
            playerName = resolved[1];
        } else {
            if (!ctx.isPlayer()) {
                ctx.sendMessage(JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.playerMustBeOnline", null)
                ));
                return;
            }

            if (!sender.hasPermission("jvips.use")) {
                Map<String, String> vars = new HashMap<>();
                vars.put("perm", "jvips.use");
                ctx.sendMessage(JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.noPermissionUse", vars)
                ));
                return;
            }

            Player player = ctx.senderAs(Player.class);
            uuid = Objects.requireNonNull(player.getUuid()).toString();
            playerName = player.getDisplayName();
        }

        showHistory(ctx, uuid, playerName);
    }

    /**
     * Resolve jogador por nome buscando no players.json (lastKnownName).
     * Retorna [uuid, displayName] ou null.
     */
    private String[] resolvePlayerByName(String name) {
        if (name == null || name.isEmpty()) return null;

        VipExpiryService expiry = plugin.getCore().getExpiryService();
        if (expiry == null) return null;

        Map<String, PlayerVipState> allPlayers = expiry.getAllPlayers();

        // Buscar pelo lastKnownName
        for (Map.Entry<String, PlayerVipState> entry : allPlayers.entrySet()) {
            PlayerVipState state = entry.getValue();
            if (state != null && name.equalsIgnoreCase(state.getLastKnownName())) {
                String displayName = state.getLastKnownName() != null ? state.getLastKnownName() : name;
                return new String[]{ entry.getKey(), displayName };
            }
        }

        // Tenta como UUID direto
        try {
            UUID u = UUID.fromString(name);
            String uuidStr = u.toString();
            PlayerVipState state = allPlayers.get(uuidStr);
            String displayName = (state != null && state.getLastKnownName() != null)
                    ? state.getLastKnownName() : name;
            return new String[]{ uuidStr, displayName };
        } catch (Exception ignored) {}

        // Também buscar no history.json (pode ter jogador sem VIP ativo mas com histórico)
        HistoryStore historyStore = plugin.getCore().getHistoryStore();
        if (historyStore != null) {
            Map<String, List<VipHistoryEntry>> allHistory = historyStore.load();
            for (Map.Entry<String, List<VipHistoryEntry>> entry : allHistory.entrySet()) {
                List<VipHistoryEntry> list = entry.getValue();
                if (list != null && !list.isEmpty()) {
                    // Verifica displayName de qualquer entrada
                    for (VipHistoryEntry he : list) {
                        if (he.getVipDisplayName() != null && name.equalsIgnoreCase(he.getVipDisplayName())) {
                            // Não é o nome do jogador... pula
                            break;
                        }
                    }
                }
            }
        }

        return null;
    }

    private void showHistory(CommandContext ctx, String uuid, String playerName) {
        HistoryStore historyStore = plugin.getCore().getHistoryStore();
        if (historyStore == null) {
            Map<String, String> vars = new HashMap<>();
            vars.put("player", playerName);
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.history.empty", vars)
            ));
            return;
        }

        List<VipHistoryEntry> entries = historyStore.getHistory(uuid);

        if (entries == null || entries.isEmpty()) {
            Map<String, String> vars = new HashMap<>();
            vars.put("player", playerName);
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.history.empty", vars)
            ));
            return;
        }

        PluginConfig.Formatting fmt = plugin.getCore().getPluginConfig().getFormatting();
        String dateFmt = fmt.getDateFormat();
        String hourFmt = fmt.getHourFormat();
        String tz = fmt.getTimezone();

        PluginConfig.ListSettings ls = plugin.getCore().getPluginConfig().getListSettings();
        int perPage = ls.getEntriesPerPage();
        int totalPages = (int) Math.ceil((double) entries.size() / perPage);

        int page = 1;
        if (pageArg.provided(ctx)) {
            Integer p = pageArg.get(ctx);
            if (p != null && p >= 1) page = p;
        }
        if (page > totalPages) page = totalPages;

        int startIdx = (page - 1) * perPage;
        int endIdx = Math.min(startIdx + perPage, entries.size());

        Map<String, String> headerVars = new HashMap<>();
        headerVars.put("player", playerName);
        headerVars.put("page", String.valueOf(page));
        headerVars.put("totalPages", String.valueOf(totalPages));
        headerVars.put("total", String.valueOf(entries.size()));
        ctx.sendMessage(JvipsTextParser.parseToMessage(
                plugin.getMessages().format("admin.history.header", headerVars)
        ));

        for (int i = startIdx; i < endIdx; i++) {
            VipHistoryEntry hl = entries.get(i);

            String vipDisplay = (hl.getVipDisplayName() != null && !hl.getVipDisplayName().isEmpty())
                    ? hl.getVipDisplayName() : hl.getVipId();

            String startFormatted = DateTimeFormatUtil.format(hl.getActivatedAt(), dateFmt, hourFmt, tz);
            String endFormatted;
            String reason;

            if (hl.getEndedAt() == 0) {
                endFormatted = plugin.getMessages().format("admin.history.stillActive", null);
                reason = "active";
            } else {
                endFormatted = DateTimeFormatUtil.format(hl.getEndedAt(), dateFmt, hourFmt, tz);
                reason = (hl.getEndReason() != null) ? hl.getEndReason() : "expired";
            }

            Map<String, String> vars = new HashMap<>();
            vars.put("vipDisplay", vipDisplay);
            vars.put("start", startFormatted);
            vars.put("end", endFormatted);
            vars.put("reason", reason);

            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.history.entry", vars)
            ));
        }

        if (totalPages > 1) {
            Map<String, String> footerVars = new HashMap<>();
            footerVars.put("page", String.valueOf(page));
            footerVars.put("totalPages", String.valueOf(totalPages));
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.history.footer", footerVars)
            ));
        }
    }
}
