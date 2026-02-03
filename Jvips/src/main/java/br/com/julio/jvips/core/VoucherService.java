package br.com.julio.jvips.core;

import br.com.julio.jvips.core.config.VipConfig;
import br.com.julio.jvips.core.crypto.HmacSigner;
import br.com.julio.jvips.core.model.PlayerVipState;
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
        // valida vip existe
        config.getVipOrThrow(vipId);

        String voucherId = UUID.randomUUID().toString();
        long issuedAt = Instant.now().getEpochSecond();

        VoucherPayload payload = new VoucherPayload(
                voucherId,
                vipId,
                issuedToPlayerUuid,
                issuedAt
        );

        String signature = sign(payload);

        // registra voucher emitido (não usado)
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

    /** Aplica o VIP via voucher se não houver outro ativo (regra: 1 VIP por vez) */
    public ActivationResult activateVoucher(
            VoucherPayload payload,
            String activatingPlayerUuid,
            String activatingPlayerName
    ) {
        long now = Instant.now().getEpochSecond();

        // checa VIP ativo
        Map<String, PlayerVipState> players = playersStore.load();
        PlayerVipState state = players.get(activatingPlayerUuid);
        if (state != null && state.hasActiveVip(now)) {
            return ActivationResult.blockedAlreadyHasVip(state);
        }

        VipDefinition vip = config.getVipOrThrow(payload.getVipId());
        long expiresAt = now + vip.getDurationSeconds();

        // fallback seguro pro lastKnownName
        String lastKnownName = (activatingPlayerName == null || activatingPlayerName.trim().isEmpty())
                ? activatingPlayerUuid
                : activatingPlayerName.trim();

        PlayerVipState newState = new PlayerVipState(
                payload.getVipId(),
                now,
                expiresAt,
                lastKnownName
        );

        // salva estado do jogador
        players = new HashMap<>(players);
        players.put(activatingPlayerUuid, newState);
        playersStore.save(players);

        // marca voucher como usado
        Map<String, VoucherRecord> vouchers = vouchersStore.load();
        VoucherRecord record = vouchers.get(payload.getVoucherId());
        if (record != null) {
            record.setUsedAt(now);
            record.setUsedBy(activatingPlayerUuid);
            vouchers.put(payload.getVoucherId(), record);
            vouchersStore.save(vouchers);
        }

        return ActivationResult.activated(vip, newState);
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
        return adminAddVip(vipId, targetPlayerUuid, targetPlayerUuid);
    }

    /**
     * Versão com nome (pra preencher lastKnownName corretamente).
     */
    public ActivationResult adminAddVip(String vipId, String targetPlayerUuid, String targetPlayerName) {
        long now = Instant.now().getEpochSecond();

        // valida vip existe
        VipDefinition vip = config.getVipOrThrow(vipId);

        Map<String, PlayerVipState> players = playersStore.load();
        PlayerVipState current = players.get(targetPlayerUuid);

        // se já tem VIP ativo, bloqueia
        if (current != null && current.hasActiveVip(now)) {
            return ActivationResult.blockedAlreadyHasVip(current);
        }

        long expiresAt = now + vip.getDurationSeconds();

        String lastKnownName = (targetPlayerName == null || targetPlayerName.trim().isEmpty())
                ? targetPlayerUuid
                : targetPlayerName.trim();

        PlayerVipState newState = new PlayerVipState(
                vip.getId(),
                now,
                expiresAt,
                lastKnownName
        );

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

        // remove o estado inteiro (1 vip por vez)
        players = new HashMap<>(players);
        players.remove(targetPlayerUuid);
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
            VipDefinition vip,
            PlayerVipState previousState,
            PlayerVipState newState
    ) {
        public static ActivationResult blockedAlreadyHasVip(PlayerVipState state) {
            return new ActivationResult(false, true, null, state, null);
        }

        public static ActivationResult activated(VipDefinition vip, PlayerVipState newState) {
            return new ActivationResult(true, false, vip, null, newState);
        }
    }
}
