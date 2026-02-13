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

import br.com.julio.jvips.plugin.util.VipBroadcastManager;

public final class JvipsVoucherInteraction extends SimpleInstantInteraction {

    private static final String META_TYPE = "jvips:type";
    private static final String META_SIG = "jvips:sig";
    private static final String META_VIP_ID = "jvips:vipId";
    private static final String META_ISSUED_TO = "jvips:issuedTo";
    private static final String META_VOUCHER_ID = "jvips:voucherId";
    private static final String META_ISSUED_AT = "jvips:issuedAt";
    private static final String META_CUSTOM_DURATION = "jvips:customDuration";
    private static final String META_CMD_VOUCHER_ID = "jvips:cmdVoucherId";

    private static final String TYPE_VIP_VOUCHER = "vip_voucher";
    private static final String TYPE_COMMAND_VOUCHER = "command_voucher";

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

            if (!isJvipsVoucher(held)) {
                interactionContext.getState().state = InteractionState.Finished;
                return;
            }

            if (!player.hasPermission("jvips.use")) {
                java.util.Map<String, String> vars = new java.util.HashMap<>();
                vars.put("perm", "jvips.use");
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.noPermissionUse", vars)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // Determinar tipo de voucher
            String voucherType = getVoucherType(held);

            if (TYPE_COMMAND_VOUCHER.equals(voucherType)) {
                handleCommandVoucher(plugin, player, held, interactionContext);
                return;
            }

            // Fluxo VIP voucher (padrão)

            VoucherData data = readVoucherData(held);
            if (data == null) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.invalidVoucher", null)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            String playerUuid = getPlayerUuid(player);
            if (playerUuid == null) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.playerResolveFailed", null)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // resolve nome UMA vez (vai ser salvo em players.json como lastKnownName)
            String playerName = resolvePlayerNameSafe(playerUuid, player);

            VoucherPayload payload = new VoucherPayload(
                    data.voucherId, data.vipId, data.issuedTo, data.issuedAt, data.customDuration
            );

            VoucherService svc = plugin.getCore().getVoucherService();

            VoucherService.ValidationResult vr = svc.validateVoucher(payload, data.signature, playerUuid);
            if (!vr.ok()) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(msgForKey(vr.errorKey())));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // ✅ NOVA ASSINATURA: agora passa o nome também (lastKnownName)
            VoucherService.ActivationResult ar = svc.activateVoucher(payload, playerUuid, playerName);


            if (ar.blockedByStackLimit()) {
                java.util.Map<String, String> stackVars = new java.util.HashMap<>();
                stackVars.put("maxStack", String.valueOf(ar.maxStack()));
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.stackLimitReached", stackVars)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            if (ar.blockedByExistingVip()) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.alreadyHasVip", null)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }
            if (!ar.activated()) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("error.activationFailed", null)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // consome o item
            consumeOne(interactionContext);

            // Gravar no history.json
            VipDefinition vipDef = ar.vip();
            if (vipDef != null && ar.newState() != null && !ar.stacked()) {
                br.com.julio.jvips.plugin.util.HistoryRecorder.recordActivation(
                        playerUuid, vipDef,
                        ar.newState().getActivatedAt(), ar.newState().getExpiresAt()
                );
            }

            String vipDisplay = (vipDef != null && vipDef.getDisplayName() != null && !vipDef.getDisplayName().isEmpty())
                    ? vipDef.getDisplayName()
                    : payload.getVipId();

            if (ar.stacked()) {
                // Mensagem de stack
                java.util.Map<String, String> stackVars = new java.util.HashMap<>();
                stackVars.put("vipDisplay", vipDisplay);
                stackVars.put("addedDuration", br.com.julio.jvips.core.util.DurationFormatter.format(ar.addedDuration()));
                stackVars.put("totalRemaining", br.com.julio.jvips.core.util.DurationFormatter.format(
                        ar.newState().getExpiresAt() - java.time.Instant.now().getEpochSecond()));
                stackVars.put("stackCount", String.valueOf(ar.newState().getStackCount()));
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("player.vipStacked", stackVars)));
            } else {
                // Mensagem de ativação normal
                java.util.Map<String, String> vars = new java.util.HashMap<>();
                vars.put("vipDisplay", vipDisplay);
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("player.vipActivated", vars)));
            }

            // Broadcast na tela (Event Title) ao ativar VIP (com cooldown global e apenas para ativações via voucher)
            VipBroadcastManager.tryBroadcastVipActivated(
                    plugin,
                    VipBroadcastManager.ActivationSource.VOUCHER,
                    playerName,
                    vipDisplay
            );

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

    /**
     * Verifica se o item é qualquer voucher JVIPS (vip_voucher ou command_voucher).
     */
    private static boolean isJvipsVoucher(ItemStack stack) {
        BsonDocument meta = stack.getMetadata();
        if (meta == null) return false;

        BsonValue v = meta.get(META_TYPE);
        if (v == null || !v.isString()) return false;

        String type = v.asString().getValue();
        return TYPE_VIP_VOUCHER.equals(type) || TYPE_COMMAND_VOUCHER.equals(type);
    }

    /**
     * Retorna o tipo de voucher: "vip_voucher" ou "command_voucher".
     */
    private static String getVoucherType(ItemStack stack) {
        BsonDocument meta = stack.getMetadata();
        if (meta == null) return null;
        BsonValue v = meta.get(META_TYPE);
        if (v == null || !v.isString()) return null;
        return v.asString().getValue();
    }

    /**
     * Fluxo de Command Voucher: sem HMAC, sem VIP, sem persist.
     * Apenas valida vínculo UUID → roda comandos → consome item.
     */
    private void handleCommandVoucher(JvipsPlugin plugin, Player player, ItemStack held,
                                      InteractionContext interactionContext) {
        try {
            BsonDocument meta = held.getMetadata();
            String cmdVoucherId = getString(meta, META_CMD_VOUCHER_ID);
            String issuedTo = getString(meta, META_ISSUED_TO);

            if (cmdVoucherId == null || issuedTo == null) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.invalidVoucher", null)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // Validar vínculo UUID
            String playerUuid = getPlayerUuid(player);
            if (playerUuid == null || !issuedTo.equals(playerUuid)) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.notYourVoucher", null)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // Buscar definição no config
            br.com.julio.jvips.core.config.CommandVoucherConfig cmdConfig = plugin.getCommandVoucherConfig();
            if (cmdConfig == null) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.invalidVoucher", null)));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            br.com.julio.jvips.core.model.CommandVoucherDefinition def = cmdConfig.get(cmdVoucherId);
            if (def == null) {
                player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.invalidCommandVoucher",
                                java.util.Map.of("id", cmdVoucherId))));
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            // Consumir o item ANTES de rodar os comandos
            consumeOne(interactionContext);

            // Resolver nome do jogador
            String playerName = resolvePlayerNameSafe(playerUuid, player);

            // Mensagem ao jogador
            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("displayName", def.getDisplayName());
            vars.put("player", playerName);
            player.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("commandvoucher.activated", vars)));

            // Executar commandsOnActivate
            try {
                VipCommandService cmdSvc = plugin.getCore().getCommandService();
                if (cmdSvc != null && def.getCommandsOnActivate() != null) {
                    for (String rawCmd : def.getCommandsOnActivate()) {
                        String resolved = rawCmd.replace("{player}", playerName);
                        cmdSvc.dispatchCommand(resolved);
                    }
                    System.out.println("[JVIPS] CommandVoucher '" + cmdVoucherId + "' activated by " + playerName
                            + " — " + def.getCommandsOnActivate().size() + " commands executed");
                }
            } catch (Throwable t) {
                System.err.println("[JVIPS] Failed to run commandVoucher commands: " + t);
                t.printStackTrace();
            }

            interactionContext.getState().state = InteractionState.Finished;

        } catch (Throwable t) {
            System.err.println("[JVIPS] CommandVoucher handling error: " + t);
            interactionContext.getState().state = InteractionState.Failed;
        }
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
        long customDuration = getLong(meta, META_CUSTOM_DURATION);

        if (vipId == null || issuedTo == null || voucherId == null || sig == null) return null;
        return new VoucherData(vipId, issuedTo, voucherId, issuedAt, sig, customDuration);
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

    private record VoucherData(String vipId, String issuedTo, String voucherId, long issuedAt, String signature, long customDuration) {}
}
