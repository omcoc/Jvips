package br.com.julio.jvips.core.model;

/**
 * Registro histórico de um VIP que foi ativado (e possivelmente expirado/removido).
 * Serializado em players.json dentro de cada PlayerVipState.
 */
public final class VipHistoryEntry {

    private String vipId;
    private String vipDisplayName;
    private long activatedAt;   // epoch seconds
    private long expiresAt;     // epoch seconds
    private long endedAt;       // epoch seconds (quando realmente terminou: expiração ou remoção). 0 se ainda ativo.
    private String endReason;   // "expired", "admin_remove", ou null se ainda ativo

    public VipHistoryEntry() {}

    public VipHistoryEntry(String vipId, String vipDisplayName, long activatedAt, long expiresAt, long endedAt, String endReason) {
        this.vipId = vipId;
        this.vipDisplayName = vipDisplayName;
        this.activatedAt = activatedAt;
        this.expiresAt = expiresAt;
        this.endedAt = endedAt;
        this.endReason = endReason;
    }

    public String getVipId() { return vipId; }
    public void setVipId(String vipId) { this.vipId = vipId; }

    public String getVipDisplayName() { return vipDisplayName; }
    public void setVipDisplayName(String vipDisplayName) { this.vipDisplayName = vipDisplayName; }

    public long getActivatedAt() { return activatedAt; }
    public void setActivatedAt(long activatedAt) { this.activatedAt = activatedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }

    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
}
