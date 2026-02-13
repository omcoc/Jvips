package br.com.julio.jvips.plugin.systems;

import br.com.julio.jvips.core.VipCommandService;
import br.com.julio.jvips.core.VipExpiryService;
import br.com.julio.jvips.core.util.DurationFormatter;
import br.com.julio.jvips.plugin.JvipsPlugin;
import br.com.julio.jvips.plugin.JvipsServices;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
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

        // ---------------------------------------------------------
        // Reminders (7d, 1d, 1h) - envia apenas se o jogador estiver online
        // ---------------------------------------------------------
        try {
            List<VipExpiryService.VipReminder> reminders = expiry.collectReminders(now);
            for (VipExpiryService.VipReminder r : reminders) {
                if (r.playerUuid() == null) continue;
                try {
                    PlayerRef ref = Universe.get().getPlayer(java.util.UUID.fromString(r.playerUuid()));
                    if (ref == null || !ref.isValid()) continue;

                    String vipDisplay = (r.vipDefinition() != null
                            && r.vipDefinition().getDisplayName() != null
                            && !r.vipDefinition().getDisplayName().isEmpty())
                            ? r.vipDefinition().getDisplayName()
                            : r.vipId();

                    String key;
                    if (r.windowSeconds() <= 3600L) {
                        key = "player.vipReminder.1h";
                    } else if (r.windowSeconds() <= 86400L) {
                        key = "player.vipReminder.1d";
                    } else {
                        key = "player.vipReminder.7d";
                    }

                    java.util.Map<String, String> vars = new java.util.HashMap<>();
                    vars.put("vipDisplay", vipDisplay);
                    vars.put("remaining", DurationFormatter.format(Math.max(0, r.remainingSeconds())));
                    ref.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format(key, vars)));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        List<VipExpiryService.ExpiredVip> expired = expiry.sweepExpired();
        if (expired.isEmpty()) return;

        for (VipExpiryService.ExpiredVip e : expired) {
            if (e.vipDefinition() == null) {
                System.out.println("[JVIPS] Expirou vipId=" + e.vipId() + " mas não existe mais no vips.json; pulando commandsOnExpire.");
                continue;
            }

            // Gravar expiração no history.json
            br.com.julio.jvips.plugin.util.HistoryRecorder.recordExpiration(
                    e.playerUuid(), e.vipId(), Instant.now().getEpochSecond()
            );

            // Aqui usamos UUID (players.json guarda UUID). LuckPerms costuma aceitar UUID no "lp user".
            String player = e.playerUuid();

            cmds.runExpireCommands(e.vipDefinition(), player)
                    .thenRun(() ->
                            System.out.println("[JVIPS] commandsOnExpire FINALIZADO vip="
                                    + e.vipId() + " player=" + player)
                    );

            System.out.println("[JVIPS] VIP expirou vip=" + e.vipId() + " player=" + player);

            // Se o jogador estiver online, avisa no chat (mensagens via JSON)
            try {
                PlayerRef ref = Universe.get().getPlayer(java.util.UUID.fromString(player));
                if (ref != null && ref.isValid()) {
                    String vipDisplay = (e.vipDefinition() != null
                            && e.vipDefinition().getDisplayName() != null
                            && !e.vipDefinition().getDisplayName().isEmpty())
                            ? e.vipDefinition().getDisplayName()
                            : e.vipId();

                    java.util.Map<String, String> vars = new java.util.HashMap<>();
                    vars.put("vipDisplay", vipDisplay);
                    ref.sendMessage(br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("player.vipExpired", vars)));
                }
            } catch (Throwable ignored) {
                // não falha o ticking caso a API mude
            }
        }
    }
}
