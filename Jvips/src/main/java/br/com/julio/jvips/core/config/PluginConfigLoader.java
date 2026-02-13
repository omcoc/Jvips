package br.com.julio.jvips.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PluginConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PluginConfigLoader() {}

    public static PluginConfig load(Path path) {
        try {
            if (!Files.exists(path)) {
                return new PluginConfig().normalize();
            }

            String json = Files.readString(path);
            PluginConfig cfg = GSON.fromJson(json, PluginConfig.class);

            if (cfg == null) {
                return new PluginConfig().normalize();
            }

            return cfg.normalize();

        } catch (Exception e) {
            return new PluginConfig().normalize();
        }
    }
}
