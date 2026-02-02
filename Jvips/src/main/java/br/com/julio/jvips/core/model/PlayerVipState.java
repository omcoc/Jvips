package br.com.julio.jvips.core.model;

public final class PlayerVipState {
    private String activeVipId;     // ex: "thorium"
    private long activatedAt;       // epoch seconds
    private long expiresAt;         // epoch seconds
    private String lastKnownName;   // nome no momento da ativação (fallback)

    public PlayerVipState() {}

    public PlayerVipState(String activeVipId, long activatedAt, long expiresAt, String lastKnownName) {
        this.activeVipId = activeVipId;
        this.activatedAt = activatedAt;
        this.expiresAt = expiresAt;
        this.lastKnownName = lastKnownName;
    }

    public String getActiveVipId() { return activeVipId; }
    public void setActiveVipId(String activeVipId) { this.activeVipId = activeVipId; }

    public long getActivatedAt() { return activatedAt; }
    public void setActivatedAt(long activatedAt) { this.activatedAt = activatedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public String getLastKnownName() { return lastKnownName; }
    public void setLastKnownName(String lastKnownName) { this.lastKnownName = lastKnownName; }

    public boolean hasActiveVip(long nowEpochSeconds) {
        return activeVipId != null && !activeVipId.isEmpty() && expiresAt > nowEpochSeconds;
    }
}
