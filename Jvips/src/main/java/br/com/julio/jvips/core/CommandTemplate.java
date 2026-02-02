package br.com.julio.jvips.core;

import java.util.Map;

public final class CommandTemplate {

    private CommandTemplate() {}

    public static String apply(String template, Map<String, String> vars) {
        if (template == null) return "";
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out.trim();
    }
}
