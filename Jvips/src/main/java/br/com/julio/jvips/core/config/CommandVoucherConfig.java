package br.com.julio.jvips.core.config;

import br.com.julio.jvips.core.model.CommandVoucherDefinition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Carrega e armazena as definições de Command Vouchers.
 */
public final class CommandVoucherConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Map<String, CommandVoucherDefinition> commandVouchers = new HashMap<>();

    public CommandVoucherConfig() {}

    public Map<String, CommandVoucherDefinition> getCommandVouchers() { return commandVouchers; }

    public CommandVoucherDefinition get(String id) {
        return commandVouchers.get(id);
    }

    public CommandVoucherDefinition getOrThrow(String id) {
        CommandVoucherDefinition def = commandVouchers.get(id);
        if (def == null) throw new IllegalArgumentException("Command voucher not found: " + id);
        return def;
    }

    /**
     * Carrega de um arquivo no disco.
     */
    public static CommandVoucherConfig loadFromPath(Path path) {
        try {
            if (!Files.exists(path)) return new CommandVoucherConfig();

            String json = Files.readString(path);
            Map<String, CommandVoucherDefinition> map = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructMapType(HashMap.class, String.class, CommandVoucherDefinition.class));

            CommandVoucherConfig cfg = new CommandVoucherConfig();
            if (map != null) {
                for (Map.Entry<String, CommandVoucherDefinition> e : map.entrySet()) {
                    CommandVoucherDefinition def = e.getValue();
                    def.setId(e.getKey());
                    def.validate();

                    // Garantir itemId padrão se não especificado
                    if (def.getVoucher() != null
                            && (def.getVoucher().getItemId() == null || def.getVoucher().getItemId().trim().isEmpty())) {
                        def.getVoucher().setItemId("Jvips_Voucher");
                    }

                    cfg.commandVouchers.put(e.getKey(), def);
                }
            }
            return cfg;

        } catch (Exception e) {
            System.err.println("[Jvips] Failed to load command_vouchers.json: " + e.getMessage());
            return new CommandVoucherConfig();
        }
    }

    /**
     * Carrega do resources do JAR.
     */
    public static CommandVoucherConfig loadFromResource(String resourceName) {
        try (InputStream in = CommandVoucherConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) return new CommandVoucherConfig();

            Map<String, CommandVoucherDefinition> map = MAPPER.readValue(in,
                    MAPPER.getTypeFactory().constructMapType(HashMap.class, String.class, CommandVoucherDefinition.class));

            CommandVoucherConfig cfg = new CommandVoucherConfig();
            if (map != null) {
                for (Map.Entry<String, CommandVoucherDefinition> e : map.entrySet()) {
                    CommandVoucherDefinition def = e.getValue();
                    def.setId(e.getKey());
                    def.validate();
                    cfg.commandVouchers.put(e.getKey(), def);
                }
            }
            return cfg;

        } catch (Exception e) {
            return new CommandVoucherConfig();
        }
    }
}
