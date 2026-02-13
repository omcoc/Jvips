package br.com.julio.jvips.plugin.util;

import br.com.julio.jvips.core.config.PluginConfig;
import br.com.julio.jvips.plugin.JvipsPlugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class VipBroadcastManager {

    private VipBroadcastManager() {}

    /**
     * Fonte da ativação, para evitar broadcast em ações administrativas.
     */
    public enum ActivationSource {
        VOUCHER,
        ADMIN
    }

    private static final Object LOCK = new Object();
    private static long nextAllowedEpochSeconds = 0L;

    // Acúmulo durante o cooldown global (para broadcast agrupado)
    private static int pendingCount = 0;
    private static String pendingLastPlayer = null;
    private static String pendingSecondLastPlayer = null;
    private static String pendingLastVipDisplay = null;
    private static String pendingLastVipPlain = null;

    public static void tryBroadcastVipActivated(
            JvipsPlugin plugin,
            ActivationSource source,
            String playerName,
            String vipDisplay
    ) {
        if (plugin == null) return;

        PluginConfig cfg = plugin.getPluginConfig();
        PluginConfig.VipBroadcast bc = (cfg != null) ? cfg.getVipBroadcast() : new PluginConfig.VipBroadcast();

        if (!bc.isEnabled()) return;
        if (source != ActivationSource.VOUCHER) return;

        long now = Instant.now().getEpochSecond();

        // Decide se vai disparar agora ou acumular
        final long cd;
        synchronized (LOCK) {
            long configured = bc.getCooldownSeconds();
            cd = configured < 0 ? 0 : configured;

            if (now < nextAllowedEpochSeconds) {
                // cooldown global ativo -> acumula
                pendingCount++;
                pendingSecondLastPlayer = pendingLastPlayer;
                pendingLastPlayer = playerName;
                pendingLastVipDisplay = vipDisplay;
                pendingLastVipPlain = br.com.julio.jvips.core.text.JvipsTextParser.stripMarkup(vipDisplay);
                return;
            }

            // cooldown liberado -> vamos disparar e reiniciar janela
            nextAllowedEpochSeconds = now + cd;
        }

        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("player", playerName == null ? "" : playerName);
            vars.put("vipDisplay", vipDisplay == null ? "" : vipDisplay);

                        String vipPlain = br.com.julio.jvips.core.text.JvipsTextParser.stripMarkup(vipDisplay);
            vars.put("vipPlain", vipPlain);
// Se houve ativações durante o cooldown, faz broadcast agrupado.
            int total;
            String secondLast;
            synchronized (LOCK) {
                total = pendingCount + 1;
                // o "segundo último" é o último que aconteceu DURANTE o cooldown
                secondLast = pendingLastPlayer;
                // reseta o acumulador após decidir
                pendingCount = 0;
                pendingLastPlayer = null;
                pendingSecondLastPlayer = null;
                pendingLastVipDisplay = null;
                pendingLastVipPlain = null;
            }

            // Mega (>=5) reforça prova social / FOMO
            if (total >= 5) {
                vars.put("count", String.valueOf(total));
                // últimos 2 nomes (quando existir)
                vars.put("player1", vars.get("player"));
                vars.put("player2", secondLast == null ? "" : secondLast);

                Message title = br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("broadcast.vipActivated.mega.title", vars));
                Message subtitle = br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("broadcast.vipActivated.mega.subtitle", vars));

                EventTitleUtil.showEventTitleToUniverse(
                        title,
                        subtitle,
                        true,
                        EventTitleUtil.DEFAULT_ZONE,
                        EventTitleUtil.DEFAULT_FADE_DURATION,
                        EventTitleUtil.DEFAULT_DURATION,
                        EventTitleUtil.DEFAULT_FADE_DURATION
                );
                return;
            }

            if (total >= 2) {
                vars.put("count", String.valueOf(total));
                Message title = br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("broadcast.vipActivated.multi.title", vars));
                Message subtitle = br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("broadcast.vipActivated.multi.subtitle", vars));

                EventTitleUtil.showEventTitleToUniverse(
                        title,
                        subtitle,
                        true,
                        EventTitleUtil.DEFAULT_ZONE,
                        EventTitleUtil.DEFAULT_FADE_DURATION,
                        EventTitleUtil.DEFAULT_DURATION,
                        EventTitleUtil.DEFAULT_FADE_DURATION
                );
                return;
            }

            Message title = br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("broadcast.vipActivated.title", vars));
            Message subtitle = br.com.julio.jvips.core.text.JvipsTextParser.parseToMessage(plugin.getMessages().format("broadcast.vipActivated.subtitle", vars));

            EventTitleUtil.showEventTitleToUniverse(
                    title,
                    subtitle,
                    true,
                    EventTitleUtil.DEFAULT_ZONE,
                    EventTitleUtil.DEFAULT_FADE_DURATION,
                    EventTitleUtil.DEFAULT_DURATION,
                    EventTitleUtil.DEFAULT_FADE_DURATION
            );
        } catch (Throwable ignored) {
            // Não derruba ativação se a API mudar
        }
    }
}
