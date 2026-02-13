package br.com.julio.jvips.core.storage;

import br.com.julio.jvips.core.model.PlayerChestState;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistência dos baús virtuais VIP em vipschest.json.
 * Formato:
 * {
 *   "version": 1,
 *   "chests": {
 *     "uuid-do-jogador": {
 *       "capacity": 9,
 *       "items": {
 *         "0": { "id": "Ore_Copper", "quantity": 7, ... },
 *         "3": { "id": "Tool_Hatchet_Crude", "quantity": 1, ... }
 *       }
 *     }
 *   }
 * }
 */
public final class VipsChestStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path filePath;
    private final Object lock = new Object();

    public VipsChestStore(Path filePath) {
        this.filePath = filePath;
    }

    public Map<String, PlayerChestState> load() {
        synchronized (lock) {
            ensureExists();
            try {
                byte[] bytes = Files.readAllBytes(filePath);
                Root root = MAPPER.readValue(bytes, Root.class);
                if (root.chests == null) root.chests = new HashMap<>();
                return root.chests;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + filePath, e);
            }
        }
    }

    public void save(Map<String, PlayerChestState> chests) {
        synchronized (lock) {
            ensureExists();
            Root root = new Root();
            root.version = 1;
            root.chests = (chests == null) ? new HashMap<>() : chests;

            try {
                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                atomicWrite(json);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write " + filePath, e);
            }
        }
    }

    /**
     * Retorna o baú de um jogador (ou null se não existir).
     */
    public PlayerChestState getChest(String playerUuid) {
        Map<String, PlayerChestState> all = load();
        return all.get(playerUuid);
    }

    /**
     * Salva/atualiza o baú de um jogador.
     */
    public void saveChest(String playerUuid, PlayerChestState state) {
        synchronized (lock) {
            Map<String, PlayerChestState> all = load();
            all.put(playerUuid, state);
            save(all);
        }
    }

    private void ensureExists() {
        try {
            Path dir = filePath.getParent();
            if (dir != null) Files.createDirectories(dir);
            if (!Files.exists(filePath)) {
                Root root = new Root();
                root.version = 1;
                root.chests = new HashMap<>();
                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                Files.write(filePath, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed ensuring file exists: " + filePath, e);
        }
    }

    private void atomicWrite(String content) throws IOException {
        Path tmp = Paths.get(filePath.toString() + ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static final class Root {
        public int version;
        public Map<String, PlayerChestState> chests;
    }
}
