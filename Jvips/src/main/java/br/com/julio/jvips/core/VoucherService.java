package br.com.julio.jvips.core;

import br.com.julio.jvips.core.config.VipConfig;
import br.com.julio.jvips.core.crypto.HmacSigner;
import br.com.julio.jvips.core.model.PlayerVipState;
import br.com.julio.jvips.core.model.VipHistoryEntry;
import br.com.julio.jvips.core.model.VoucherPayload;
import br.com.julio.jvips.core.model.VoucherRecord;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.storage.PlayersStore;
import br.com.julio.jvips.core.storage.VouchersStore;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VoucherService {

    private final VipConfig config;
    private final PlayersStore playersStore;
    private final VouchersStore vouchersStore;

    public VoucherService(VipConfig config, PlayersStore playersStore, VouchersStore vouchersStore) {
        this.config = config;
        this.playersStore = playersStore;
        this.vouchersStore = vouchersStore;
    }

    /** Gera payload + assinatura (HMAC) para um voucher amarrado ao jogador */
    public GeneratedVoucher generateVoucher(String vipId, String issuedToPlayerUuid) {
        return generateVoucher(vipId, issuedToPlayerUuid, 0);
    }

    /** Gera voucher com duração custom (0 = usar padrão do vips.json) */
    public GeneratedVoucher generateVoucher(String vipId, String issuedToPlayerUuid, long customDurationSeconds) {
        config.getVipOrThrow(vipId);

        String voucherId = UUID.randomUUID().toString();
        long issuedAt = Instant.now().getEpochSecond();

        VoucherPayload payload = new VoucherPayload(
                voucherId, vipId, issuedToPlayerUuid, issuedAt, customDurationSeconds
        );

        String signature = sign(payload);

        Map<String, VoucherRecord> vouchers = vouchersStore.load();
        vouchers.put(voucherId, new VoucherRecord(vipId, issuedToPlayerUuid, issuedAt));
        vouchersStore.save(vouchers);

        return new GeneratedVoucher(payload, signature);
    }

    /** Valida assinatura e vínculo do voucher */
    public ValidationResult validateVoucher(
            VoucherPayload payload,
            String signature,
            String activatingPlayerUuid
    ) {
        // 1) assinatura
        String expected = sign(payload);
        if (!expected.equals(signature)) {
            return ValidationResult.failure("invalidVoucher");
        }

        // 2) amarrado ao jogador
        if (!payload.getIssuedTo().equals(activatingPlayerUuid)) {
            return ValidationResult.failure("notYourVoucher");
        }

        // 3) já usado?
        Map<String, VoucherRecord> vouchers = vouchersStore.load();
        VoucherRecord record = vouchers.get(payload.getVoucherId());
        if (record == null) {
            return ValidationResult.failure("invalidVoucher");
        }
        if (record.isUsed()) {
            return ValidationResult.failure("alreadyUsedVoucher");
        }

        return ValidationResult.success();
    }

    /** Aplica o VIP via voucher. Suporta stacking se configurado no vips.json. */
    public ActivationResult activateVoucher(
            VoucherPayload payload,
            String activatingPlayerUuid,
            String activatingPlayerName
    ) {
        long now = Instant.now().getEpochSecond();
        VipDefinition vip = config.getVipOrThrow(payload.getVipId());
        long duration = payload.getEffectiveDuration(vip.getDurationSeconds());

        Map<String, PlayerVipState> players = playersStore.load();
        PlayerVipState state = players.get(activatingPlayerUuid);

        // Se já tem VIP ativo...
        if (state != null && state.hasActiveVip(now)) {
            String activeId = state.getActiveVipId();

            // Mesmo VIP + stackable → estende o tempo
            if (activeId != null && activeId.equalsIgnoreCase(vip.getId()) && vip.isStackable()) {
                int maxStack = vip.getStackAmount();
                if (maxStack > 0 && state.getStackCount() >= maxStack) {
                    return ActivationResult.blockedStackLimit(state, maxStack);
                }

                state.setExpiresAt(state.getExpiresAt() + duration);
                state.setStackCount(state.getStackCount() + 1);
                state.setRemindersSent(0);

                if (activatingPlayerName != null && !activatingPlayerName.trim().isEmpty()) {
                    state.setLastKnownName(activatingPlayerName.trim());
                }

                players = new HashMap<>(players);
                players.put(activatingPlayerUuid, state);
                playersStore.save(players);

                markVoucherUsed(payload.getVoucherId(), activatingPlayerUuid);

                return ActivationResult.stacked(vip, state, duration);
            }

            // VIP diferente ou não stackable → bloqueia
            return ActivationResult.blockedAlreadyHasVip(state);
        }

        // Ativação normal (sem VIP ativo)
        long expiresAt = now + duration;

        String lastKnownName = (activatingPlayerName == null || activatingPlayerName.trim().isEmpty())
                ? activatingPlayerUuid
                : activatingPlayerName.trim();

        PlayerVipState newState = new PlayerVipState(
                payload.getVipId(), now, expiresAt, lastKnownName
        );
        newState.setStackCount(0);

        if (state != null) {
            newState.setHistory(state.getHistory());
        }

        players = new HashMap<>(players);
        players.put(activatingPlayerUuid, newState);
        playersStore.save(players);

        markVoucherUsed(payload.getVoucherId(), activatingPlayerUuid);

        return ActivationResult.activated(vip, newState);
    }

    private void markVoucherUsed(String voucherId, String playerUuid) {
        long now = Instant.now().getEpochSecond();
        Map<String, VoucherRecord> vouchers = vouchersStore.load();
        VoucherRecord record = vouchers.get(voucherId);
        if (record != null) {
            record.setUsedAt(now);
            record.setUsedBy(playerUuid);
            vouchers.put(voucherId, record);
            vouchersStore.save(vouchers);
        }
    }

    // =========================================================
    // ✅ ADMIN METHODS (para /vips add e /vips remove)
    // =========================================================

    /**
     * Admin adiciona VIP direto (sem voucher). Respeita regra: 1 VIP por vez.
     * Retorna ActivationResult (mesma estrutura do fluxo normal) para você reutilizar mensagens/logs.
     *
     * Observação: você pode passar playerName depois se quiser.
     */
    public ActivationResult adminAddVip(String vipId, String targetPlayerUuid) {
        return adminAddVip(vipId, targetPlayerUuid, targetPlayerUuid, 0);
    }

    public ActivationResult adminAddVip(String vipId, String targetPlayerUuid, String targetPlayerName) {
        return adminAddVip(vipId, targetPlayerUuid, targetPlayerName, 0);
    }

    /**
     * Admin adiciona VIP direto (sem voucher). Suporta duração custom e stacking.
     */
    public ActivationResult adminAddVip(String vipId, String targetPlayerUuid, String targetPlayerName, long customDurationSeconds) {
        long now = Instant.now().getEpochSecond();

        VipDefinition vip = config.getVipOrThrow(vipId);
        long duration = customDurationSeconds > 0 ? customDurationSeconds : vip.getDurationSeconds();

        Map<String, PlayerVipState> players = playersStore.load();
        PlayerVipState current = players.get(targetPlayerUuid);

        // Se já tem VIP ativo...
        if (current != null && current.hasActiveVip(now)) {
            String activeId = current.getActiveVipId();

            // Mesmo VIP + stackable → estende
            if (activeId != null && activeId.equalsIgnoreCase(vip.getId()) && vip.isStackable()) {
                int maxStack = vip.getStackAmount();
                if (maxStack > 0 && current.getStackCount() >= maxStack) {
                    return ActivationResult.blockedStackLimit(current, maxStack);
                }

                current.setExpiresAt(current.getExpiresAt() + duration);
                current.setStackCount(current.getStackCount() + 1);
                current.setRemindersSent(0);

                if (targetPlayerName != null && !targetPlayerName.trim().isEmpty()) {
                    current.setLastKnownName(targetPlayerName.trim());
                }

                players = new HashMap<>(players);
                players.put(targetPlayerUuid, current);
                playersStore.save(players);

                return ActivationResult.stacked(vip, current, duration);
            }

            return ActivationResult.blockedAlreadyHasVip(current);
        }

        long expiresAt = now + duration;

        String lastKnownName = (targetPlayerName == null || targetPlayerName.trim().isEmpty())
                ? targetPlayerUuid
                : targetPlayerName.trim();

        PlayerVipState newState = new PlayerVipState(
                vip.getId(), now, expiresAt, lastKnownName
        );
        newState.setStackCount(0);

        if (current != null) {
            newState.setHistory(current.getHistory());
        }

        players = new HashMap<>(players);
        players.put(targetPlayerUuid, newState);
        playersStore.save(players);

        return ActivationResult.activated(vip, newState);
    }

    /**
     * Admin remove VIP do jogador (se o vipId bater com o ativo).
     * Retorna true se removeu, false se não havia/batia.
     */
    public boolean adminRemoveVip(String vipId, String targetPlayerUuid) {
        Map<String, PlayerVipState> players = playersStore.load();
        PlayerVipState st = players.get(targetPlayerUuid);
        if (st == null) return false;

        String active = st.getActiveVipId();
        if (active == null || active.isEmpty()) return false;

        // se vipId foi fornecido e não bate, não remove
        if (vipId != null && !vipId.isBlank() && !active.equalsIgnoreCase(vipId.trim())) {
            return false;
        }

        // Salvar no histórico antes de limpar
        long now = Instant.now().getEpochSecond();
        String displayName = active;
        try {
            VipDefinition def = config.getVipOrThrow(active);
            if (def.getDisplayName() != null && !def.getDisplayName().isEmpty()) {
                displayName = def.getDisplayName();
            }
        } catch (Exception ignored) {}

        VipHistoryEntry histEntry = new VipHistoryEntry(
                active, displayName,
                st.getActivatedAt(), st.getExpiresAt(),
                now, "admin_remove"
        );
        st.addHistoryEntry(histEntry);

        // Limpa VIP ativo mas mantém o state (para preservar history)
        st.setActiveVipId(null);
        st.setActivatedAt(0);
        st.setExpiresAt(0);
        st.setRemindersSent(0);

        players = new HashMap<>(players);
        players.put(targetPlayerUuid, st);
        playersStore.save(players);

        return true;
    }

    /**
     * Só para o VipsRemoveCommand conseguir pegar o VipDefinition certo
     * (para rodar commandsOnExpire manualmente quando remover via admin).
     *
     * Retorna o VipDefinition SE (a) o jogador tem vip ativo e (b) vipId bate.
     */
    public VipDefinition peekActiveVipDefinition(String targetPlayerUuid, String vipId) {
        long now = Instant.now().getEpochSecond();

        Map<String, PlayerVipState> players = playersStore.load();
        PlayerVipState st = players.get(targetPlayerUuid);
        if (st == null) return null;

        if (!st.hasActiveVip(now)) return null;

        String active = st.getActiveVipId();
        if (active == null || active.isEmpty()) return null;

        if (vipId != null && !vipId.isBlank() && !active.equalsIgnoreCase(vipId.trim())) {
            return null;
        }

        try {
            return config.getVipOrThrow(active);
        } catch (Exception ignored) {
            return null;
        }
    }

    // =========================================================

    private String sign(VoucherPayload payload) {
        return HmacSigner.hmacSha256Hex(
                config.getSecurity().getHmacSecret(),
                payload.toSignableString()
        );
    }

    /* ===== Resultados ===== */

    public record GeneratedVoucher(VoucherPayload payload, String signature) {}

    public record ValidationResult(boolean ok, String errorKey) {
        public static ValidationResult success() { return new ValidationResult(true, null); }
        public static ValidationResult failure(String errorKey) { return new ValidationResult(false, errorKey); }
    }

    public record ActivationResult(
            boolean activated,
            boolean blockedByExistingVip,
            boolean stacked,
            boolean blockedByStackLimit,
            int maxStack,
            long addedDuration,
            VipDefinition vip,
            PlayerVipState previousState,
            PlayerVipState newState
    ) {
        public static ActivationResult blockedAlreadyHasVip(PlayerVipState state) {
            return new ActivationResult(false, true, false, false, 0, 0, null, state, null);
        }

        public static ActivationResult blockedStackLimit(PlayerVipState state, int maxStack) {
            return new ActivationResult(false, false, false, true, maxStack, 0, null, state, null);
        }

        public static ActivationResult activated(VipDefinition vip, PlayerVipState newState) {
            return new ActivationResult(true, false, false, false, 0, 0, vip, null, newState);
        }

        public static ActivationResult stacked(VipDefinition vip, PlayerVipState newState, long addedDuration) {
            return new ActivationResult(true, false, true, false, 0, addedDuration, vip, null, newState);
        }
    }
}
