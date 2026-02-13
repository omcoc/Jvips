package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.VipExpiryService;
import br.com.julio.jvips.core.config.PluginConfig;
import br.com.julio.jvips.core.model.PlayerVipState;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.text.JvipsTextParser;
import br.com.julio.jvips.core.util.DurationFormatter;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VipsListCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final OptionalArg<String> playerArg;
    private final OptionalArg<Integer> pageArg;

    public VipsListCommand(JvipsPlugin plugin) {
        super("list", "Lists all active VIPs or a specific player's VIP.");
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
        long now = Instant.now().getEpochSecond();
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
            showPlayerVipByName(ctx, targetName, now);
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

        showAllActiveVips(ctx, now);
    }

    /**
     * Resolve jogador por nome buscando no players.json (lastKnownName).
     * Não depende de getOnlinePlayers().
     */
    private void showPlayerVipByName(CommandContext ctx, String targetName, long now) {
        if (targetName == null || targetName.isEmpty()) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.playerMustBeOnline", null)
            ));
            return;
        }

        VipExpiryService expiry = plugin.getCore().getExpiryService();
        if (expiry == null) {
            Map<String, String> vars = new HashMap<>();
            vars.put("player", targetName);
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.list.playerNoVip", vars)
            ));
            return;
        }

        // Buscar no players.json pelo lastKnownName
        Map<String, PlayerVipState> allPlayers = expiry.getAllPlayers();
        String foundUuid = null;
        PlayerVipState foundState = null;

        for (Map.Entry<String, PlayerVipState> entry : allPlayers.entrySet()) {
            PlayerVipState state = entry.getValue();
            if (state != null && targetName.equalsIgnoreCase(state.getLastKnownName())) {
                foundUuid = entry.getKey();
                foundState = state;
                break;
            }
        }

        // Também tenta interpretar como UUID direto
        if (foundUuid == null) {
            try {
                UUID uuid = UUID.fromString(targetName);
                foundUuid = uuid.toString();
                foundState = allPlayers.get(foundUuid);
            } catch (Exception ignored) {}
        }

        if (foundState == null || !foundState.hasActiveVip(now)) {
            Map<String, String> vars = new HashMap<>();
            vars.put("player", targetName);
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.list.playerNoVip", vars)
            ));
            return;
        }

        String displayName = foundState.getLastKnownName() != null ? foundState.getLastKnownName() : targetName;
        String vipDisplay = resolveVipDisplay(foundState.getActiveVipId());
        long remaining = Math.max(0, foundState.getExpiresAt() - now);

        Map<String, String> headerVars = new HashMap<>();
        headerVars.put("player", displayName);
        ctx.sendMessage(JvipsTextParser.parseToMessage(
                plugin.getMessages().format("admin.list.playerHeader", headerVars)
        ));

        Map<String, String> vars = new HashMap<>();
        vars.put("player", displayName);
        vars.put("vipDisplay", vipDisplay);
        vars.put("remaining", DurationFormatter.format(remaining));
        ctx.sendMessage(JvipsTextParser.parseToMessage(
                plugin.getMessages().format("admin.list.entry", vars)
        ));
    }

    private void showAllActiveVips(CommandContext ctx, long now) {
        VipExpiryService expiry = plugin.getCore().getExpiryService();
        if (expiry == null) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.list.empty", null)
            ));
            return;
        }

        Map<String, PlayerVipState> allPlayers = expiry.getAllPlayers();

        List<ActiveEntry> activeEntries = new ArrayList<>();
        for (Map.Entry<String, PlayerVipState> entry : allPlayers.entrySet()) {
            PlayerVipState state = entry.getValue();
            if (state != null && state.hasActiveVip(now)) {
                activeEntries.add(new ActiveEntry(
                        state.getLastKnownName() != null ? state.getLastKnownName() : entry.getKey(),
                        state.getActiveVipId(),
                        Math.max(0, state.getExpiresAt() - now)
                ));
            }
        }

        if (activeEntries.isEmpty()) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.list.empty", null)
            ));
            return;
        }

        PluginConfig.ListSettings ls = plugin.getCore().getPluginConfig().getListSettings();
        int perPage = ls.getEntriesPerPage();
        int totalPages = (int) Math.ceil((double) activeEntries.size() / perPage);

        int page = 1;
        if (pageArg.provided(ctx)) {
            Integer p = pageArg.get(ctx);
            if (p != null && p >= 1) page = p;
        }
        if (page > totalPages) page = totalPages;

        int startIdx = (page - 1) * perPage;
        int endIdx = Math.min(startIdx + perPage, activeEntries.size());

        Map<String, String> headerVars = new HashMap<>();
        headerVars.put("page", String.valueOf(page));
        headerVars.put("totalPages", String.valueOf(totalPages));
        headerVars.put("total", String.valueOf(activeEntries.size()));
        ctx.sendMessage(JvipsTextParser.parseToMessage(
                plugin.getMessages().format("admin.list.header", headerVars)
        ));

        for (int i = startIdx; i < endIdx; i++) {
            ActiveEntry ae = activeEntries.get(i);
            String vipDisplay = resolveVipDisplay(ae.vipId);

            Map<String, String> vars = new HashMap<>();
            vars.put("player", ae.playerName);
            vars.put("vipDisplay", vipDisplay);
            vars.put("remaining", DurationFormatter.format(ae.remainingSeconds));

            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.list.entry", vars)
            ));
        }

        if (totalPages > 1) {
            Map<String, String> footerVars = new HashMap<>();
            footerVars.put("page", String.valueOf(page));
            footerVars.put("totalPages", String.valueOf(totalPages));
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.list.footer", footerVars)
            ));
        }
    }

    private String resolveVipDisplay(String vipId) {
        try {
            VipDefinition vipDef = plugin.getCore().getConfig().getVipOrThrow(vipId);
            if (vipDef.getDisplayName() != null && !vipDef.getDisplayName().isEmpty()) {
                return vipDef.getDisplayName();
            }
        } catch (Exception ignored) {}
        return vipId;
    }

    private record ActiveEntry(String playerName, String vipId, long remainingSeconds) {}
}
