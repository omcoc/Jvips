package br.com.julio.jvips.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado persistido do VIP do jogador.
 *
 * Observação: este objeto é serializado em JSON (players.json).
 */
public final class PlayerVipState {
    private String activeVipId;     // ex: "thorium"
    private long activatedAt;       // epoch seconds
    private long expiresAt;         // epoch seconds
    private String lastKnownName;   // nome no momento da ativação (fallback)

    /** Bitmask de lembretes já enviados (7d=1, 1d=2, 1h=4). */
    private int remindersSent;

    /** Quantas vezes o VIP foi estendido via stacking (0 = ativação inicial). */
    private int stackCount;

    /** Histórico de VIPs anteriores (ativados e expirados/removidos). */
    private List<VipHistoryEntry> history;

    public PlayerVipState() {
        this.remindersSent = 0;
        this.history = new ArrayList<>();
    }

    public PlayerVipState(String activeVipId, long activatedAt, long expiresAt, String lastKnownName) {
        this(activeVipId, activatedAt, expiresAt, lastKnownName, 0);
    }

    public PlayerVipState(String activeVipId, long activatedAt, long expiresAt, String lastKnownName, int remindersSent) {
        this.activeVipId = activeVipId;
        this.activatedAt = activatedAt;
        this.expiresAt = expiresAt;
        this.lastKnownName = lastKnownName;
        this.remindersSent = remindersSent;
        this.history = new ArrayList<>();
    }

    public String getActiveVipId() { return activeVipId; }
    public void setActiveVipId(String activeVipId) { this.activeVipId = activeVipId; }

    public long getActivatedAt() { return activatedAt; }
    public void setActivatedAt(long activatedAt) { this.activatedAt = activatedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public String getLastKnownName() { return lastKnownName; }
    public void setLastKnownName(String lastKnownName) { this.lastKnownName = lastKnownName; }

    public int getRemindersSent() { return remindersSent; }
    public void setRemindersSent(int remindersSent) { this.remindersSent = remindersSent; }

    public int getStackCount() { return stackCount; }
    public void setStackCount(int stackCount) { this.stackCount = stackCount; }

    public boolean hasActiveVip(long nowEpochSeconds) {
        return activeVipId != null && !activeVipId.isEmpty() && expiresAt > nowEpochSeconds;
    }

    public List<VipHistoryEntry> getHistory() {
        if (history == null) history = new ArrayList<>();
        return history;
    }

    public void setHistory(List<VipHistoryEntry> history) {
        this.history = (history != null) ? history : new ArrayList<>();
    }

    /**
     * Adiciona uma entrada ao histórico.
     */
    public void addHistoryEntry(VipHistoryEntry entry) {
        if (entry == null) return;
        if (this.history == null) this.history = new ArrayList<>();
        this.history.add(entry);
    }
}
