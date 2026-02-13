package br.com.julio.jvips.core.config;

import com.google.gson.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Utilitário de merge inteligente para arquivos JSON de configuração.
 *
 * Regras do merge:
 *   - Propriedades que o usuário já definiu NÃO são sobrescritas.
 *   - Propriedades novas do JAR são adicionadas com o valor padrão.
 *   - Objetos aninhados são mergeados recursivamente.
 *   - Arrays NÃO são mergeados (são do usuário — ex: commandsOnActivate).
 *   - Primitivos existentes no disco são preservados.
 */
public final class ConfigMerger {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigMerger() {}

    /**
     * Faz merge do JSON default do JAR com o arquivo existente no disco.
     * Se houve mudança, reescreve o arquivo.
     *
     * @param resourcePath caminho do resource no JAR (ex: "config.json")
     * @param diskPath     caminho do arquivo no disco
     * @param classLoader  classLoader para carregar o resource
     * @param log          consumer de mensagens de log (ex: msg -> getLogger().at(INFO).log(msg))
     * @return true se o arquivo foi atualizado, false se não precisou de merge
     */
    public static boolean mergeIfNeeded(String resourcePath, Path diskPath,
                                        ClassLoader classLoader, Consumer<String> log) {
        if (!Files.exists(diskPath)) return false;

        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) return false;

            String jarJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String diskJson = Files.readString(diskPath, StandardCharsets.UTF_8);

            JsonElement jarElement = JsonParser.parseString(jarJson);
            JsonElement diskElement = JsonParser.parseString(diskJson);

            if (!jarElement.isJsonObject() || !diskElement.isJsonObject()) return false;

            JsonObject jarObj = jarElement.getAsJsonObject();
            JsonObject diskObj = diskElement.getAsJsonObject();

            int added = deepMerge(jarObj, diskObj, "", log);

            if (added > 0) {
                String updatedJson = GSON.toJson(diskObj);
                Files.writeString(diskPath, updatedJson, StandardCharsets.UTF_8);
                log.accept("[Jvips] Config merged: " + added + " new properties added to " + diskPath.getFileName());
                return true;
            }

            return false;

        } catch (Exception e) {
            log.accept("[Jvips] Failed to merge config: " + diskPath.getFileName() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Merge recursivo: adiciona ao diskObj qualquer propriedade que existe no jarObj
     * mas não existe no diskObj. Objetos aninhados são mergeados recursivamente.
     *
     * @return quantidade de propriedades adicionadas
     */
    static int deepMerge(JsonObject source, JsonObject target, String path, Consumer<String> log) {
        int added = 0;

        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            JsonElement sourceVal = entry.getValue();
            String fullPath = path.isEmpty() ? key : path + "." + key;

            if (!target.has(key)) {
                target.add(key, sourceVal.deepCopy());
                added++;
                if (log != null) {
                    log.accept("[Jvips] + New config property: " + fullPath);
                }
            } else if (sourceVal.isJsonObject() && target.get(key).isJsonObject()) {
                added += deepMerge(
                        sourceVal.getAsJsonObject(),
                        target.get(key).getAsJsonObject(),
                        fullPath,
                        log
                );
            }
        }

        return added;
    }
}
