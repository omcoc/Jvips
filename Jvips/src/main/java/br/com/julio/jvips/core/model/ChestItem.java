package br.com.julio.jvips.core.model;

/**
 * Representa um item serializado no vipschest.json.
 * Segue o formato de inventário do Hytale.
 */
public final class ChestItem {

    private String id;
    private int quantity;
    private double durability;
    private double maxDurability;
    private boolean overrideDroppedItemAnimation;

    // Metadata (BsonDocument serializado como string JSON para preservar dados extras)
    private String metadata;

    public ChestItem() {}

    public ChestItem(String id, int quantity, double durability, double maxDurability, boolean overrideDroppedItemAnimation, String metadata) {
        this.id = id;
        this.quantity = quantity;
        this.durability = durability;
        this.maxDurability = maxDurability;
        this.overrideDroppedItemAnimation = overrideDroppedItemAnimation;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getDurability() { return durability; }
    public void setDurability(double durability) { this.durability = durability; }

    public double getMaxDurability() { return maxDurability; }
    public void setMaxDurability(double maxDurability) { this.maxDurability = maxDurability; }

    public boolean isOverrideDroppedItemAnimation() { return overrideDroppedItemAnimation; }
    public void setOverrideDroppedItemAnimation(boolean overrideDroppedItemAnimation) { this.overrideDroppedItemAnimation = overrideDroppedItemAnimation; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
