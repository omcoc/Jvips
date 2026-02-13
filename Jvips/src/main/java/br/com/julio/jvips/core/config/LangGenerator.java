package br.com.julio.jvips.core.config;

import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.util.DurationFormatter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Gera chaves de tradução dinâmicas no server.lang para cada VIP definido no vips.json.
 *
 * Para cada VIP, cria:
 *   items.Jvips_Voucher.<vipId>.name = [TMP] <nome do voucher>
 *   items.Jvips_Voucher.<vipId>.description = [TMP] <lore formatada com TMP tags>
 *
 * As chaves base do item (items.Jvips_Voucher.name/description) são preservadas como fallback.
 *
 * O VoucherItemFactory aponta o TranslationProperties do ItemStack para a chave do VIP,
 * e o Hytale renderiza o tooltip com o conteúdo do .lang via TextMesh Pro.
 */
public final class LangGenerator {

    /** Marcador para auto-geração — tudo entre JVIPS_START e JVIPS_END é regenerado */
    private static final String MARKER_START = "## JVIPS_AUTO_START ##";
    private static final String MARKER_END = "## JVIPS_AUTO_END ##";

    private LangGenerator() {}

    /**
     * Regenera as chaves de VIP no server.lang.
     * Preserva qualquer conteúdo manual fora dos marcadores.
     *
     * @param langPath  caminho do server.lang no disco
     * @param vips      mapa de VIPs do vips.json
     * @param log       consumer de log
     */
    public static void regenerate(Path langPath, Map<String, VipDefinition> vips, Consumer<String> log) {
        if (vips == null || vips.isEmpty()) return;

        try {
            Files.createDirectories(langPath.getParent());

            // Ler conteúdo existente
            List<String> existingLines = new ArrayList<>();
            if (Files.exists(langPath)) {
                existingLines = new ArrayList<>(Files.readAllLines(langPath, StandardCharsets.UTF_8));
            }

            // Separar: linhas manuais (fora dos marcadores) + gerar bloco novo
            List<String> manualLines = extractManualLines(existingLines);
            List<String> generatedLines = generateVipEntries(vips);

            // Montar arquivo final
            List<String> finalLines = new ArrayList<>();
            finalLines.addAll(manualLines);

            // Adicionar bloco gerado
            if (!generatedLines.isEmpty()) {
                finalLines.add("");
                finalLines.add(MARKER_START);
                finalLines.addAll(generatedLines);
                finalLines.add(MARKER_END);
            }

            Files.writeString(langPath, String.join("\n", finalLines) + "\n", StandardCharsets.UTF_8);
            log.accept("[Jvips] server.lang updated: " + generatedLines.size() + " translation entries for " + vips.size() + " VIPs");

        } catch (IOException e) {
            log.accept("[Jvips] Failed to update server.lang: " + e.getMessage());
        }
    }

    /**
     * Extrai linhas manuais (tudo fora do bloco JVIPS_AUTO).
     */
    private static List<String> extractManualLines(List<String> lines) {
        List<String> manual = new ArrayList<>();
        boolean inAutoBlock = false;

        for (String line : lines) {
            if (line.trim().equals(MARKER_START)) {
                inAutoBlock = true;
                continue;
            }
            if (line.trim().equals(MARKER_END)) {
                inAutoBlock = false;
                continue;
            }
            if (!inAutoBlock) {
                manual.add(line);
            }
        }

        // Remover linhas em branco trailing
        while (!manual.isEmpty() && manual.get(manual.size() - 1).trim().isEmpty()) {
            manual.remove(manual.size() - 1);
        }

        return manual;
    }

    /**
     * Gera as entradas .lang para cada VIP.
     */
    private static List<String> generateVipEntries(Map<String, VipDefinition> vips) {
        List<String> lines = new ArrayList<>();

        for (Map.Entry<String, VipDefinition> entry : vips.entrySet()) {
            String vipId = entry.getKey();
            VipDefinition vip = entry.getValue();

            String name = buildName(vip);
            String description = buildDescription(vip);

            lines.add("items.Jvips_Voucher." + vipId + ".name = [TMP] " + name);
            lines.add("items.Jvips_Voucher." + vipId + ".description = [TMP] " + description);
        }

        return lines;
    }

    /**
     * Constrói o nome do voucher para o .lang.
     * Usa o voucher.name limpo (sem placeholders runtime como {voucherIdShort}).
     */
    private static String buildName(VipDefinition vip) {
        String displayName = stripMarkup(vip.getDisplayName());
        if (displayName == null || displayName.isEmpty()) {
            displayName = vip.getId().toUpperCase();
        }
        return displayName + " VIP Voucher";
    }

    /**
     * Constrói a description do voucher para o .lang com formatação TMP.
     * A lore do vips.json é convertida para tags TMP.
     */
    private static String buildDescription(VipDefinition vip) {
        StringBuilder sb = new StringBuilder();

        String displayName = stripMarkup(vip.getDisplayName());
        if (displayName == null || displayName.isEmpty()) {
            displayName = vip.getId().toUpperCase();
        }

        String duration = DurationFormatter.format(vip.getDurationSeconds());

        // Header
        sb.append("To use your <color is=\"#FFD700\">").append(escLang(displayName)).append("</color> VIP Voucher:");
        sb.append("\\n\\n");

        // Instrução
        sb.append("<color is=\"#00ff00\"><b><u>Right Click.</u></b></color>");
        sb.append("\\n\\n");

        // Info
        sb.append("<color is=\"#ff0000\"><b>Information:</b></color>");
        sb.append("\\n\\n");

        // Lore lines do vips.json
        List<String> lore = (vip.getVoucher() != null) ? vip.getVoucher().getLore() : null;
        if (lore != null && !lore.isEmpty()) {
            for (String line : lore) {
                // Resolver placeholders estáticos (durationHuman)
                String resolved = line
                        .replace("{durationHuman}", duration)
                        .replace("{player}", "you"); // no .lang não temos o player name

                sb.append("• <color is=\"#ffffff\">").append(escLang(resolved)).append("</color>");
                sb.append("\\n");
            }
        } else {
            // Fallback se não tiver lore
            sb.append("• <color is=\"#ffffff\">VIP: ").append(escLang(displayName)).append("</color>");
            sb.append("\\n");
            sb.append("• <color is=\"#ffffff\">Duration: ").append(duration).append("</color>");
            sb.append("\\n");
        }

        sb.append("\\n");
        sb.append("VIP Type: <color is=\"#FFD700\">").append(escLang(displayName)).append("</color>");
        sb.append("\\n");
        sb.append("Duration: <color is=\"#ffffff\">").append(duration).append("</color>");
        sb.append("\\n\\n");
        sb.append("<color is=\"#aaaaaa\">Voucher attached to your UUID • Not dropable</color>");

        return sb.toString();
    }

    /**
     * Remove tags de markup do Hytale/MiniMessage do displayName.
     * Ex: "<#02421A><bold>[<gradient:...>THORIUM</gradient>]</bold>" → "[THORIUM]"
     */
    private static String stripMarkup(String text) {
        if (text == null) return null;
        // Remove todas as tags < ... >
        return text.replaceAll("<[^>]+>", "").trim();
    }

    /**
     * Escapa caracteres que podem quebrar o .lang
     */
    private static String escLang(String text) {
        if (text == null) return "";
        // Newlines literais → escape sequences
        return text.replace("\n", "\\n").replace("\r", "");
    }
}
