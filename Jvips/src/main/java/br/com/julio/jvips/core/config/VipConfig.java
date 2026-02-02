package br.com.julio.jvips.core.config;

import br.com.julio.jvips.core.model.VipDefinition;

import java.util.HashMap;
import java.util.Map;

public final class VipConfig {
    private int version = 1;

    private Security security = new Security();
    private Defaults defaults = new Defaults();
    private Messages messages = new Messages();

    // vipId -> VipDefinition (sem o campo id dentro do JSON; nós injetamos)
    private Map<String, VipDefinition> vips = new HashMap<>();

    public VipConfig() {}

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Defaults getDefaults() { return defaults; }
    public void setDefaults(Defaults defaults) { this.defaults = defaults; }

    public Messages getMessages() { return messages; }
    public void setMessages(Messages messages) { this.messages = messages; }

    public Map<String, VipDefinition> getVips() { return vips; }
    public void setVips(Map<String, VipDefinition> vips) { this.vips = (vips == null) ? new HashMap<>() : vips; }

    public void validate() {
        if (security == null || security.hmacSecret == null || security.hmacSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("security.hmacSecret is required in vips.json");
        }
        if (defaults == null) defaults = new Defaults();
        if (messages == null) messages = new Messages();
        if (vips == null || vips.isEmpty()) throw new IllegalArgumentException("vips section must not be empty");

        for (Map.Entry<String, VipDefinition> e : vips.entrySet()) {
            String vipId = e.getKey();
            VipDefinition def = e.getValue();
            if (def == null) throw new IllegalArgumentException("VIP '" + vipId + "' is null");
            def.setId(vipId);
            // Se voucher.itemId não vier, usa o default
            if (def.getVoucher() != null && (def.getVoucher().getItemId() == null || def.getVoucher().getItemId().trim().isEmpty())) {
                def.getVoucher().setItemId(defaults.voucherItemId);
            }
            def.validate();
        }
    }

    public VipDefinition getVipOrThrow(String vipId) {
        VipDefinition def = vips.get(vipId);
        if (def == null) throw new IllegalArgumentException("VIP not found: " + vipId);
        return def;
    }

    public static final class Security {
        private String hmacSecret;

        public Security() {}

        public String getHmacSecret() { return hmacSecret; }
        public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
    }

    public static final class Defaults {
        private String voucherItemId = "Jvips_Voucher";
        private boolean consumeOnUse = true;

        public Defaults() {}

        public String getVoucherItemId() { return voucherItemId; }
        public void setVoucherItemId(String voucherItemId) { this.voucherItemId = voucherItemId; }

        public boolean isConsumeOnUse() { return consumeOnUse; }
        public void setConsumeOnUse(boolean consumeOnUse) { this.consumeOnUse = consumeOnUse; }
    }

    public static final class Messages {
        private String prefix = "[VIP] ";
        private String receivedVoucher = "Voce recebeu um voucher do VIP {vipDisplay}.";
        private String inventoryFull = "Seu inventario esta cheio. O voucher foi dropado no chao.";
        private String activated = "VIP {vipDisplay} ativado por {durationHuman}.";
        private String alreadyHasVip = "Voce ja possui um VIP ativo ({activeVipDisplay}) ate {activeVipExpiresAt}.";
        private String invalidVoucher = "Este voucher e invalido.";
        private String alreadyUsedVoucher = "Este voucher ja foi utilizado.";
        private String notYourVoucher = "Este voucher esta vinculado a outro jogador e nao pode ser utilizado por voce.";

        public Messages() {}

        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }

        public String getReceivedVoucher() { return receivedVoucher; }
        public void setReceivedVoucher(String receivedVoucher) { this.receivedVoucher = receivedVoucher; }

        public String getInventoryFull() { return inventoryFull; }
        public void setInventoryFull(String inventoryFull) { this.inventoryFull = inventoryFull; }

        public String getActivated() { return activated; }
        public void setActivated(String activated) { this.activated = activated; }

        public String getAlreadyHasVip() { return alreadyHasVip; }
        public void setAlreadyHasVip(String alreadyHasVip) { this.alreadyHasVip = alreadyHasVip; }

        public String getInvalidVoucher() { return invalidVoucher; }
        public void setInvalidVoucher(String invalidVoucher) { this.invalidVoucher = invalidVoucher; }

        public String getAlreadyUsedVoucher() { return alreadyUsedVoucher; }
        public void setAlreadyUsedVoucher(String alreadyUsedVoucher) { this.alreadyUsedVoucher = alreadyUsedVoucher; }

        public String getNotYourVoucher() { return notYourVoucher; }
        public void setNotYourVoucher(String notYourVoucher) { this.notYourVoucher = notYourVoucher; }
    }
}
