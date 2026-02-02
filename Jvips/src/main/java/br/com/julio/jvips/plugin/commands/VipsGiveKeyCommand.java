package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
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

import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;

import java.util.UUID;

public final class VipsGiveKeyCommand extends CommandBase {

    private final JvipsPlugin plugin;

    private final RequiredArg<String> vipIdArg;
    private final RequiredArg<PlayerRef> playerArg;

    public VipsGiveKeyCommand(JvipsPlugin plugin) {
        super("givekey", "Entrega um voucher (key) de VIP para um jogador.");
        this.plugin = plugin;

        // Somente staff/admin
        requirePermission("jvips.admin");

        this.vipIdArg = withRequiredArg("vipId", "Id do VIP (ex: thorium)", ArgTypes.STRING);
        this.playerArg = withRequiredArg("player", "Jogador alvo", ArgTypes.PLAYER_REF);
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        String vipId = vipIdArg.get(ctx);
        PlayerRef targetRef = playerArg.get(ctx);

        // 1) valida VIP existe
        VipDefinition vipDef;
        try {
            vipDef = plugin.getCore().getConfig().getVipOrThrow(vipId);
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("VIP inválido: " + vipId));
            return;
        }

        // 2) valida player reference (precisa estar em um mundo válido)
        Ref<EntityStore> ref = targetRef.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("O jogador não está em um mundo válido agora (precisa estar online/in-game)."));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();

        UUID targetUuid = targetRef.getUuid();
        String targetUuidStr = targetUuid.toString();

        // 3) gera voucher no CORE (seu método real)
        VoucherService.GeneratedVoucher issued = plugin.getCore()
                .getVoucherService()
                .generateVoucher(vipId, targetUuidStr);

        // 4) resolve itemId do voucher (do JSON / VipDefinition)
        String itemId = null;
        if (vipDef.getVoucher() != null) {
            itemId = vipDef.getVoucher().getItemId();
        }
        if (itemId == null || itemId.isBlank()) {
            itemId = "Jvips_Voucher"; // fallback seguro
        }
        final String finalItemId = itemId;
        // 5) entrega no thread do world
        world.execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("Falha ao localizar o Player componente do alvo."));
                return;
            }

            ItemStack voucherItem = buildVoucherItem(
                    finalItemId,
                    issued.payload().getVipId(),
                    issued.payload().getIssuedTo(),
                    issued.payload().getVoucherId(),
                    issued.payload().getIssuedAt(),
                    issued.signature()
            );

            // Hotbar first (mesmo padrão do core do Hytale)
            ItemStackTransaction tx = player.getInventory()
                    .getCombinedHotbarFirst()
                    .addItemStack(voucherItem);

            ItemStack rem = tx.getRemainder();
            boolean ok = (rem == null || rem.isEmpty());

            if (!ok) {
                // Aqui ainda não dropamos no chão (vamos implementar com entidade de item no próximo passo).
                ctx.sendMessage(Message.raw("Inventário do jogador cheio. Voucher NÃO foi entregue."));
                targetRef.sendMessage(Message.raw("Seu inventário está cheio. Libere espaço para receber o voucher."));
                return;
            }

            ctx.sendMessage(Message.raw("Voucher do VIP '" + vipId + "' entregue para " + targetRef.getUsername() + "."));
            targetRef.sendMessage(Message.raw("Você recebeu um voucher de VIP: " + vipId + ". Clique direito para ativar."));
        });
    }

    private static ItemStack buildVoucherItem(
            String itemId,
            String vipId,
            String issuedToUuid,
            String voucherId,
            long issuedAt,
            String signature
    ) {
        BsonDocument meta = new BsonDocument();

        // =============================
        // METADATA FUNCIONAL (lógica)
        // =============================
        meta.append("jvips:type", new BsonString("vip_voucher"));
        meta.append("jvips:vipId", new BsonString(vipId));
        meta.append("jvips:issuedTo", new BsonString(issuedToUuid));
        meta.append("jvips:voucherId", new BsonString(voucherId));
        meta.append("jvips:issuedAt", new BsonInt64(issuedAt));
        meta.append("jvips:sig", new BsonString(signature));

        // =============================
        // METADATA VISUAL (UI / TOOLTIP)
        // =============================

        // Nome do item (override visual)
        meta.append("display_name", new BsonString("§6Voucher VIP §f(" + vipId + ")"));

        // Lore (linhas)
        meta.append("lore", new org.bson.BsonArray(java.util.List.of(
                new BsonString("§7Ativa o VIP §e" + vipId),
                new BsonString("§7Clique com o botão direito"),
                new BsonString(""),
                new BsonString("§8Vinculado ao jogador"),
                new BsonString("§8ID: " + voucherId.substring(0, 8))
        )));

        return new ItemStack(itemId, 1, meta);
    }

}
