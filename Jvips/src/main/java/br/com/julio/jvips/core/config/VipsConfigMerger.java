package br.com.julio.jvips.core.config;

import com.google.gson.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Merge especializado para vips.json.
 *
 * - Seções de topo (security, defaults, messages) → merge normal
 * - Seção "vips" → para cada VIP do disco, injeta propriedades novas
 *   usando um template construído de todos os VIPs do JAR
 * - VIPs que só existem no JAR (exemplos) NÃO são copiados
 * - Arrays (commandsOnActivate, lore) NUNCA são injetados
 */
public final class VipsConfigMerger {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VipsConfigMerger() {}

    public static boolean mergeIfNeeded(String resourcePath, Path diskPath,
                                        ClassLoader classLoader, Consumer<String> log) {
        if (!Files.exists(diskPath)) return false;

        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) return false;

            String jarJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String diskJson = Files.readString(diskPath, StandardCharsets.UTF_8);

            JsonObject jarObj = JsonParser.parseString(jarJson).getAsJsonObject();
            JsonObject diskObj = JsonParser.parseString(diskJson).getAsJsonObject();

            int totalAdded = 0;

            // 1) Merge das seções de topo (exceto "vips")
            for (Map.Entry<String, JsonElement> entry : jarObj.entrySet()) {
                String key = entry.getKey();
                if ("vips".equals(key)) continue;

                if (!diskObj.has(key)) {
                    diskObj.add(key, entry.getValue().deepCopy());
                    totalAdded++;
                    log.accept("[Jvips] + vips.json new section: " + key);
                } else if (entry.getValue().isJsonObject() && diskObj.get(key).isJsonObject()) {
                    totalAdded += ConfigMerger.deepMerge(
                            entry.getValue().getAsJsonObject(),
                            diskObj.get(key).getAsJsonObject(),
                            key, log
                    );
                }
            }

            // 2) Merge da seção "vips"
            if (jarObj.has("vips") && jarObj.get("vips").isJsonObject()
                    && diskObj.has("vips") && diskObj.get("vips").isJsonObject()) {

                JsonObject jarVips = jarObj.getAsJsonObject("vips");
                JsonObject diskVips = diskObj.getAsJsonObject("vips");

                JsonObject vipTemplate = buildVipTemplate(jarVips);

                if (vipTemplate != null) {
                    for (Map.Entry<String, JsonElement> diskEntry : diskVips.entrySet()) {
                        String vipId = diskEntry.getKey();
                        if (!diskEntry.getValue().isJsonObject()) continue;

                        JsonObject diskVip = diskEntry.getValue().getAsJsonObject();
                        int before = totalAdded;

                        totalAdded += mergeVipProperties(vipTemplate, diskVip, "vips." + vipId, log);

                        if (totalAdded > before) {
                            log.accept("[Jvips] VIP '" + vipId + "' updated with new properties");
                        }
                    }
                }
            }

            if (totalAdded > 0) {
                String updatedJson = GSON.toJson(diskObj);
                Files.writeString(diskPath, updatedJson, StandardCharsets.UTF_8);
                log.accept("[Jvips] vips.json merged: " + totalAdded + " new properties added");
                return true;
            }

            return false;

        } catch (Exception e) {
            log.accept("[Jvips] Failed to merge vips.json: " + e.getMessage());
            return false;
        }
    }

    private static JsonObject buildVipTemplate(JsonObject jarVips) {
        JsonObject template = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : jarVips.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;

            JsonObject vip = entry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> prop : vip.entrySet()) {
                if (!template.has(prop.getKey())) {
                    template.add(prop.getKey(), prop.getValue().deepCopy());
                }
            }
        }

        return template.size() > 0 ? template : null;
    }

    private static int mergeVipProperties(JsonObject template, JsonObject diskVip,
                                          String path, Consumer<String> log) {
        int added = 0;

        for (Map.Entry<String, JsonElement> entry : template.entrySet()) {
            String key = entry.getKey();
            JsonElement templateVal = entry.getValue();
            String fullPath = path + "." + key;

            // Pular arrays (commandsOnActivate, commandsOnExpire, lore)
            if (templateVal.isJsonArray()) continue;

            if (!diskVip.has(key)) {
                diskVip.add(key, templateVal.deepCopy());
                added++;
                if (log != null) {
                    log.accept("[Jvips] + New VIP property: " + fullPath + " = " + templateVal);
                }
            } else if (templateVal.isJsonObject() && diskVip.get(key).isJsonObject()) {
                added += ConfigMerger.deepMerge(
                        templateVal.getAsJsonObject(),
                        diskVip.get(key).getAsJsonObject(),
                        fullPath, log
                );
            }
        }

        return added;
    }
}
