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
            ctx.sendMessage(Message.raw("Jogador inválido ou offline."));
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        String targetUuidStr = targetUuid.toString();

        VoucherService svc = plugin.getCore().getVoucherService();

        // pega a definição ANTES de remover, para rodar commandsOnExpire corretamente
        VipDefinition defToExpire = svc.peekActiveVipDefinition(targetUuidStr, vipId);


        boolean removed = svc.adminRemoveVip(vipId, targetUuidStr);
        if (!removed) {
            ctx.sendMessage(Message.raw("Nada para remover: jogador sem VIP ativo ou vipId não confere."));
            return;
        }

        if (defToExpire == null) {
            ctx.sendMessage(Message.raw("VIP removido, mas não encontrei definição do VIP para executar commandsOnExpire."));
            return;
        }

        String playerToken = targetRef.getUsername();
        plugin.getCore().getCommandService()
                .runExpireCommands(defToExpire, playerToken)
                .thenRun(() -> {
                    ctx.sendMessage(Message.raw("VIP removido de " + targetRef.getUsername() + " (commandsOnExpire executados)."));
                    targetRef.sendMessage(Message.raw("Seu VIP foi removido por um administrador."));
                });
    }
}
