package br.com.julio.jvips.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VipConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private VipConfigLoader() {}

    /** Carrega do resources do JAR (src/main/resources/vips.json) */
    public static VipConfig loadFromResource(String resourceName) {
        try (InputStream in = VipConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado no JAR: " + resourceName);
            }
            VipConfig cfg = MAPPER.readValue(in, VipConfig.class);
            cfg.validate();
            return cfg;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar resource: " + resourceName, e);
        }
    }

    /** Carrega de um arquivo no disco (ex.: pasta de dados do plugin) */
    public static VipConfig loadFromPath(Path path) {
        try {
            String json = Files.readString(path);
            VipConfig cfg = MAPPER.readValue(json, VipConfig.class);
            cfg.validate();
            return cfg;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler vips.json em: " + path.toAbsolutePath(), e);
        }
    }
}
