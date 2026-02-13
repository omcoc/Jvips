package br.com.julio.jvips.core.storage;

import br.com.julio.jvips.core.model.VipHistoryEntry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistência do histórico de VIPs por jogador.
 * Armazena em history.json no diretório /data.
 */
public final class HistoryStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path filePath;
    private final Object lock = new Object();

    public HistoryStore(Path filePath) {
        this.filePath = filePath;
    }

    public Map<String, List<VipHistoryEntry>> load() {
        synchronized (lock) {
            ensureExists();
            try {
                byte[] bytes = Files.readAllBytes(filePath);
                Root root = MAPPER.readValue(bytes, Root.class);
                if (root.history == null) root.history = new HashMap<>();
                return root.history;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + filePath, e);
            }
        }
    }

    public void save(Map<String, List<VipHistoryEntry>> history) {
        synchronized (lock) {
            ensureExists();
            Root root = new Root();
            root.version = 1;
            root.history = (history == null) ? new HashMap<>() : history;

            try {
                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                atomicWrite(json);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write " + filePath, e);
            }
        }
    }

    /**
     * Adiciona uma entrada ao histórico de um jogador.
     */
    public void addEntry(String playerUuid, VipHistoryEntry entry) {
        synchronized (lock) {
            Map<String, List<VipHistoryEntry>> all = load();
            all.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(entry);
            save(all);
        }
    }

    /**
     * Retorna o histórico de um jogador.
     */
    public List<VipHistoryEntry> getHistory(String playerUuid) {
        Map<String, List<VipHistoryEntry>> all = load();
        return all.getOrDefault(playerUuid, new ArrayList<>());
    }

    /**
     * Finaliza uma entrada ativa (endedAt == 0) para o jogador/vipId.
     */
    public void finalizeEntry(String playerUuid, String vipId, long endedAtEpoch, String endReason) {
        synchronized (lock) {
            Map<String, List<VipHistoryEntry>> all = load();
            List<VipHistoryEntry> list = all.get(playerUuid);
            if (list == null) return;

            for (VipHistoryEntry e : list) {
                // endedAt é long (primitivo): 0 = ainda ativo
                if (e != null && vipId.equalsIgnoreCase(e.getVipId()) && e.getEndedAt() == 0) {
                    e.setEndedAt(endedAtEpoch);
                    e.setEndReason(endReason);
                    break;
                }
            }
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
                root.history = new HashMap<>();
                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                Files.write(filePath, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed ensuring file exists: " + filePath, e);
        }
    }

    private void atomicWrite(String content) throws IOException {
        Path tmp = Paths.get(filePath.toString() + ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static final class Root {
        public int version;
        public Map<String, List<VipHistoryEntry>> history;
    }
}
