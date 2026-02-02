package br.com.julio.jvips.core.model;

public final class VoucherRecord {
    private String vipId;
    private String issuedTo; // player UUID (string)
    private long issuedAt;

    private Long usedAt;     // null se não usado
    private String usedBy;   // player UUID

    public VoucherRecord() {}

    public VoucherRecord(String vipId, String issuedTo, long issuedAt) {
        this.vipId = vipId;
        this.issuedTo = issuedTo;
        this.issuedAt = issuedAt;
    }

    public String getVipId() { return vipId; }
    public void setVipId(String vipId) { this.vipId = vipId; }

    public String getIssuedTo() { return issuedTo; }
    public void setIssuedTo(String issuedTo) { this.issuedTo = issuedTo; }

    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }

    public Long getUsedAt() { return usedAt; }
    public void setUsedAt(Long usedAt) { this.usedAt = usedAt; }

    public String getUsedBy() { return usedBy; }
    public void setUsedBy(String usedBy) { this.usedBy = usedBy; }

    public boolean isUsed() {
        return usedAt != null && usedAt > 0;
    }
}
