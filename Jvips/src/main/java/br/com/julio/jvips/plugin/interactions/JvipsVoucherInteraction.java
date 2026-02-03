package br.com.julio.jvips.plugin.interactions;

import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.VipCommandService;
import br.com.julio.jvips.core.model.VoucherPayload;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.plugin.JvipsPlugin;
import br.com.julio.jvips.plugin.JvipsServices;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.protocol.InteractionType;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionState;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class JvipsVoucherInteraction extends SimpleInstantInteraction {

    private static final String META_TYPE = "jvips:type";
    private static final String META_SIG = "jvips:sig";
    private static final String META_VIP_ID = "jvips:vipId";
    private static final String META_ISSUED_TO = "jvips:issuedTo";
    private static final String META_VOUCHER_ID = "jvips:voucherId";
    private static final String META_ISSUED_AT = "jvips:issuedAt";

    private static final String TYPE_VIP_VOUCHER = "vip_voucher";

    private Player resolvePlayer(InteractionContext ctx) {
        Ref<EntityStore> ref = ctx.getEntity();
        if (ref == null || !ref.isValid()) return null;

        CommandBuffer<EntityStore> buffer = ctx.getCommandBuffer();
        if (buffer == null) return null;

        return buffer.getComponent(ref, Player.getComponentType());
    }

    public static final BuilderCodec<JvipsVoucherInteraction> CODEC =
            BuilderCodec.builder(
                    JvipsVoucherInteraction.class,
                    JvipsVoucherInteraction::new,
                    SimpleInstantInteraction.CODEC
            ).build();

    public JvipsVoucherInteraction() {
        super();
    }

    @Override
    protected void firstRun(
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext interactionContext,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        try {
            // só aceita Secondary
            if (interactionType != InteractionType.Secondary) {
                interactionContext.getState().state = InteractionState.Finished;
                return;
            }

            JvipsPlugin plugin = JvipsServices.getPlugin();
            if (plugin == null) {
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            Player player = resolvePlayer(interactionContext);
            if (player == null) {
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            ItemStack held = interactionContext.getHeldItem();
            if (held == null || held.isEmpty()) {
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            if (!isVipVoucher(held)) {
                interactionContext.getState().state = InteractionState.Finished;
                return;
            }

            if (!player.hasPermission("jvips.use")) {
                player.sendMessage(Message.raw("Você não tem permissão para ativar VIP (jvips.use)."));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            VoucherData data = readVoucherData(held);
            if (data == null) {
                player.sendMessage(Message.raw("Voucher inválido."));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            String playerUuid = getPlayerUuid(player);
            if (playerUuid == null) {
                player.sendMessage(Message.raw("Falha ao identificar seu jogador."));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // resolve nome UMA vez (vai ser salvo em players.json como lastKnownName)
            String playerName = resolvePlayerNameSafe(playerUuid, player);

            VoucherPayload payload = new VoucherPayload(
                    data.voucherId, data.vipId, data.issuedTo, data.issuedAt
            );

            VoucherService svc = plugin.getCore().getVoucherService();

            VoucherService.ValidationResult vr = svc.validateVoucher(payload, data.signature, playerUuid);
            if (!vr.ok()) {
                player.sendMessage(Message.raw(msgForKey(vr.errorKey())));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // ✅ NOVA ASSINATURA: agora passa o nome também (lastKnownName)
            VoucherService.ActivationResult ar = svc.activateVoucher(payload, playerUuid, playerName);


            if (ar.blockedByExistingVip()) {
                player.sendMessage(Message.raw("Você já tem um VIP ativo. Aguarde expirar para ativar outro."));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }
            if (!ar.activated()) {
                player.sendMessage(Message.raw("Não foi possível ativar o VIP agora."));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // consome o item
            consumeOne(interactionContext);

            VipDefinition vipDef = ar.vip();
            if (vipDef != null) {
                player.sendMessage(Message.raw("VIP ativado: " + vipDef.getDisplayName()));
            } else {
                player.sendMessage(Message.raw("VIP ativado com sucesso."));
            }

            // ✅ Executar commandsOnActivate do vips.json
            try {
                VipCommandService cmdSvc = plugin.getCore().getCommandService();

                if (cmdSvc != null && vipDef != null) {
                    cmdSvc.runActivateCommands(vipDef, playerName);
                    System.out.println("[JVIPS] commandsOnActivate disparado vip=" + vipDef.getId() + " player=" + playerName);
                } else {
                    System.out.println("[JVIPS] Não foi possível executar commandsOnActivate (cmdSvc="
                            + (cmdSvc != null) + ", vipDef=" + (vipDef != null) + ")");
                }
            } catch (Throwable t) {
                System.err.println("[JVIPS] Falha ao rodar commandsOnActivate: " + t);
                t.printStackTrace();
            }

            interactionContext.getState().state = InteractionState.Finished;

        } catch (Throwable t) {
            interactionContext.getState().state = InteractionState.Failed;
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

    private static boolean isVipVoucher(ItemStack stack) {
        BsonDocument meta = stack.getMetadata();
        if (meta == null) return false;

        BsonValue v = meta.get(META_TYPE);
        if (v == null || !v.isString()) return false;

        return TYPE_VIP_VOUCHER.equals(v.asString().getValue());
    }

    private static VoucherData readVoucherData(ItemStack stack) {
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
            UUID uuid = player.getPlayerRef().getUuid(); // deprecado, mas funciona nessa build
            return uuid.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void consumeOne(InteractionContext ctx) {
        ItemContainer container = ctx.getHeldItemContainer();
        if (container == null) return;

        byte slot = ctx.getHeldItemSlot();
        ItemStack current = container.getItemStack(slot);
        if (current == null || current.isEmpty()) return;

        int q = current.getQuantity();
        if (q <= 1) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY);
        } else {
            container.setItemStackForSlot(slot, current.withQuantity(q - 1));
        }
    }

    private static String msgForKey(String key) {
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
