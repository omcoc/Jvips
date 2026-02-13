package br.com.julio.jvips.core.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formatador de data/hora configurável via config.json.
 *
 * Configurações:
 *   dateFormat: "dd/MM/yyyy" (padrão) ou "MM/dd/yyyy"
 *   hourFormat: "24h" (padrão) ou "12h"
 *   timezone:   "America/Sao_Paulo" (padrão) ou qualquer timezone válida
 */
public final class DateTimeFormatUtil {

    private DateTimeFormatUtil() {}

    /**
     * Formata epoch seconds para "HH:mm - dd/MM/yyyy" (ou variante configurada).
     */
    public static String format(long epochSeconds, String dateFormat, String hourFormat, String timezone) {
        if (epochSeconds <= 0) return "—";

        ZoneId zone;
        try {
            zone = ZoneId.of(timezone != null && !timezone.isBlank() ? timezone : "America/Sao_Paulo");
        } catch (Exception e) {
            zone = ZoneId.of("America/Sao_Paulo");
        }

        ZonedDateTime zdt = Instant.ofEpochSecond(epochSeconds).atZone(zone);

        String hourPart;
        if ("12h".equalsIgnoreCase(hourFormat != null ? hourFormat.trim() : "24h")) {
            hourPart = zdt.format(DateTimeFormatter.ofPattern("hh:mm a"));
        } else {
            hourPart = zdt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        String datePart;
        if ("MM/dd/yyyy".equalsIgnoreCase(dateFormat != null ? dateFormat.trim() : "dd/MM/yyyy")) {
            datePart = zdt.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } else {
            datePart = zdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }

        return hourPart + " - " + datePart;
    }
}
