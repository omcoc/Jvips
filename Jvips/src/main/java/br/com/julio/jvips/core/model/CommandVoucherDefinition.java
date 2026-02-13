package br.com.julio.jvips.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Definição de um Command Voucher — voucher de uso único que roda comandos.
 * Não cria VIP, sem duração, sem persistência em players.json.
 *
 * Configurado em command_vouchers.json.
 */
public final class CommandVoucherDefinition {

    private String id;
    private String displayName;

    private VoucherSpec voucher = new VoucherSpec();
    private List<String> commandsOnActivate = new ArrayList<>();

    public CommandVoucherDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public VoucherSpec getVoucher() { return voucher; }
    public void setVoucher(VoucherSpec voucher) { this.voucher = voucher; }

    public List<String> getCommandsOnActivate() { return commandsOnActivate; }
    public void setCommandsOnActivate(List<String> cmds) {
        this.commandsOnActivate = (cmds == null) ? new ArrayList<>() : cmds;
    }

    public void validate() {
        if (id == null || id.trim().isEmpty())
            throw new IllegalArgumentException("CommandVoucherDefinition.id is required");
        if (displayName == null || displayName.isEmpty()) displayName = id;
        if (voucher == null) voucher = new VoucherSpec();
        voucher.validate(id);
        if (commandsOnActivate == null) commandsOnActivate = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "CommandVoucherDefinition{id='" + id + "', displayName='" + displayName + "'}";
    }

    public static final class VoucherSpec {
        private String itemId;
        private String name;
        private List<String> lore = new ArrayList<>();

        public VoucherSpec() {}

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = (lore == null) ? new ArrayList<>() : lore; }

        public void validate(String parentId) {
            if (name == null || name.trim().isEmpty()) {
                name = "[" + parentId.toUpperCase() + "] Command Voucher #{voucherIdShort}";
            }
            if (lore == null) lore = new ArrayList<>();
        }
    }
}
