package br.com.julio.jvips.core;

import br.com.julio.jvips.core.config.VipConfig;
import br.com.julio.jvips.core.model.PlayerVipState;
import br.com.julio.jvips.core.model.VipDefinition;
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

                // remove o estado do player (regra 1 VIP por vez)
                updated.remove(playerUuid);
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
}
