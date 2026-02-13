package br.com.julio.jvips.plugin.util;

import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.model.VipHistoryEntry;
import br.com.julio.jvips.core.storage.HistoryStore;
import br.com.julio.jvips.plugin.JvipsServices;

/**
 * Utilitário para registrar eventos no history.json.
 * Usado quando VIPs são ativados, expirados ou removidos.
 */
public final class HistoryRecorder {

    private HistoryRecorder() {}

    /**
     * Registra a ativação de um VIP no history.json.
     */
    public static void recordActivation(String playerUuid, String vipId,
                                         String vipDisplayName, long activatedAt, long expiresAt) {
        HistoryStore store = getStore();
        if (store == null) return;

        VipHistoryEntry entry = new VipHistoryEntry(
                vipId, vipDisplayName, activatedAt, expiresAt, 0, null
        );
        store.addEntry(playerUuid, entry);
    }

    /**
     * Registra a ativação de um VIP usando o VipDefinition.
     */
    public static void recordActivation(String playerUuid, VipDefinition vipDef,
                                         long activatedAt, long expiresAt) {
        String displayName = (vipDef.getDisplayName() != null && !vipDef.getDisplayName().isEmpty())
                ? vipDef.getDisplayName() : vipDef.getId();
        recordActivation(playerUuid, vipDef.getId(), displayName, activatedAt, expiresAt);
    }

    /**
     * Finaliza uma entrada no histórico quando o VIP expira.
     */
    public static void recordExpiration(String playerUuid, String vipId, long endedAt) {
        HistoryStore store = getStore();
        if (store == null) return;
        store.finalizeEntry(playerUuid, vipId, endedAt, "expired");
    }

    /**
     * Finaliza uma entrada no histórico quando o VIP é removido por admin.
     */
    public static void recordAdminRemoval(String playerUuid, String vipId, long endedAt) {
        HistoryStore store = getStore();
        if (store == null) return;
        store.finalizeEntry(playerUuid, vipId, endedAt, "admin_remove");
    }

    private static HistoryStore getStore() {
        try {
            return JvipsServices.getPlugin().getCore().getHistoryStore();
        } catch (Exception e) {
            return null;
        }
    }
}
