package br.com.julio.jvips.core;

import br.com.julio.jvips.core.config.VipConfig;
import br.com.julio.jvips.core.model.PlayerVipState;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.model.VipHistoryEntry;
import br.com.julio.jvips.core.storage.PlayersStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VipExpiryService {

    private final VipConfig config;
    private final PlayersStore playersStore;

    public VipExpiryService(VipConfig config, PlayersStore playersStore) {
        this.config = config;
        this.playersStore = playersStore;
    }

    /**
     * Retorna o estado do jogador (players.json) ou null se não houver VIP ativo.
     */
    public PlayerVipState getPlayerState(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return null;
        Map<String, PlayerVipState> players = playersStore.load();
        return players.get(playerUuid);
    }

    /**
     * Retorna todos os jogadores do players.json (para listagem/admin).
     */
    public Map<String, PlayerVipState> getAllPlayers() {
        return playersStore.load();
    }

    /**
     * Varre players.json e retorna lembretes que devem ser enviados.
     *
     * Regras:
     * - Envia 1 lembrete por janela (7d, 1d, 1h), no máximo 1 por sweep por player.
     * - Persistimos um bitmask em PlayerVipState.remindersSent.
     */
    public List<VipReminder> collectReminders(long nowEpochSeconds) {
        // thresholds (em segundos)
        final long t7d = 7L * 24L * 60L * 60L;
        final long t1d = 1L * 24L * 60L * 60L;
        final long t1h = 1L * 60L * 60L;

        Map<String, PlayerVipState> players = playersStore.load();
        if (players.isEmpty()) return java.util.Collections.emptyList();

        Map<String, PlayerVipState> updated = new HashMap<>(players);
        List<VipReminder> out = new ArrayList<>();

        for (Map.Entry<String, PlayerVipState> entry : players.entrySet()) {
            String playerUuid = entry.getKey();
            PlayerVipState state = entry.getValue();
            if (state == null) continue;

            String vipId = state.getActiveVipId();
            if (vipId == null || vipId.isEmpty()) continue;

            long remaining = state.getExpiresAt() - nowEpochSeconds;
            if (remaining <= 0) continue; // já expirou (sweepExpired cuidará)

            int mask = state.getRemindersSent();

            // define qual janela se aplica (prioridade: mais próximo do expirar)
            // 1h: remaining <= 1h
            // 1d: remaining <= 1d && > 1h
            // 7d: remaining <= 7d && > 1d
            long windowSeconds = -1;
            int bit = 0;

            if (remaining <= t1h) {
                bit = 4;
                windowSeconds = t1h;
            } else if (remaining <= t1d) {
                bit = 2;
                windowSeconds = t1d;
            } else if (remaining <= t7d) {
                bit = 1;
                windowSeconds = t7d;
            }

            if (bit == 0) continue; // ainda não está em janela de lembrete
            if ((mask & bit) != 0) continue; // já enviou esse lembrete

            VipDefinition vipDef;
            try {
                vipDef = config.getVipOrThrow(vipId);
            } catch (Exception ignored) {
                vipDef = null;
            }

            // marca como enviado
            PlayerVipState newState = new PlayerVipState(
                    state.getActiveVipId(),
                    state.getActivatedAt(),
                    state.getExpiresAt(),
                    state.getLastKnownName(),
                    mask | bit
            );
            updated.put(playerUuid, newState);

            out.add(new VipReminder(
                    playerUuid,
                    vipId,
                    state.getExpiresAt(),
                    remaining,
                    windowSeconds,
                    vipDef
            ));
        }

        if (!out.isEmpty()) {
            playersStore.save(updated);
        }
        return out;
    }

    /**
     * Varre players.json e remove VIPs expirados.
     * Retorna uma lista de expirações para que a camada do servidor execute commandsOnExpire.
     */
    public List<ExpiredVip> sweepExpired() {
        long now = Instant.now().getEpochSecond();

        Map<String, PlayerVipState> players = playersStore.load();
        Map<String, PlayerVipState> updated = new HashMap<>(players);

        List<ExpiredVip> expired = new ArrayList<>();



        for (Map.Entry<String, PlayerVipState> entry : players.entrySet()) {
            String playerUuid = entry.getKey();
            PlayerVipState state = entry.getValue();
            if (state == null) continue;

            // se não tem VIP ativo, ignora
            String vipId = state.getActiveVipId();
            if (vipId == null || vipId.isEmpty()) continue;

            // se expirou
            if (state.getExpiresAt() <= now) {
                VipDefinition vipDef;
                try {
                    vipDef = config.getVipOrThrow(vipId);
                } catch (Exception ignored) {
                    // VIP removido do config? Ainda assim limpamos o estado.
                    vipDef = null;
                }

                expired.add(new ExpiredVip(
                        playerUuid,
                        vipId,
                        state.getActivatedAt(),
                        state.getExpiresAt(),
                        vipDef
                ));

                // Salvar no histórico antes de remover
                String vipDisplayName = (vipDef != null && vipDef.getDisplayName() != null)
                        ? vipDef.getDisplayName() : vipId;
                VipHistoryEntry histEntry = new VipHistoryEntry(
                        vipId, vipDisplayName,
                        state.getActivatedAt(), state.getExpiresAt(),
                        now, "expired"
                );
                state.addHistoryEntry(histEntry);

                // Limpa VIP ativo mas mantém o state (para preservar history)
                state.setActiveVipId(null);
                state.setActivatedAt(0);
                state.setExpiresAt(0);
                state.setRemindersSent(0);
                updated.put(playerUuid, state);
            }
        }

        // Só grava se teve alteração
        if (!expired.isEmpty()) {
            playersStore.save(updated);
        }

        return expired;
    }

    /**
     * Informação para a camada do servidor executar comandos de expiração.
     */
    public record ExpiredVip(
            String playerUuid,
            String vipId,
            long activatedAt,
            long expiresAt,
            VipDefinition vipDefinition // pode ser null se vipId não existe mais no config
    ) {}

    public record VipReminder(
            String playerUuid,
            String vipId,
            long expiresAt,
            long remainingSeconds,
            long windowSeconds,
            VipDefinition vipDefinition
    ) {}
}
