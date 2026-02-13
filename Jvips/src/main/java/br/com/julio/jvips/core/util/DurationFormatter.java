package br.com.julio.jvips.core.util;

import java.util.concurrent.TimeUnit;

public final class DurationFormatter {

    private DurationFormatter() {
        // util class
    }

    /**
     * Converte segundos em texto humano.
     * Exemplos:
     *  - 10        -> 10s
     *  - 65        -> 1m 5s
     *  - 3600      -> 1h
     *  - 90061     -> 1d 1h 1m 1s
     */
    public static String format(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0s";
        }

        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        totalSeconds -= TimeUnit.DAYS.toSeconds(days);

        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        totalSeconds -= TimeUnit.HOURS.toSeconds(hours);

        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds);
        totalSeconds -= TimeUnit.MINUTES.toSeconds(minutes);

        long seconds = totalSeconds;

        StringBuilder sb = new StringBuilder();

        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
