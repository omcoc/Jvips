package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

/**
 * Player command: /vip status (or /vip)
 */
public final class VipCommand extends AbstractCommand {

    private final JvipsPlugin plugin;

    public VipCommand(JvipsPlugin plugin) {
        super("vip", "Shows your VIP status.");
        // Use JVips permissions (LuckPerms/etc) instead of auto-generated jplugins.* command nodes.
        requirePermission("jvips.use");
        this.plugin = plugin;

        addSubCommand(new VipStatusCommand(plugin));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // default: behave like /vip status
        new VipStatusCommand(plugin).executeSync(ctx);
        return CompletableFuture.completedFuture(null);
    }
}
