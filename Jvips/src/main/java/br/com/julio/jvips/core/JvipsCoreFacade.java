package br.com.julio.jvips.core;

import br.com.julio.jvips.core.config.VipConfig;

public final class JvipsCoreFacade {

    private final VipConfig config;
    private final VoucherService voucherService;
    private final VipExpiryService expiryService;
    private final VipCommandService commandService;

    public JvipsCoreFacade(
            VipConfig config,
            VoucherService voucherService,
            VipExpiryService expiryService,
            VipCommandService commandService
    ) {
        this.config = config;
        this.voucherService = voucherService;
        this.expiryService = expiryService;
        this.commandService = commandService;
    }

    public VoucherService getVoucherService() {
        return voucherService;
    }

    public VipExpiryService getExpiryService() {
        return expiryService;
    }

    // ✅ Getter original (mantém compatibilidade)
    public VipCommandService getCommandService() {
        return commandService;
    }

    // ✅ Alias CLARO para uso no Interaction
    public VipCommandService getVipCommandService() {
        return commandService;
    }

    public VipConfig getConfig() {
        return config;
    }
}

