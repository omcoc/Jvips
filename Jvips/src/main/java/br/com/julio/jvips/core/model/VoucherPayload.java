package br.com.julio.jvips.core.model;

public final class VoucherPayload {
    private String voucherId; // UUID
    private String vipId;
    private String issuedTo;   // player UUID (string)
    private long issuedAt;     // epoch seconds
    private long customDurationSeconds; // 0 = usar padrão do vips.json

    public VoucherPayload() {}

    public VoucherPayload(String voucherId, String vipId, String issuedTo, long issuedAt) {
        this(voucherId, vipId, issuedTo, issuedAt, 0);
    }

    public VoucherPayload(String voucherId, String vipId, String issuedTo, long issuedAt, long customDurationSeconds) {
        this.voucherId = voucherId;
        this.vipId = vipId;
        this.issuedTo = issuedTo;
        this.issuedAt = issuedAt;
        this.customDurationSeconds = customDurationSeconds;
    }

    public String getVoucherId() { return voucherId; }
    public String getVipId() { return vipId; }
    public String getIssuedTo() { return issuedTo; }
    public long getIssuedAt() { return issuedAt; }
    public long getCustomDurationSeconds() { return customDurationSeconds; }

    /** Retorna duração efetiva: custom se > 0, senão o padrão fornecido. */
    public long getEffectiveDuration(long defaultDuration) {
        return customDurationSeconds > 0 ? customDurationSeconds : defaultDuration;
    }

    /** Payload canônico para assinatura */
    public String toSignableString() {
        return voucherId + "|" + vipId + "|" + issuedTo + "|" + issuedAt + "|" + customDurationSeconds;
    }
}
