package br.com.julio.jvips.core;

import br.com.julio.jvips.core.config.PluginConfig;
import br.com.julio.jvips.core.config.VipConfig;
import br.com.julio.jvips.core.messages.MessagesService;
import br.com.julio.jvips.core.storage.HistoryStore;
import br.com.julio.jvips.plugin.chest.VipsChestManager;

public final class JvipsCoreFacade {

    private VipConfig config;
    private PluginConfig pluginConfig;
    private MessagesService messages;

    private final VoucherService voucherService;
    private final VipExpiryService expiryService;
    private final VipCommandService commandService;
    private HistoryStore historyStore;
    private VipsChestManager chestManager;

    // ✅ Mantém compatibilidade com teu código atual
    public JvipsCoreFacade(
            VipConfig config,
            VoucherService voucherService,
            VipExpiryService expiryService,
            VipCommandService commandService
    ) {
        this(config, new PluginConfig(), new MessagesService(),
                voucherService, expiryService, commandService);
    }

    // ✅ Novo construtor completo (para setup/reload)
    public JvipsCoreFacade(
            VipConfig config,
            PluginConfig pluginConfig,
            MessagesService messages,
            VoucherService voucherService,
            VipExpiryService expiryService,
            VipCommandService commandService
    ) {
        this.config = config;

        this.pluginConfig = (pluginConfig != null) ? pluginConfig : new PluginConfig();
        this.messages = (messages != null) ? messages : new MessagesService();

        this.voucherService = voucherService;
        this.expiryService = expiryService;
        this.commandService = commandService;
    }

    // =========================================================
    // CONFIG VIPS
    // =========================================================
    public VipConfig getConfig() {
        return config;
    }

    public void setConfig(VipConfig newConfig) {
        if (newConfig == null) throw new IllegalArgumentException("VipConfig cannot be null");
        this.config = newConfig;
    }

    // =========================================================
    // PLUGIN CONFIG + MESSAGES
    // =========================================================
    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public void setPluginConfig(PluginConfig pluginConfig) {
        if (pluginConfig == null) throw new IllegalArgumentException("PluginConfig cannot be null");
        this.pluginConfig = pluginConfig;
    }

    public MessagesService getMessages() {
        return messages;
    }

    public void setMessages(MessagesService messages) {
        if (messages == null) throw new IllegalArgumentException("MessagesService cannot be null");
        this.messages = messages;
    }

    // =========================================================
    // SERVICES
    // =========================================================
    public VoucherService getVoucherService() {
        return voucherService;
    }

    public VipExpiryService getExpiryService() {
        return expiryService;
    }

    public VipCommandService getCommandService() {
        return commandService;
    }

    public VipCommandService getVipCommandService() {
        return commandService;
    }

    public HistoryStore getHistoryStore() {
        return historyStore;
    }

    public void setHistoryStore(HistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    public VipsChestManager getChestManager() {
        return chestManager;
    }

    public void setChestManager(VipsChestManager chestManager) {
        this.chestManager = chestManager;
    }
}
