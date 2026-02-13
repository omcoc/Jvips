package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.VipExpiryService;
import br.com.julio.jvips.core.model.PlayerVipState;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.text.JvipsTextParser;
import br.com.julio.jvips.core.util.DurationFormatter;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * /vip status (e /vip)
 */
public final class VipStatusCommand extends CommandBase {

    private final JvipsPlugin plugin;

    public VipStatusCommand(JvipsPlugin plugin) {
        super("status", "Shows your current VIP status.");
        this.plugin = plugin;
        // Use JVips permissions (LuckPerms/etc) instead of auto-generated jplugins.* command nodes.
        requirePermission("jvips.use");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.playerMustBeOnline", null)
            ));
            return;
        }

        // senderAsPlayerRef() retorna Ref<EntityStore>, não PlayerRef
        Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();

        if (playerRef == null) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.playerMustBeOnline", null)
            ));
            return;
        }

        // Para obter o UUID, use o PlayerRef via Universe
        Player player = ctx.senderAs(Player.class);
        String uuid = Objects.requireNonNull(player.getUuid()).toString();
        long now = Instant.now().getEpochSecond();

        VipExpiryService expiry = plugin.getCore().getExpiryService();
        PlayerVipState state = (expiry != null) ? expiry.getPlayerState(uuid) : null;

        if (state == null || !state.hasActiveVip(now)) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("player.vipStatus.none", null)
            ));
            return;
        }

        String vipId = state.getActiveVipId();
        VipDefinition vipDef = null;
        try {
            vipDef = plugin.getCore().getConfig().getVipOrThrow(vipId);
        } catch (Exception ignored) {
        }

        String vipDisplay = (vipDef != null && vipDef.getDisplayName() != null && !vipDef.getDisplayName().isEmpty())
                ? vipDef.getDisplayName()
                : vipId;

        long remaining = Math.max(0, state.getExpiresAt() - now);

        Map<String, String> vars = new HashMap<>();
        vars.put("vipId", vipId);
        vars.put("vipDisplay", vipDisplay);
        vars.put("remaining", DurationFormatter.format(remaining));

        ctx.sendMessage(JvipsTextParser.parseToMessage(
                plugin.getMessages().format("player.vipStatus.active", vars)
        ));
    }
}
