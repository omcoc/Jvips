package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;

public final class VipsRemoveCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final RequiredArg<PlayerRef> playerArg;
    private final RequiredArg<String> vipIdArg;

    public VipsRemoveCommand(JvipsPlugin plugin) {
        super("remove", "Remove um VIP de um jogador (admin).");
        this.plugin = plugin;

        requirePermission("jvips.admin");

        // ordem do seu comando: /vips remove <player> <vipId|*>
        this.playerArg = withRequiredArg("player", "Jogador alvo (online)", ArgTypes.PLAYER_REF);
        this.vipIdArg = withRequiredArg("vipId", "Id do VIP (ex: thorium) ou *", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        PlayerRef targetRef = playerArg.get(ctx);
        String vipId = vipIdArg.get(ctx);

        if (targetRef == null) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.playerMustBeOnline", null)));
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        String targetUuidStr = targetUuid.toString();

        VoucherService svc = plugin.getCore().getVoucherService();

        // pega a definição ANTES de remover, para rodar commandsOnExpire corretamente
        VipDefinition defToExpire = svc.peekActiveVipDefinition(targetUuidStr, vipId);

        boolean removed = svc.adminRemoveVip(vipId, targetUuidStr);
        if (!removed) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("admin.remove.none", null)));
            return;
        }

        // Gravar remoção no history.json
        if (defToExpire != null) {
            br.com.julio.jvips.plugin.util.HistoryRecorder.recordAdminRemoval(
                    targetUuidStr, defToExpire.getId(), java.time.Instant.now().getEpochSecond()
            );
        }

        if (defToExpire == null) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("admin.remove.missingDef", null)));
            return;
        }

        String playerToken = targetRef.getUsername();
        plugin.getCore().getCommandService()
                .runExpireCommands(defToExpire, playerToken)
                .thenRun(() -> {
                    java.util.Map<String, String> adminVars = new java.util.HashMap<>();
                    adminVars.put("vipId", defToExpire.getId());
                    adminVars.put("player", targetRef.getUsername());
                    ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("admin.remove.okExecuted", adminVars)));

                    targetRef.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("player.vipRemoved", null)));
                });
    }
}
