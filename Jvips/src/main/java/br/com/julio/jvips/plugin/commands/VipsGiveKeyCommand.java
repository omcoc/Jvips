package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.items.VoucherItemFactory;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.util.DurationParser;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /vips givekey <vip> <player> [--duration tempo]
 *
 * Exemplos:
 *   /vips givekey thorium __om__                    → duração padrão do vips.json
 *   /vips givekey thorium __om__ --duration 1d2h10m → 1 dia, 2 horas, 10 minutos
 *   /vips givekey thorium __om__ --duration 2h5s    → 2 horas e 5 segundos
 *   /vips givekey thorium __om__ --duration 10m     → 10 minutos
 */
public final class VipsGiveKeyCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final RequiredArg<String> vipIdArg;
    private final RequiredArg<PlayerRef> playerArg;
    private final OptionalArg<String> durationArg;

    public VipsGiveKeyCommand(JvipsPlugin plugin) {
        super("givekey", "Delivers a VIP voucher to a player. Optional: custom duration (e.g. 1d2h10m5s).");
        this.plugin = plugin;

        requirePermission("jvips.admin");

        this.vipIdArg = withRequiredArg("vipId", "Id do VIP (ex: thorium)", ArgTypes.STRING);
        this.playerArg = withRequiredArg("player", "Jogador alvo", ArgTypes.PLAYER_REF);
        this.durationArg = withOptionalArg("duration", "Custom duration (ex: 1d2h10m5s, 30d, 2h30m)", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        final String vipId = vipIdArg.get(ctx);
        final PlayerRef targetRef = playerArg.get(ctx);

        final VipDefinition vipDef;
        try {
            vipDef = plugin.getCore().getConfig().getVipOrThrow(vipId);
        } catch (Exception e) {
            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("vipId", vipId);
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.invalidVip", vars)));
            return;
        }

        // ===== Parse de tempo custom do argumento opcional (--duration 1d2h10m5s) =====
        long customDurationSeconds = 0;
        if (durationArg.provided(ctx)) {
            String durationInput = durationArg.get(ctx);
            if (durationInput != null && !durationInput.trim().isEmpty()) {
                long parsed = DurationParser.parse(durationInput.trim());
                if (parsed > 0) {
                    customDurationSeconds = parsed;
                } else {
                    java.util.Map<String, String> vars = new java.util.HashMap<>();
                    vars.put("input", durationInput);
                    ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                            plugin.getMessages().format("error.invalidDuration", vars)));
                    return;
                }
            }
        }

        final Ref<EntityStore> ref = targetRef.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.playerMustBeOnline", null)));
            return;
        }

        final Store<EntityStore> store = ref.getStore();
        final World world = ((EntityStore) store.getExternalData()).getWorld();

        final UUID targetUuid = targetRef.getUuid();
        final String targetUuidStr = targetUuid.toString();
        final String targetName = targetRef.getUsername();

        final VoucherService.GeneratedVoucher issued =
                plugin.getCore().getVoucherService().generateVoucher(vipId, targetUuidStr, customDurationSeconds);

        final String itemId = (vipDef.getVoucher() != null && vipDef.getVoucher().getItemId() != null
                && !vipDef.getVoucher().getItemId().trim().isEmpty())
                ? vipDef.getVoucher().getItemId().trim()
                : "Jvips_Voucher";

        final long finalCustomDuration = customDurationSeconds;

        world.execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            ItemStack voucherItem = VoucherItemFactory.create(
                    itemId,
                    vipDef,
                    targetName,
                    issued.payload().getIssuedTo(),
                    issued.payload().getVoucherId(),
                    issued.payload().getIssuedAt(),
                    issued.signature(),
                    finalCustomDuration
            );

            ItemStackTransaction tx = player.getInventory()
                    .getCombinedHotbarFirst()
                    .addItemStack(voucherItem);

            if (tx.getRemainder() != null && !tx.getRemainder().isEmpty()) {
                ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.inventoryFull", null)));
                targetRef.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.inventoryFull", null)));
                return;
            }

            long effectiveDuration = finalCustomDuration > 0 ? finalCustomDuration : vipDef.getDurationSeconds();

            java.util.Map<String, String> adminVars = new java.util.HashMap<>();
            adminVars.put("vipId", vipId);
            adminVars.put("player", targetName);
            adminVars.put("duration", br.com.julio.jvips.core.util.DurationFormatter.format(effectiveDuration));
            ctx.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("admin.givekey.ok", adminVars)));

            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("vip", vipId);
            vars.put("duration", br.com.julio.jvips.core.util.DurationFormatter.format(effectiveDuration));
            vars.put("player", targetName);
            vars.put("vipDisplay", (vipDef.getDisplayName() != null && !vipDef.getDisplayName().isEmpty()) ? vipDef.getDisplayName() : vipId);

            String msg = plugin.getMessages().format("voucher.received", vars);
            targetRef.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(msg));
        });
    }

}
