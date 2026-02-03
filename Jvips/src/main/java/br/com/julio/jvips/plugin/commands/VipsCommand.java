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
        addSubCommand(new VipsReloadCommand(plugin));
        addSubCommand(new VipsAddCommand(plugin));
        addSubCommand(new VipsRemoveCommand(plugin));
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sendMessage(Message.raw(
                "JVIPS Commands:\n" +
                        "/vips givekey <vipId> <player|uuid>\n" +
                        "/vips add <player|uuid> <vipId>\n" +
                        "/vips remove <player|uuid> <vipId|*>\n" +
                        "/vips reload"
        ));
        return CompletableFuture.completedFuture(null);
    }
}
