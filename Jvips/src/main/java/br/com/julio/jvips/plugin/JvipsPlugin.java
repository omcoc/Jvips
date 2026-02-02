package br.com.julio.jvips.plugin;

import br.com.julio.jvips.core.CommandDispatcher;
import br.com.julio.jvips.core.JvipsCoreFacade;
import br.com.julio.jvips.core.VipCommandService;
import br.com.julio.jvips.core.VipExpiryService;
import br.com.julio.jvips.core.VoucherService;
import br.com.julio.jvips.core.config.VipConfig;
import br.com.julio.jvips.core.config.VipConfigLoader;
import br.com.julio.jvips.core.storage.PlayersStore;
import br.com.julio.jvips.core.storage.VouchersStore;
import br.com.julio.jvips.plugin.commands.VipsCommand;
import br.com.julio.jvips.plugin.systems.VoucherDropBlockSystem;

import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class JvipsPlugin extends JavaPlugin {

    private JvipsCoreFacade core;

    public JvipsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[Jvips] Setup inicial...");

        // 1) Config files
        Path dataFolder = getDataDirectory();
        ensureDefaultFile(dataFolder, "vips.json");

        VipConfig config;
        try {
            config = VipConfigLoader.loadFromPath(dataFolder.resolve("vips.json"));
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("[Jvips] ERRO ao carregar vips.json (usando config vazio).");
            config = new VipConfig();
        }

        // 2) Storage
        PlayersStore playersStore = new PlayersStore(dataFolder.resolve("players.json"));
        VouchersStore vouchersStore = new VouchersStore(dataFolder.resolve("vouchers.json"));

        // 3) Dispatcher (EXECUTA COMO CONSOLE DE VERDADE)
        CommandDispatcher dispatcher = this::dispatchCommandAsConsole;

        // 4) Core services
        VoucherService voucherService = new VoucherService(config, playersStore, vouchersStore);
        VipExpiryService expiryService = new VipExpiryService(config, playersStore);
        VipCommandService vipCommandService = new VipCommandService(dispatcher);

        this.core = new JvipsCoreFacade(config, voucherService, expiryService, vipCommandService);

        // 5) Service locator
        JvipsServices.setPlugin(this);

        // 6) Commands
        getCommandRegistry().registerCommand(new VipsCommand(this));
        getLogger().at(Level.INFO).log("[Jvips] Comando /vips registrado.");

        // 7) ECS: bloquear drop de voucher (NÃO mexe em drop normal)
        getEntityStoreRegistry().registerEntityEventType(DropItemEvent.PlayerRequest.class);
        getEntityStoreRegistry().registerSystem(new VoucherDropBlockSystem());
        getLogger().at(Level.INFO).log("[Jvips] VoucherDropBlockSystem registrado.");

        //7.1 Tick para checar expiração de Vips
        getEntityStoreRegistry().registerSystem(new br.com.julio.jvips.plugin.systems.VipExpiryTickingSystem());
        getLogger().at(Level.INFO).log("[Jvips] VipExpiryTickingSystem registrado (expiração automática).");



        // 8) Interaction codec
        this.getCodecRegistry(Interaction.CODEC).register(
                "jvips_voucher_interaction",
                br.com.julio.jvips.plugin.interactions.JvipsVoucherInteraction.class,
                br.com.julio.jvips.plugin.interactions.JvipsVoucherInteraction.CODEC
        );
        getLogger().at(Level.INFO).log("[Jvips] Interaction jvips_voucher_interaction registrada.");

        getLogger().at(Level.INFO).log("[Jvips] Setup concluído.");
    }

    // =========================================================
    // EXECUÇÃO DE COMANDOS COMO CONSOLE
    // =========================================================
    private CompletableFuture<Void> dispatchCommandAsConsole(String rawCmd) {
        if (rawCmd == null) return CompletableFuture.completedFuture(null);

        String normalized = rawCmd.trim();
        if (normalized.isEmpty()) return CompletableFuture.completedFuture(null);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        final String cmd = normalized; // 👈 agora é FINAL


        // 🔹 ITEM 2 — LOG DE INÍCIO (AQUI)
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

    private void ensureDefaultFile(Path dataFolder, String fileName) {
        try {
            Files.createDirectories(dataFolder);

            Path target = dataFolder.resolve(fileName);
            if (Files.exists(target)) return;

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(fileName)) {
                if (in == null) {
                    getLogger().at(Level.WARNING).log("[Jvips] Recurso %s não encontrado no JAR.", fileName);
                    return;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                getLogger().at(Level.INFO).log("[Jvips] Criado %s em %s", fileName, target.toAbsolutePath().toString());
            }
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("[Jvips] Falha ao criar %s.", fileName);
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
}
