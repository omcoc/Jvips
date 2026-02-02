package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

public final class VipsCommand extends AbstractCommand {

    private final JvipsPlugin plugin;

    public VipsCommand(JvipsPlugin plugin) {
        super("vips", "Gerenciamento de VIPs (Jvips)");
        this.plugin = plugin;

        // subcomandos
        addSubCommand(new VipsGiveKeyCommand(plugin));
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Uso: /vips givekey <vipId> <player>"));
        return CompletableFuture.completedFuture(null);
    }
}
