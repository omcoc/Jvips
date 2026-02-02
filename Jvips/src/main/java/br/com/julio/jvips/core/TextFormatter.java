package br.com.julio.jvips.core;

import br.com.julio.jvips.core.model.PlayerVipState;
import br.com.julio.jvips.core.model.VipDefinition;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class TextFormatter {

    private final ZoneId zoneId;
    private final DateTimeFormatter dateTimeFmt;

    public TextFormatter(ZoneId zoneId) {
        this.zoneId = zoneId;
        this.dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("pt", "BR"))
                .withZone(zoneId);
    }

    public String formatMessage(String template, Map<String, String> vars) {
        if (template == null) return "";
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", safe(e.getValue()));
            }
        }
        return out;
    }

    public String durationHuman(long durationSeconds) {
        if (durationSeconds <= 0) return "0s";

        long seconds = durationSeconds;

        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(days == 1 ? " dia" : " dias");
        if (hours > 0) appendPart(sb, hours + (hours == 1 ? " hora" : " horas"));
        if (minutes > 0) appendPart(sb, minutes + (minutes == 1 ? " minuto" : " minutos"));
        if (sb.length() == 0 && seconds > 0) sb.append(seconds).append(seconds == 1 ? " segundo" : " segundos");

        return sb.toString();
    }

    public String formatEpochSeconds(long epochSeconds) {
        return dateTimeFmt.format(Instant.ofEpochSecond(epochSeconds));
    }

    public String vipDisplayOrId(VipDefinition vip) {
        if (vip == null) return "";
        String dn = vip.getDisplayName();
        return (dn == null || dn.isEmpty()) ? vip.getId() : dn;
    }

    public String activeVipDisplay(PlayerVipState state, Map<String, VipDefinition> vipMap) {
        if (state == null || state.getActiveVipId() == null) return "";
        VipDefinition vip = vipMap.get(state.getActiveVipId());
        if (vip == null) return state.getActiveVipId();
        return vipDisplayOrId(vip);
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(part);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
