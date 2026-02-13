package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.util.DurationParser;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;

/**
 * /vips add <player> <vip> [duration]
 *
 * Exemplos:
 *   /vips add __om__ thorium          → duração padrão do vips.json
 *   /vips add __om__ thorium 1d2h10m  → 1 dia, 2 horas, 10 minutos
 */
public final class VipsAddCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final RequiredArg<PlayerRef> playerArg;
    private final RequiredArg<String> vipIdArg;
    private final OptionalArg<String> durationArg;

    public VipsAddCommand(JvipsPlugin plugin) {
        super("add", "Activates a VIP for a player (admin). Optional: custom duration (e.g. 1d2h10m5s).");
        this.plugin = plugin;

        requirePermission("jvips.admin");

        this.playerArg = withRequiredArg("player", "Jogador alvo (online)", ArgTypes.PLAYER_REF);
        this.vipIdArg = withRequiredArg("vipId", "Id do VIP (ex: thorium)", ArgTypes.STRING);
        this.durationArg = withOptionalArg("duration", "Custom duration (ex: 1d2h10m5s, 30d, 2h30m)", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        PlayerRef targetRef = playerArg.get(ctx);
        String vipId = vipIdArg.get(ctx);

        if (targetRef == null) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.playerMustBeOnline", null)));
            return;
        }

        VipDefinition vipDef;
        try {
            vipDef = plugin.getCore().getConfig().getVipOrThrow(vipId);
        } catch (Exception e) {
            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("vipId", vipId);
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.invalidVip", vars)));
            return;
        }

        // ===== Parse de tempo custom (--duration 1d2h10m5s) =====
        long customDurationSeconds = 0;
        if (durationArg.provided(ctx)) {
            String durationInput = durationArg.get(ctx);
            if (durationInput != null && !durationInput.trim().isEmpty()) {
                long parsed = DurationParser.parse(durationInput.trim());
                if (parsed > 0) {
                    customDurationSeconds = parsed;
                } else {
                    java.util.Map<String, String> vars = new java.util.HashMap<>();
                    vars.put("input", durationInput);
                    ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                            plugin.getMessages().format("error.invalidDuration", vars)));
                    return;
                }
            }
        }

        UUID targetUuid = targetRef.getUuid();
        String targetUuidStr = targetUuid.toString();

        VoucherService svc = plugin.getCore().getVoucherService();
        VoucherService.ActivationResult ar =
                svc.adminAddVip(vipId, targetUuidStr, targetRef.getUsername(), customDurationSeconds);

        if (ar.blockedByStackLimit()) {
            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("maxStack", String.valueOf(ar.maxStack()));
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.stackLimitReached", vars)));
            return;
        }

        if (ar.blockedByExistingVip()) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.alreadyHasVip", null)));
            return;
        }
        if (!ar.activated()) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.activationFailed", null)));
            return;
        }

        // Gravar no history.json (só para ativação inicial, não stack)
        if (ar.newState() != null && !ar.stacked()) {
            br.com.julio.jvips.plugin.util.HistoryRecorder.recordActivation(
                    targetUuidStr, vipDef,
                    ar.newState().getActivatedAt(), ar.newState().getExpiresAt()
            );
        }

        String playerToken = targetRef.getUsername();
        String vipDisplay = (vipDef.getDisplayName() != null && !vipDef.getDisplayName().isEmpty())
                ? vipDef.getDisplayName()
                : vipId;

        long effectiveDuration = customDurationSeconds > 0 ? customDurationSeconds : vipDef.getDurationSeconds();

        if (ar.stacked()) {
            // Mensagem de stack para admin
            java.util.Map<String, String> adminVars = new java.util.HashMap<>();
            adminVars.put("vipId", vipId);
            adminVars.put("player", playerToken);
            adminVars.put("addedDuration", br.com.julio.jvips.core.util.DurationFormatter.format(ar.addedDuration()));
            adminVars.put("totalRemaining", br.com.julio.jvips.core.util.DurationFormatter.format(
                    ar.newState().getExpiresAt() - java.time.Instant.now().getEpochSecond()));
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.add.stacked", adminVars)));

            // Mensagem para o jogador
            java.util.Map<String, String> playerVars = new java.util.HashMap<>();
            playerVars.put("vipDisplay", vipDisplay);
            playerVars.put("addedDuration", br.com.julio.jvips.core.util.DurationFormatter.format(ar.addedDuration()));
            playerVars.put("totalRemaining", br.com.julio.jvips.core.util.DurationFormatter.format(
                    ar.newState().getExpiresAt() - java.time.Instant.now().getEpochSecond()));
            playerVars.put("stackCount", String.valueOf(ar.newState().getStackCount()));
            targetRef.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("player.vipStacked", playerVars)));
        } else {
            // Ativação normal — executar commandsOnActivate
            plugin.getCore().getCommandService()
                    .runActivateCommands(vipDef, playerToken)
                    .thenRun(() -> {
                        java.util.Map<String, String> adminVars = new java.util.HashMap<>();
                        adminVars.put("vipId", vipId);
                        adminVars.put("player", targetRef.getUsername());
                        adminVars.put("duration", br.com.julio.jvips.core.util.DurationFormatter.format(effectiveDuration));
                        ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("admin.add.ok", adminVars)));

                        java.util.Map<String, String> playerVars = new java.util.HashMap<>();
                        playerVars.put("vipDisplay", vipDisplay);
                        targetRef.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("player.vipActivated", playerVars)));
                    });
        }
    }
}
