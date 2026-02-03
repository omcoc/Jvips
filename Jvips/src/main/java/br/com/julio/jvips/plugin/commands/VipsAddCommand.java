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

public final class VipsAddCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final RequiredArg<PlayerRef> playerArg;
    private final RequiredArg<String> vipIdArg;

    public VipsAddCommand(JvipsPlugin plugin) {
        super("add", "Ativa um VIP para um jogador (admin).");
        this.plugin = plugin;

        requirePermission("jvips.admin");

        // ordem do seu comando: /vips add <player> <vipId>
        this.playerArg = withRequiredArg("player", "Jogador alvo (online)", ArgTypes.PLAYER_REF);
        this.vipIdArg = withRequiredArg("vipId", "Id do VIP (ex: thorium)", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        PlayerRef targetRef = playerArg.get(ctx);
        String vipId = vipIdArg.get(ctx);

        if (targetRef == null) {
            ctx.sendMessage(Message.raw("Jogador inválido ou offline."));
            return;
        }

        // valida VIP existe
        VipDefinition vipDef;
        try {
            vipDef = plugin.getCore().getConfig().getVipOrThrow(vipId);
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("VIP inválido: " + vipId));
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        String targetUuidStr = targetUuid.toString();

        VoucherService svc = plugin.getCore().getVoucherService();
        //VoucherService.ActivationResult ar = svc.adminAddVip(vipId, targetUuidStr);

        VoucherService.ActivationResult ar =
                svc.adminAddVip(vipId, targetUuidStr, targetRef.getUsername());


        if (ar.blockedByExistingVip()) {
            ctx.sendMessage(Message.raw("Esse jogador já tem VIP ativo. Remova antes ou aguarde expirar."));
            return;
        }
        if (!ar.activated()) {
            ctx.sendMessage(Message.raw("Não foi possível ativar VIP agora."));
            return;
        }

        // execute commandsOnActivate em sequência (seu VipCommandService já está sequencial)
        String playerToken = targetRef.getUsername(); // para say ficar bonito e LP aceitar nome
        plugin.getCore().getCommandService()
                .runActivateCommands(vipDef, playerToken)
                .thenRun(() -> {
                    ctx.sendMessage(Message.raw("VIP '" + vipId + "' ativado para " + targetRef.getUsername() + "."));
                    targetRef.sendMessage(Message.raw("Seu VIP foi ativado: " + vipId));
                });
    }
}
