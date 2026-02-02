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
        VipDefinition vip = config.getVipOrThrow(vipId);

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

    /** Aplica o VIP se não houver outro ativo (regra: 1 VIP por vez) */
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
        record.setUsedAt(now);
        record.setUsedBy(activatingPlayerUuid);
        vouchers.put(payload.getVoucherId(), record);
        vouchersStore.save(vouchers);

        return ActivationResult.activated(vip, newState);
    }

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
