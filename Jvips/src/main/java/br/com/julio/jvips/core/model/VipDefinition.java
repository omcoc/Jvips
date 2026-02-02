package br.com.julio.jvips.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VipDefinition {
    private String id;                 // ex: "thorium"
    private String displayName;         // ex: "[THORIUM]"
    private long durationSeconds;       // ex: 2592000

    private VoucherSpec voucher = new VoucherSpec();
    private List<String> commandsOnActivate = new ArrayList<>();
    private List<String> commandsOnExpire = new ArrayList<>();

    public VipDefinition() {}

    public VipDefinition(String id, String displayName, long durationSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.durationSeconds = durationSeconds;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public VoucherSpec getVoucher() { return voucher; }
    public void setVoucher(VoucherSpec voucher) { this.voucher = voucher; }

    public String getVoucherItemId() {
        if (voucher == null) return null;
        return voucher.getItemId();
    }

    public String getVoucherName() {
        return voucher == null ? null : voucher.getName();
    }

    public List<String> getVoucherLore() {
        return voucher == null ? List.of() : voucher.getLore();
    }

    public List<String> getCommandsOnActivate() { return commandsOnActivate; }
    public void setCommandsOnActivate(List<String> commandsOnActivate) {
        this.commandsOnActivate = (commandsOnActivate == null) ? new ArrayList<>() : commandsOnActivate;
    }

    public List<String> getCommandsOnExpire() { return commandsOnExpire; }
    public void setCommandsOnExpire(List<String> commandsOnExpire) {
        this.commandsOnExpire = (commandsOnExpire == null) ? new ArrayList<>() : commandsOnExpire;
    }

    public void validate() {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("VipDefinition.id is required");
        if (displayName == null) displayName = id;
        if (durationSeconds <= 0) throw new IllegalArgumentException("VipDefinition.durationSeconds must be > 0 for " + id);
        if (voucher == null) voucher = new VoucherSpec();
        voucher.validate(id);
    }

    @Override
    public String toString() {
        return "VipDefinition{id='" + id + "', displayName='" + displayName + "', durationSeconds=" + durationSeconds + "}";
    }

    public static final class VoucherSpec {
        private String itemId; // ex: "Jvips_Voucher"
        private String name;
        private List<String> lore = new ArrayList<>();

        public VoucherSpec() {}

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) {
            this.lore = (lore == null) ? new ArrayList<>() : lore;
        }

        public void validate(String vipId) {
            // itemId pode vir do defaults; aqui só validamos o que for fornecido
            if (name == null || name.trim().isEmpty()) {
                // Nome padrão se não vier no JSON
                name = "[" + vipId.toUpperCase() + "] Voucher #{voucherIdShort}";
            }
            if (lore == null) lore = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "VoucherSpec{itemId='" + itemId + "', name='" + name + "', loreSize=" + (lore == null ? 0 : lore.size()) + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VoucherSpec)) return false;
            VoucherSpec that = (VoucherSpec) o;
            return Objects.equals(itemId, that.itemId) &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(lore, that.lore);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, name, lore);
        }
    }
}
