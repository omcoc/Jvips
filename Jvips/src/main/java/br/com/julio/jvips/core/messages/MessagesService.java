package br.com.julio.jvips.core.messages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MessagesService {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private Map<String, String> primary = Collections.emptyMap();
    private Map<String, String> fallback = Collections.emptyMap();

    /** Load only one language file (no fallback). */
    public void load(Path messagesFile) {
        this.primary = readFile(messagesFile);
        this.fallback = Collections.emptyMap();
    }

    /** Load primary + fallback (e.g., pt_BR + en_US). */
    public void load(Path primaryFile, Path fallbackFile) {
        this.primary = readFile(primaryFile);
        this.fallback = readFile(fallbackFile);
    }

    private Map<String, String> readFile(Path file) {
        try {
            if (file == null || !Files.exists(file)) return Collections.emptyMap();

            String json = Files.readString(file);
            Map<String, String> parsed = GSON.fromJson(json, MAP_TYPE);

            if (parsed == null || parsed.isEmpty()) return Collections.emptyMap();

            return new HashMap<>(parsed); // defensive copy
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    /** Get raw message (no placeholder formatting). */
    public String get(String key) {
        String v = primary.get(key);
        if (v != null) return v;

        v = fallback.get(key);
        if (v != null) return v;

        return key; // last resort: show key
    }

    /** Compatibility helper: get(key, vars) == format(key, vars). */
    public String get(String key, Map<String, String> vars) {
        return format(key, vars);
    }

    /** Replace placeholders like {vipId}. Always injects {prefix}. */
    public String format(String key, Map<String, String> vars) {
        String base = get(key);

        String prefix = primary.containsKey("prefix")
                ? primary.get("prefix")
                : fallback.getOrDefault("prefix", "");

        Map<String, String> all = new HashMap<>();
        all.put("prefix", safe(prefix));

        if (vars != null && !vars.isEmpty()) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                all.put(e.getKey(), safe(e.getValue()));
            }
        }

        for (Map.Entry<String, String> e : all.entrySet()) {
            base = base.replace("{" + e.getKey() + "}", e.getValue());
        }

        // Opcional: se o JSON tiver códigos legados (&a, &l, &r, etc.), converte para o markup do Hytale
        // (formato <#RRGGBB> e tags <b><i><u>). Se você não usar & no JSON, nada é alterado.
        return translateLegacyFormatting(base);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Converte códigos legados (&0-9a-f, &l, &r) para o markup do Hytale.
     * Mantém qualquer markup já existente (ex.: <#00ff00> ... </#00ff00>). 
     */
    private static String translateLegacyFormatting(String s) {
        if (s == null || s.indexOf('&') < 0) return s;

        // Mapeamento básico (padrão Minecraft) -> hex
        // (você pode ajustar aqui se quiser outra paleta)
        java.util.Map<Character, String> hex = new java.util.HashMap<>();
        hex.put('0', "000000");
        hex.put('1', "0000aa");
        hex.put('2', "00aa00");
        hex.put('3', "00aaaa");
        hex.put('4', "aa0000");
        hex.put('5', "aa00aa");
        hex.put('6', "ffaa00");
        hex.put('7', "aaaaaa");
        hex.put('8', "555555");
        hex.put('9', "5555ff");
        hex.put('a', "55ff55");
        hex.put('b', "55ffff");
        hex.put('c', "ff5555");
        hex.put('d', "ff55ff");
        hex.put('e', "ffff55");
        hex.put('f', "ffffff");

        StringBuilder out = new StringBuilder(s.length() + 32);
        String openColor = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));

                if (hex.containsKey(code)) {
                    // fecha cor anterior se existir
                    if (openColor != null) {
                        out.append("</#").append(openColor).append('>');
                        openColor = null;
                    }
                    openColor = hex.get(code);
                    out.append("<#").append(openColor).append(">");
                    i++; // consome o código
                    continue;
                }

                if (code == 'l') { // bold
                    if (!bold) {
                        out.append("<b>");
                        bold = true;
                    }
                    i++;
                    continue;
                }
                if (code == 'o') { // italic
                    if (!italic) {
                        out.append("<i>");
                        italic = true;
                    }
                    i++;
                    continue;
                }

                if (code == 'n') { // underline
                    if (!underline) {
                        out.append("<u>");
                        underline = true;
                    }
                    i++;
                    continue;
                }



                if (code == 'r') { // reset
                    if (bold) {
                        out.append("</b>");
                        bold = false;
                    }
                    if (italic) {
                        out.append("</i>");
                        italic = false;
                    }
                    if (underline) {
                        out.append("</u>");
                        underline = false;
                    }

                    if (openColor != null) {
                        out.append("</#").append(openColor).append('>');
                        openColor = null;
                    }
                    i++;

                    continue;
                }
            }

            out.append(ch);
        }

        // fecha tags abertas para evitar markup quebrado
        if (bold) out.append("</b>");
        if (italic) out.append("</i>");
        if (underline) out.append("</u>");

        if (openColor != null) out.append("</#").append(openColor).append('>');

        return out.toString();
    }
}
