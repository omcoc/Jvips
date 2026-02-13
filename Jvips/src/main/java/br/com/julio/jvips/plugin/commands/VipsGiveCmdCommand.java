package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.config.CommandVoucherConfig;
import br.com.julio.jvips.core.items.CommandVoucherItemFactory;
import br.com.julio.jvips.core.model.CommandVoucherDefinition;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /vips givecmd <commandVoucherId> <player>
 *
 * Entrega um Command Voucher ao jogador. Sem HMAC, sem duração.
 */
public final class VipsGiveCmdCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final RequiredArg<String> cmdVoucherIdArg;
    private final RequiredArg<PlayerRef> playerArg;

    public VipsGiveCmdCommand(JvipsPlugin plugin) {
        super("givecmd", "Delivers a Command Voucher to a player.");
        this.plugin = plugin;

        requirePermission("jvips.admin");

        this.cmdVoucherIdArg = withRequiredArg("commandVoucherId", "Command voucher ID (ex: hunterquest)", ArgTypes.STRING);
        this.playerArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        final String cmdVoucherId = cmdVoucherIdArg.get(ctx);
        final PlayerRef targetRef = playerArg.get(ctx);

        CommandVoucherConfig cmdConfig = plugin.getCommandVoucherConfig();
        if (cmdConfig == null) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.commandVoucherNotLoaded", null)));
            return;
        }

        final CommandVoucherDefinition def = cmdConfig.get(cmdVoucherId);
        if (def == null) {
            Map<String, String> vars = new HashMap<>();
            vars.put("id", cmdVoucherId);
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.invalidCommandVoucher", vars)));
            return;
        }

        final Ref<EntityStore> ref = targetRef.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.playerMustBeOnline", null)));
            return;
        }

        final Store<EntityStore> store = ref.getStore();
        final World world = ((EntityStore) store.getExternalData()).getWorld();

        final UUID targetUuid = targetRef.getUuid();
        final String targetUuidStr = targetUuid.toString();
        final String targetName = targetRef.getUsername();

        final String voucherId = UUID.randomUUID().toString();
        final long issuedAt = Instant.now().getEpochSecond();

        world.execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            ItemStack voucherItem = CommandVoucherItemFactory.create(
                    def, targetName, targetUuidStr, voucherId, issuedAt
            );

            ItemStackTransaction tx = player.getInventory()
                    .getCombinedHotbarFirst()
                    .addItemStack(voucherItem);

            if (tx.getRemainder() != null && !tx.getRemainder().isEmpty()) {
                ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.inventoryFull", null)));
                targetRef.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.inventoryFull", null)));
                return;
            }

            // Admin feedback
            Map<String, String> adminVars = new HashMap<>();
            adminVars.put("id", cmdVoucherId);
            adminVars.put("player", targetName);
            adminVars.put("displayName", def.getDisplayName());
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("admin.givecmd.ok", adminVars)));

            // Player notification
            Map<String, String> playerVars = new HashMap<>();
            playerVars.put("displayName", def.getDisplayName());
            playerVars.put("player", targetName);
            targetRef.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("commandvoucher.received", playerVars)));
        });
    }
}
