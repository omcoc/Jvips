package br.com.julio.jvips.core.storage;

import br.com.julio.jvips.core.model.VipHistoryEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Wrapper/facade para o HistoryStore, oferecendo métodos
 * mais convenientes para o uso nos serviços e comandos.
 */
public final class VipHistoryStore {

    private final HistoryStore store;

    public VipHistoryStore(Path filePath) {
        this.store = new HistoryStore(filePath);
    }

    /**
     * Carrega todo o mapa de histórico.
     */
    public Map<String, List<VipHistoryEntry>> loadAll() {
        return store.load();
    }

    /**
     * Salva todo o mapa de histórico.
     */
    public void saveAll(Map<String, List<VipHistoryEntry>> history) {
        store.save(history);
    }

    /**
     * Retorna o histórico de um jogador.
     */
    public List<VipHistoryEntry> getPlayerHistory(String playerUuid) {
        return store.getHistory(playerUuid);
    }

    /**
     * Adiciona uma nova entrada ao histórico de um jogador.
     */
    public void addEntry(String playerUuid, VipHistoryEntry entry) {
        store.addEntry(playerUuid, entry);
    }

    /**
     * Registra o início de um novo VIP (endedAt = 0, sem reason).
     */
    public void recordActivation(String playerUuid, String vipId, String vipDisplayName,
                                  long activatedAt, long expiresAt) {
        VipHistoryEntry entry = new VipHistoryEntry(
                vipId, vipDisplayName, activatedAt, expiresAt, 0, null
        );
        store.addEntry(playerUuid, entry);
    }

    /**
     * Finaliza uma entrada ativa (endedAt == 0) para o jogador/vipId.
     * Usa setEndReason (campo correto no VipHistoryEntry).
     */
    public void finalizeEntry(String playerUuid, String vipId, long endedAtEpoch, String reason) {
        Map<String, List<VipHistoryEntry>> all = store.load();
        List<VipHistoryEntry> list = all.get(playerUuid);
        if (list == null) return;

        for (VipHistoryEntry e : list) {
            // endedAt é long (primitivo): 0 = ainda ativo
            if (e != null && vipId.equalsIgnoreCase(e.getVipId()) && e.getEndedAt() == 0) {
                e.setEndedAt(endedAtEpoch);
                e.setEndReason(reason);  // CORRETO: setEndReason, não setReason
                break;
            }
        }
        store.save(all);
    }
}
