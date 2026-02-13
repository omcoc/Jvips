package br.com.julio.jvips.core.config;


public final class PluginConfig {

    private String language = "pt_BR";
    private VipExpiry vipExpiry = new VipExpiry();
    private VipBroadcast vipBroadcast = new VipBroadcast();
    private Logging logging = new Logging();
    private Formatting formatting = new Formatting();
    private ListSettings listSettings = new ListSettings();

    // ===== Getters null-safe =====
    public String getLanguage() {
        return (language == null || language.trim().isEmpty()) ? "pt_BR" : language.trim();
    }

    public VipExpiry getVipExpiry() {
        return (vipExpiry != null) ? vipExpiry : new VipExpiry();
    }

    public VipBroadcast getVipBroadcast() {
        return (vipBroadcast != null) ? vipBroadcast : new VipBroadcast();
    }

    public Logging getLogging() {
        return (logging != null) ? logging : new Logging();
    }

    public Formatting getFormatting() {
        return (formatting != null) ? formatting : new Formatting();
    }

    public ListSettings getListSettings() {
        return (listSettings != null) ? listSettings : new ListSettings();
    }

    // ===== Normalização pós-load =====
    public PluginConfig normalize() {
        if (language == null || language.trim().isEmpty()) {
            language = "pt_BR";
        } else {
            language = language.trim();
        }

        if (vipExpiry == null) vipExpiry = new VipExpiry();
        if (vipBroadcast == null) vipBroadcast = new VipBroadcast();
        if (logging == null) logging = new Logging();
        if (formatting == null) formatting = new Formatting();
        if (listSettings == null) listSettings = new ListSettings();

        // limites seguros
        if (vipExpiry.sweepEverySeconds < 1L) vipExpiry.sweepEverySeconds = 1L;
        if (vipExpiry.sweepEverySeconds > 3600L) vipExpiry.sweepEverySeconds = 3600L;

        if (vipBroadcast.cooldownSeconds < 0L) vipBroadcast.cooldownSeconds = 0L;
        if (vipBroadcast.cooldownSeconds > 3600L) vipBroadcast.cooldownSeconds = 3600L;

        return this;
    }

    // ===== Inner classes =====
    public static final class VipExpiry {
        private long sweepEverySeconds = 10L;

        public long getSweepEverySeconds() {
            return sweepEverySeconds;
        }
    }

    public static final class VipBroadcast {
        private boolean enabled = true;
        private long cooldownSeconds = 30L;

        public boolean isEnabled() {
            return enabled;
        }

        public long getCooldownSeconds() {
            return cooldownSeconds;
        }
    }

    public static final class Logging {
        private boolean debug = false;

        public boolean isDebug() {
            return debug;
        }
    }

    public static final class Formatting {
        private String dateFormat = "dd/MM/yyyy"; // ou "MM/dd/yyyy"
        private String hourFormat = "24h";         // ou "12h"
        private String timezone = "America/Sao_Paulo";

        public String getDateFormat() {
            return (dateFormat != null && !dateFormat.isBlank()) ? dateFormat.trim() : "dd/MM/yyyy";
        }

        public String getHourFormat() {
            return (hourFormat != null && !hourFormat.isBlank()) ? hourFormat.trim() : "24h";
        }

        public String getTimezone() {
            return (timezone != null && !timezone.isBlank()) ? timezone.trim() : "America/Sao_Paulo";
        }
    }

    public static final class ListSettings {
        private int entriesPerPage = 5;

        public int getEntriesPerPage() {
            return (entriesPerPage < 1) ? 5 : Math.min(entriesPerPage, 20);
        }
    }
}
