package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

public final class VipsReloadCommand extends CommandBase {

    private final JvipsPlugin plugin;

    public VipsReloadCommand(JvipsPlugin plugin) {
        super("reload", "Recarrega vips.json sem reiniciar o servidor.");
        this.plugin = plugin;

        requirePermission("jvips.admin");
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        boolean ok = plugin.reloadJvipsConfig();
        ctx.sendMessage(Message.raw(ok
                ? "JVIPS: vips.json recarregado com sucesso."
                : "JVIPS: Falha ao recarregar vips.json (veja o console)."));
    }
}
