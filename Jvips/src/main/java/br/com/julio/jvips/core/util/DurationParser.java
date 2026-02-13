package br.com.julio.jvips.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte strings de duração humanas para segundos.
 *
 * Formatos aceitos:
 *   "1d 2h 10m 5s"  → 93605
 *   "2h 5s"          → 7205
 *   "30d"            → 2592000
 *   "1d2h10m5s"      → 93605 (sem espaços também funciona)
 *
 * Unidades: d (dias), h (horas), m (minutos), s (segundos)
 */
public final class DurationParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {}

    /**
     * Faz parse de uma string de duração e retorna o total em segundos.
     *
     * @param input string como "1d 2h 10m 5s"
     * @return total em segundos, ou -1 se nenhum token válido encontrado
     */
    public static long parse(String input) {
        if (input == null || input.trim().isEmpty()) return -1;

        Matcher matcher = DURATION_PATTERN.matcher(input.trim());

        long totalSeconds = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            switch (unit) {
                case "d" -> totalSeconds += value * 86400;
                case "h" -> totalSeconds += value * 3600;
                case "m" -> totalSeconds += value * 60;
                case "s" -> totalSeconds += value;
            }
        }

        return found && totalSeconds > 0 ? totalSeconds : -1;
    }

    /**
     * Verifica se a string contém tokens de duração válidos.
     */
    public static boolean isDurationString(String input) {
        if (input == null || input.trim().isEmpty()) return false;
        return DURATION_PATTERN.matcher(input.trim()).find();
    }
}
