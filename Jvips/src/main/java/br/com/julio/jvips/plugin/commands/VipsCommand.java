package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;

import java.util.concurrent.CompletableFuture;

public final class VipsCommand extends AbstractCommand {

    private final JvipsPlugin plugin;

    public VipsCommand(JvipsPlugin plugin) {
        super("vips", "Gerenciamento de VIPs (Jvips)");
        this.plugin = plugin;

        // NÃO exigir permissão no pai: cada subcomando controla sua própria permissão.
        // Isso permite que jogadores com jvips.use acessem /vips list e /vips history,
        // enquanto /vips add, /vips remove etc. exigem jvips.admin individualmente.

        // subcomandos
        addSubCommand(new VipsGiveKeyCommand(plugin));
        addSubCommand(new VipsReloadCommand(plugin));
        addSubCommand(new VipsAddCommand(plugin));
        addSubCommand(new VipsRemoveCommand(plugin));
        addSubCommand(new VipsListCommand(plugin));
        addSubCommand(new VipsHistoryCommand(plugin));
        addSubCommand(new VipsChestCommand(plugin));
        addSubCommand(new VipsGiveCmdCommand(plugin));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // Sem subcomando: mostra help (só para admin)
        CommandSender sender = null;
        try {
            sender = ctx.sender();
        } catch (Exception ignored) {}

        if (sender != null && sender.hasPermission("jvips.admin")) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.help", null)));
        } else {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("player.help", null)));
        }
        return CompletableFuture.completedFuture(null);
    }
}
