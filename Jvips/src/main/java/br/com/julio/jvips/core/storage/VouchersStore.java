package br.com.julio.jvips.core.storage;

import br.com.julio.jvips.core.model.VoucherRecord;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public final class VouchersStore {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path filePath;
    private final Object lock = new Object();

    public VouchersStore(Path filePath) {
        this.filePath = filePath;
    }

    public Map<String, VoucherRecord> load() {
        synchronized (lock) {
            ensureExists();
            try {
                byte[] bytes = Files.readAllBytes(filePath);
                Root root = MAPPER.readValue(bytes, Root.class);
                if (root.vouchers == null) root.vouchers = new HashMap<>();
                return root.vouchers;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + filePath, e);
            }
        }
    }

    public void save(Map<String, VoucherRecord> vouchers) {
        synchronized (lock) {
            ensureExists();
            Root root = new Root();
            root.version = 1;
            root.vouchers = (vouchers == null) ? new HashMap<>() : vouchers;

            try {
                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                atomicWrite(json);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write " + filePath, e);
            }
        }
    }

    private void ensureExists() {
        try {
            Path dir = filePath.getParent();
            if (dir != null) Files.createDirectories(dir);
            if (!Files.exists(filePath)) {
                Root root = new Root();
                root.version = 1;
                root.vouchers = new HashMap<>();
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
        public Map<String, VoucherRecord> vouchers;
    }
}
