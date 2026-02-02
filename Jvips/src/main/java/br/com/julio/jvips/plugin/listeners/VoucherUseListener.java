package br.com.julio.jvips.plugin.listeners;

import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.model.VoucherPayload;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.util.UUID;

public final class VoucherUseListener {

    private static final String META_TYPE = "jvips:type";
    private static final String META_SIG = "jvips:sig";
    private static final String META_VIP_ID = "jvips:vipId";
    private static final String META_ISSUED_TO = "jvips:issuedTo";
    private static final String META_VOUCHER_ID = "jvips:voucherId";
    private static final String META_ISSUED_AT = "jvips:issuedAt";

    private static final String TYPE_VIP_VOUCHER = "vip_voucher";

    private final JvipsPlugin plugin;

    public VoucherUseListener(JvipsPlugin plugin) {
        this.plugin = plugin;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        // 1) filtra apenas “uso” / “secundário”
        InteractionType type = event.getActionType();
        if (type != InteractionType.Secondary && type != InteractionType.Use) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        // 2) permissão para usar VIP (você libera via permissões do servidor)
        if (!player.hasPermission("jvips.use")) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("Você não tem permissão para ativar VIP (jvips.use)."));
            return;
        }

        // 3) item na mão (evento)
        ItemStack inHand = event.getItemInHand();
        if (inHand == null || inHand.isEmpty()) return;

        // 4) checa se é voucher do Jvips
        if (!isVipVoucher(inHand)) return;

        // É nosso voucher: cancela a interação padrão para evitar side-effects
        event.setCancelled(true);

        VoucherData data = readVoucherData(inHand);
        if (data == null) {
            player.sendMessage(Message.raw("Voucher inválido."));
            return;
        }

        // 5) valida e ativa via CORE
        VoucherService svc = plugin.getCore().getVoucherService();

        String playerUuid = getPlayerUuid(player);
        if (playerUuid == null) {
            player.sendMessage(Message.raw("Falha ao identificar seu jogador."));
            return;
        }

        String playerName = resolvePlayerNameSafe(playerUuid, player);

        VoucherPayload payload = new VoucherPayload(
                data.voucherId,
                data.vipId,
                data.issuedTo,
                data.issuedAt
        );

        VoucherService.ValidationResult vr = svc.validateVoucher(payload, data.signature, playerUuid);
        if (!vr.ok()) {
            // voucher permanece intacto
            player.sendMessage(Message.raw(msgForKey(vr.errorKey())));
            return;
        }

        // ✅ AQUI: assinatura nova (3 args)
        VoucherService.ActivationResult ar = svc.activateVoucher(payload, playerUuid, playerName);

        if (ar.blockedByExistingVip()) {
            // manter voucher intacto
            player.sendMessage(Message.raw("Você já tem um VIP ativo. Aguarde expirar para ativar outro."));
            return;
        }

        if (!ar.activated()) {
            // fallback, mantém item
            player.sendMessage(Message.raw("Não foi possível ativar o VIP agora."));
            return;
        }

        // 6) consumimos 1 item somente após ativar com sucesso
        consumeOneFromActiveHotbar(player);

        // 7) feedback + executar comandos onActivate
        VipDefinition vip = ar.vip();
        String vipName = (vip != null && vip.getDisplayName() != null) ? vip.getDisplayName() : payload.getVipId();
        player.sendMessage(Message.raw("VIP ativado: " + vipName));

        if (vip != null) {
            plugin.getCore().getCommandService().runActivateCommands(vip, playerName);
        }
    }

    private boolean isVipVoucher(ItemStack stack) {
        BsonDocument meta = stack.getMetadata();
        if (meta == null) return false;

        BsonValue v = meta.get(META_TYPE);
        if (v == null || !v.isString()) return false;

        return TYPE_VIP_VOUCHER.equals(v.asString().getValue());
    }

    private VoucherData readVoucherData(ItemStack stack) {
        BsonDocument meta = stack.getMetadata();
        if (meta == null) return null;

        String vipId = getString(meta, META_VIP_ID);
        String issuedTo = getString(meta, META_ISSUED_TO);
        String voucherId = getString(meta, META_VOUCHER_ID);
        long issuedAt = getLong(meta, META_ISSUED_AT);
        String sig = getString(meta, META_SIG);

        if (vipId == null || issuedTo == null || voucherId == null || sig == null) return null;

        return new VoucherData(vipId, issuedTo, voucherId, issuedAt, sig);
    }

    private static String getPlayerUuid(Player player) {
        try {
            UUID uuid = player.getPlayerRef().getUuid(); // deprecated, mas funciona nessa build
            return uuid.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolvePlayerNameSafe(String playerUuid, Player player) {
        try {
            Object ref = player.getPlayerRef();
            if (ref == null) return playerUuid;

            // tenta getName()
            try {
                return (String) ref.getClass().getMethod("getName").invoke(ref);
            } catch (NoSuchMethodException ignored) {}

            // tenta getUsername()
            try {
                return (String) ref.getClass().getMethod("getUsername").invoke(ref);
            } catch (NoSuchMethodException ignored) {}

            // tenta getDisplayName()
            try {
                return (String) ref.getClass().getMethod("getDisplayName").invoke(ref);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable ignored) {}

        return (playerUuid != null ? playerUuid : "unknown");
    }

    private void consumeOneFromActiveHotbar(Player player) {
        Inventory inv = player.getInventory();
        if (inv == null) return;

        ItemContainer hotbar = inv.getHotbar();
        if (hotbar == null) return;

        // Em algumas builds isso é byte; em outras, short. Forçamos para short por segurança.
        short slot = (short) inv.getActiveHotbarSlot();

        ItemStack current = hotbar.getItemStack(slot);
        if (current == null || current.isEmpty()) return;

        int q = current.getQuantity();
        if (q <= 1) {
            hotbar.setItemStackForSlot(slot, ItemStack.EMPTY);
        } else {
            hotbar.setItemStackForSlot(slot, current.withQuantity(q - 1));
        }

        // sincroniza inventário com o client
        player.sendInventory();
    }

    private String msgForKey(String key) {
        if (key == null) return "Voucher inválido.";
        return switch (key) {
            case "invalidVoucher" -> "Voucher inválido.";
            case "notYourVoucher" -> "Este voucher não é seu.";
            case "alreadyUsedVoucher" -> "Este voucher já foi usado.";
            default -> "Não foi possível ativar o voucher (" + key + ").";
        };
    }

    private static String getString(BsonDocument doc, String k) {
        BsonValue v = doc.get(k);
        if (v == null || !v.isString()) return null;
        return v.asString().getValue();
    }

    private static long getLong(BsonDocument doc, String k) {
        BsonValue v = doc.get(k);
        if (v == null) return 0L;
        if (v.isInt64()) return v.asInt64().getValue();
        if (v.isInt32()) return v.asInt32().getValue();
        return 0L;
    }

    private record VoucherData(String vipId, String issuedTo, String voucherId, long issuedAt, String signature) {}
}
