package br.com.julio.jvips.plugin;

import br.com.julio.jvips.core.CommandDispatcher;
import br.com.julio.jvips.core.JvipsCoreFacade;
import br.com.julio.jvips.core.VipCommandService;
import br.com.julio.jvips.core.VipExpiryService;
import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.config.PluginConfig;
import br.com.julio.jvips.core.config.PluginConfigLoader;
import br.com.julio.jvips.core.config.VipConfig;
import br.com.julio.jvips.core.config.VipConfigLoader;
import br.com.julio.jvips.core.messages.MessagesService;
import br.com.julio.jvips.core.storage.PlayersStore;
import br.com.julio.jvips.core.storage.VouchersStore;
import br.com.julio.jvips.plugin.commands.VipCommand;
import br.com.julio.jvips.plugin.commands.VipsCommand;
import br.com.julio.jvips.plugin.systems.VoucherDropBlockSystem;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.nio.file.StandardCopyOption;

public final class JvipsPlugin extends JavaPlugin {

    private JvipsCoreFacade core;

    // novos: config do plugin + messages carregadas
    private PluginConfig pluginConfig;
    private MessagesService messages;
    private br.com.julio.jvips.core.config.CommandVoucherConfig commandVoucherConfig;

    public JvipsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[Jvips] Setup inicial...");

        Path dataFolder = getDataDirectory();
        Path dataDir = dataFolder.resolve("data");

        try {
            Files.createDirectories(dataDir);
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e)
                    .log("[Jvips] Failed to create data directory.");
        }
        migrateLegacyData(dataFolder, dataDir);


        // =========================================================
        // 1) Garantir arquivos padrão (do JAR -> dataFolder)
        // =========================================================
        ensureDefaultResource("config.json", dataFolder.resolve("config.json"));
        ensureDefaultResource("vips.json", dataFolder.resolve("vips.json"));
        ensureDefaultResource("command_vouchers.json", dataFolder.resolve("command_vouchers.json"));

        ensureDefaultResource("Messages/pt_BR.json", dataFolder.resolve("Messages").resolve("pt_BR.json"));
        ensureDefaultResource("Messages/en_US.json", dataFolder.resolve("Messages").resolve("en_US.json"));
        ensureDefaultResource("Messages/es_ES.json", dataFolder.resolve("Messages").resolve("es_ES.json"));

        // =========================================================
        // 1.5) Merge inteligente — adicionar propriedades novas sem
        //      sobrescrever o que o admin já configurou
        // =========================================================

        // config.json: merge genérico de objetos (novas seções/campos)
        br.com.julio.jvips.core.config.ConfigMerger.mergeIfNeeded(
                "config.json", dataFolder.resolve("config.json"),
                getClass().getClassLoader(), msg -> getLogger().at(Level.INFO).log(msg));

        // vips.json: merge especializado (injeta stackable, stackAmount, etc.
        // em VIPs existentes do admin, sem tocar arrays/commandsOnActivate)
        br.com.julio.jvips.core.config.VipsConfigMerger.mergeIfNeeded(
                "vips.json", dataFolder.resolve("vips.json"),
                getClass().getClassLoader(), msg -> getLogger().at(Level.INFO).log(msg));

        // Messages: merge de chaves novas (flat map de strings)
        mergeMessageKeys("Messages/pt_BR.json", dataFolder.resolve("Messages").resolve("pt_BR.json"));
        mergeMessageKeys("Messages/en_US.json", dataFolder.resolve("Messages").resolve("en_US.json"));
        mergeMessageKeys("Messages/es_ES.json", dataFolder.resolve("Messages").resolve("es_ES.json"));

        // =========================================================
        // 2) Carregar config.json + vips.json + messages
        // =========================================================
        try {
            this.pluginConfig = PluginConfigLoader.load(dataFolder.resolve("config.json"));
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e)
                    .log("[Jvips] ERRO ao carregar config.json (usando padrão).");
            this.pluginConfig = new PluginConfig(); // precisa existir com defaults
        }

        VipConfig vipConfig;
        try {
            vipConfig = VipConfigLoader.loadFromPath(dataFolder.resolve("vips.json"));
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e)
                    .log("[Jvips] ERRO ao carregar vips.json (usando config vazio).");
            vipConfig = new VipConfig();
        }

        // Command Vouchers (arquivo separado, sem HMAC)
        try {
            this.commandVoucherConfig = br.com.julio.jvips.core.config.CommandVoucherConfig
                    .loadFromPath(dataFolder.resolve("command_vouchers.json"));
            getLogger().at(Level.INFO).log("[Jvips] Command vouchers carregados: %d definições.",
                    commandVoucherConfig.getCommandVouchers().size());
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e)
                    .log("[Jvips] Failed to load command_vouchers.json.");
            this.commandVoucherConfig = new br.com.julio.jvips.core.config.CommandVoucherConfig();
        }

        this.messages = new MessagesService();
        try {
            String lang = (pluginConfig.getLanguage() == null || pluginConfig.getLanguage().trim().isEmpty())
                    ? "pt_BR"
                    : pluginConfig.getLanguage().trim();

            Path messagesDir = dataFolder.resolve("Messages");
            Path langFile = messagesDir.resolve(lang + ".json");
            Path fallbackFile = messagesDir.resolve("en_US.json");

            // primary + fallback (se faltar alguma key no idioma, cai no en_US)
            messages.load(langFile, fallbackFile);

            getLogger().at(Level.INFO).log("[Jvips] Messages carregadas: %s (fallback: %s)",
                    langFile.toAbsolutePath().toString(),
                    fallbackFile.toAbsolutePath().toString());
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e)
                    .log("[Jvips] ERRO ao carregar messages. O plugin continuará, mas mensagens podem falhar.");
        }

        // =========================================================
        // 3) Storage
        // =========================================================
        PlayersStore playersStore = new PlayersStore(dataDir.resolve("players.json"));
        VouchersStore vouchersStore = new VouchersStore(dataDir.resolve("vouchers.json"));
        br.com.julio.jvips.core.storage.HistoryStore historyStore = new br.com.julio.jvips.core.storage.HistoryStore(dataDir.resolve("history.json"));
        br.com.julio.jvips.core.storage.VipsChestStore chestStore = new br.com.julio.jvips.core.storage.VipsChestStore(dataDir.resolve("vipschest.json"));

        // =========================================================
        // 4) Dispatcher
        // =========================================================
        CommandDispatcher dispatcher = this::dispatchCommandAsConsole;

        // =========================================================
        // 5) Core services
        // =========================================================
        VoucherService voucherService = new VoucherService(vipConfig, playersStore, vouchersStore);
        VipExpiryService expiryService = new VipExpiryService(vipConfig, playersStore);
        VipCommandService vipCommandService = new VipCommandService(dispatcher);

        this.core = new JvipsCoreFacade(vipConfig, voucherService, expiryService, vipCommandService);
        this.core.setHistoryStore(historyStore);
        this.core.setChestManager(new br.com.julio.jvips.plugin.chest.VipsChestManager(chestStore));

        // 6) Service locator
        JvipsServices.setPlugin(this);

        // =========================================================
        // 7) Commands
        // =========================================================
        getCommandRegistry().registerCommand(new VipsCommand(this));
        getLogger().at(Level.INFO).log("[Jvips] Comando /vips registrado.");
        getCommandRegistry().registerCommand(new VipCommand(this));
        getLogger().at(Level.INFO).log("[Jvips] Comando /vip registrado.");

        // =========================================================
        // 8) ECS: registrar evento (com proteção contra duplicidade)
        // =========================================================
        try {
            getEntityStoreRegistry().registerEntityEventType(DropItemEvent.PlayerRequest.class);
            getLogger().at(Level.INFO).log("[Jvips] DropItemEvent.PlayerRequest registrado com sucesso.");
        } catch (IllegalArgumentException ignored) {
            getLogger().at(Level.INFO).log("[Jvips] DropItemEvent.PlayerRequest já estava registrado.");
        }

        getEntityStoreRegistry().registerSystem(new VoucherDropBlockSystem());
        getLogger().at(Level.INFO).log("[Jvips] VoucherDropBlockSystem registrado.");

        // =========================================================
        // 9) Tick expiração (você pode futuramente ler tick do pluginConfig)
        // =========================================================
        getEntityStoreRegistry().registerSystem(new br.com.julio.jvips.plugin.systems.VipExpiryTickingSystem());
        getLogger().at(Level.INFO).log("[Jvips] VipExpiryTickingSystem registrado (expiração automática).");

        // =========================================================
        // 10) Interaction codec
        // =========================================================
        this.getCodecRegistry(Interaction.CODEC).register(
                "jvips_voucher_interaction",
                br.com.julio.jvips.plugin.interactions.JvipsVoucherInteraction.class,
                br.com.julio.jvips.plugin.interactions.JvipsVoucherInteraction.CODEC
        );
        getLogger().at(Level.INFO).log("[Jvips] Interaction jvips_voucher_interaction registrada.");

        getLogger().at(Level.INFO).log("[Jvips] Setup concluído.");
    }

    // =========================================================
    // EXECUÇÃO DE COMANDOS COMO CONSOLE (async + log start/ok/fail)
    // =========================================================
    private CompletableFuture<Void> dispatchCommandAsConsole(String rawCmd) {
        if (rawCmd == null) return CompletableFuture.completedFuture(null);

        String normalized = rawCmd.trim();
        if (normalized.isEmpty()) return CompletableFuture.completedFuture(null);
        if (normalized.startsWith("/")) normalized = normalized.substring(1);

        final String cmd = normalized;

        getLogger().at(Level.INFO).log("[Jvips] DispatchAsConsole START -> %s", cmd);

        try {
            return CommandManager.get()
                    .handleCommand(ConsoleSender.INSTANCE, cmd)
                    .handle((res, err) -> {
                        if (err != null) {
                            getLogger().at(Level.SEVERE).withCause(err)
                                    .log("[Jvips] DispatchAsConsole FAIL -> %s", cmd);
                        } else {
                            getLogger().at(Level.INFO)
                                    .log("[Jvips] DispatchAsConsole OK -> %s", cmd);
                        }
                        return null;
                    });
        } catch (Throwable t) {
            getLogger().at(Level.SEVERE).withCause(t)
                    .log("[Jvips] DispatchAsConsole THROW -> %s", cmd);
            return CompletableFuture.completedFuture(null);
        }
    }

    // =========================================================
    // Copiar resource do JAR -> dataFolder (suporta subpastas)
    // =========================================================
    private void ensureDefaultResource(String resourcePath, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());

            if (Files.exists(targetPath)) return;

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    getLogger().at(Level.WARNING).log("[Jvips] Resource not found in JAR: %s", resourcePath);
                    return;
                }

                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                getLogger().at(Level.INFO).log("[Jvips] Default file created: %s", targetPath.toAbsolutePath().toString());
            }

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e)
                    .log("[Jvips] Failed to create default file: %s", targetPath.toAbsolutePath().toString());
        }
    }

    /**
     * Injeta chaves faltantes do JSON do JAR no arquivo existente do servidor.
     * Isso garante que novas mensagens adicionadas em updates sejam automaticamente
     * adicionadas sem sobrescrever as personalizações do admin.
     */
    private void mergeMessageKeys(String resourcePath, Path targetPath) {
        if (!Files.exists(targetPath)) return;

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return;

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<java.util.LinkedHashMap<String, String>>() {}.getType();

            // Lê o JSON do JAR (fonte com todas as keys)
            String jarJson = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            java.util.LinkedHashMap<String, String> jarMap = gson.fromJson(jarJson, mapType);
            if (jarMap == null) return;

            // Lê o JSON existente no servidor
            String diskJson = Files.readString(targetPath, java.nio.charset.StandardCharsets.UTF_8);
            java.util.LinkedHashMap<String, String> diskMap = gson.fromJson(diskJson, mapType);
            if (diskMap == null) diskMap = new java.util.LinkedHashMap<>();

            // Injeta keys faltantes (sem sobrescrever existentes)
            boolean changed = false;
            for (Map.Entry<String, String> entry : jarMap.entrySet()) {
                if (!diskMap.containsKey(entry.getKey())) {
                    diskMap.put(entry.getKey(), entry.getValue());
                    changed = true;
                    getLogger().at(Level.INFO).log("[Jvips] Merged new message key: %s -> %s", entry.getKey(), targetPath.getFileName());
                }
            }

            if (changed) {
                String updatedJson = gson.toJson(diskMap);
                Files.writeString(targetPath, updatedJson, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e)
                    .log("[Jvips] Failed to merge message keys for: %s", targetPath.toAbsolutePath().toString());
        }
    }


    // =========================================================
    // Reload completo (config.json + messages + vips.json)
    // =========================================================
    public boolean reloadJvipsConfig() {
        try {
            Path dataFolder = getDataDirectory();

            // Merge inteligente antes de carregar (adiciona propriedades novas)
            br.com.julio.jvips.core.config.ConfigMerger.mergeIfNeeded(
                    "config.json", dataFolder.resolve("config.json"),
                    getClass().getClassLoader(), msg -> getLogger().at(Level.INFO).log(msg));

            br.com.julio.jvips.core.config.VipsConfigMerger.mergeIfNeeded(
                    "vips.json", dataFolder.resolve("vips.json"),
                    getClass().getClassLoader(), msg -> getLogger().at(Level.INFO).log(msg));

            mergeMessageKeys("Messages/pt_BR.json", dataFolder.resolve("Messages").resolve("pt_BR.json"));
            mergeMessageKeys("Messages/en_US.json", dataFolder.resolve("Messages").resolve("en_US.json"));
            mergeMessageKeys("Messages/es_ES.json", dataFolder.resolve("Messages").resolve("es_ES.json"));

            // 1) plugin config
            PluginConfig newPluginConfig = PluginConfigLoader.load(dataFolder.resolve("config.json"));

            // 2) vips config
            VipConfig newVipConfig = VipConfigLoader.loadFromPath(dataFolder.resolve("vips.json"));

            // 3) messages
            MessagesService newMessages = new MessagesService();
            String lang = (newPluginConfig.getLanguage() == null || newPluginConfig.getLanguage().trim().isEmpty())
                    ? "pt_BR"
                    : newPluginConfig.getLanguage().trim();
            Path messagesDir = dataFolder.resolve("Messages");
            Path langFile = messagesDir.resolve(lang + ".json");
            Path fallbackFile = messagesDir.resolve("en_US.json");
            newMessages.load(langFile, fallbackFile);

            // 4) rewire core services (stores continuam apontando pros mesmos arquivos)
            Path dataDir = dataFolder.resolve("data");
            Files.createDirectories(dataDir);

            PlayersStore playersStore = new PlayersStore(dataDir.resolve("players.json"));
            VouchersStore vouchersStore = new VouchersStore(dataDir.resolve("vouchers.json"));
            br.com.julio.jvips.core.storage.HistoryStore historyStore = new br.com.julio.jvips.core.storage.HistoryStore(dataDir.resolve("history.json"));
            br.com.julio.jvips.core.storage.VipsChestStore chestStore = new br.com.julio.jvips.core.storage.VipsChestStore(dataDir.resolve("vipschest.json"));

            VoucherService voucherService = new VoucherService(newVipConfig, playersStore, vouchersStore);
            VipExpiryService expiryService = new VipExpiryService(newVipConfig, playersStore);
            VipCommandService vipCommandService = new VipCommandService(this::dispatchCommandAsConsole);

            this.core = new JvipsCoreFacade(newVipConfig, voucherService, expiryService, vipCommandService);
            this.core.setHistoryStore(historyStore);
            this.core.setChestManager(new br.com.julio.jvips.plugin.chest.VipsChestManager(chestStore));

            // swap fields
            this.pluginConfig = newPluginConfig;
            this.messages = newMessages;

            // Reload command vouchers
            try {
                this.commandVoucherConfig = br.com.julio.jvips.core.config.CommandVoucherConfig
                        .loadFromPath(dataFolder.resolve("command_vouchers.json"));
            } catch (Exception e) {
                this.commandVoucherConfig = new br.com.julio.jvips.core.config.CommandVoucherConfig();
            }

            getLogger().at(Level.INFO).log("[Jvips] Reload OK (lang=%s).", lang);
            return true;

        } catch (Throwable t) {
            getLogger().at(Level.SEVERE).withCause(t).log("[Jvips] Reload FAIL.");
            return false;
        }
    }
    private void migrateLegacyData(Path baseDir, Path dataDir) {
        try {
            Path oldPlayers = baseDir.resolve("players.json");
            Path oldVouchers = baseDir.resolve("vouchers.json");

            if (Files.exists(oldPlayers) && !Files.exists(dataDir.resolve("players.json"))) {
                Files.move(oldPlayers, dataDir.resolve("players.json"));
            }

            if (Files.exists(oldVouchers) && !Files.exists(dataDir.resolve("vouchers.json"))) {
                Files.move(oldVouchers, dataDir.resolve("vouchers.json"));
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e)
                    .log("[Jvips] Failed to migrate legacy data files.");
        }
    }


    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("[Jvips] Start (plugin ativo).");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("[Jvips] Shutdown (plugin desligando).");
    }

    public JvipsCoreFacade getCore() {
        return core;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public MessagesService getMessages() {
        return messages;
    }

    public br.com.julio.jvips.core.config.CommandVoucherConfig getCommandVoucherConfig() {
        return commandVoucherConfig;
    }
}