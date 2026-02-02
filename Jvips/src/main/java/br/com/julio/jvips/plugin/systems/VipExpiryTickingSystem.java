package br.com.julio.jvips.plugin.systems;

import br.com.julio.jvips.core.VipCommandService;
import br.com.julio.jvips.core.VipExpiryService;
import br.com.julio.jvips.plugin.JvipsPlugin;
import br.com.julio.jvips.plugin.JvipsServices;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;

public final class VipExpiryTickingSystem extends TickingSystem<EntityStore> {

    // varre a cada 10s (seguro e leve)
    private static final long SWEEP_EVERY_SECONDS = 10L;
    private long nextSweepAt = 0L;

    @Override
    public void tick(float dt, int tick, @Nonnull Store<EntityStore> store) {
        long now = Instant.now().getEpochSecond();
        if (now < nextSweepAt) return;
        nextSweepAt = now + SWEEP_EVERY_SECONDS;

        JvipsPlugin plugin = JvipsServices.getPlugin();
        if (plugin == null || plugin.getCore() == null) return;

        VipExpiryService expiry = plugin.getCore().getExpiryService();
        VipCommandService cmds = plugin.getCore().getCommandService();
        if (expiry == null || cmds == null) return;

        List<VipExpiryService.ExpiredVip> expired = expiry.sweepExpired();
        if (expired.isEmpty()) return;

        for (VipExpiryService.ExpiredVip e : expired) {
            if (e.vipDefinition() == null) {
                System.out.println("[JVIPS] Expirou vipId=" + e.vipId() + " mas não existe mais no vips.json; pulando commandsOnExpire.");
                continue;
            }

            // Aqui usamos UUID (players.json guarda UUID). LuckPerms costuma aceitar UUID no "lp user".
            String player = e.playerUuid();

            cmds.runExpireCommands(e.vipDefinition(), player)
                    .thenRun(() ->
                            System.out.println("[JVIPS] commandsOnExpire FINALIZADO vip="
                                    + e.vipId() + " player=" + player)
                    );

            System.out.println("[JVIPS] VIP expirou vip=" + e.vipId() + " player=" + player);
        }
    }
}
