package br.com.julio.jvips.core.model;

public final class VoucherPayload {
    private String voucherId; // UUID
    private String vipId;
    private String issuedTo;   // player UUID (string)
    private long issuedAt;     // epoch seconds

    public VoucherPayload() {}

    public VoucherPayload(String voucherId, String vipId, String issuedTo, long issuedAt) {
        this.voucherId = voucherId;
        this.vipId = vipId;
        this.issuedTo = issuedTo;
        this.issuedAt = issuedAt;
    }

    public String getVoucherId() { return voucherId; }
    public String getVipId() { return vipId; }
    public String getIssuedTo() { return issuedTo; }
    public long getIssuedAt() { return issuedAt; }

    /** Payload canônico para assinatura */
    public String toSignableString() {
        return voucherId + "|" + vipId + "|" + issuedTo + "|" + issuedAt;
    }
}
