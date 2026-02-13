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
        ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(ok
                ? plugin.getMessages().format("admin.reload.ok", null)
                : plugin.getMessages().format("admin.reload.fail", null)));
    }
}
