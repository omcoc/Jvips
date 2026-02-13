package br.com.julio.jvips.core.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Estado do baú virtual de um jogador.
 * Serializado no vipschest.json.
 */
public final class PlayerChestState {

    private int capacity;  // tamanho máximo do baú quando foi salvo
    private Map<String, ChestItem> items;  // slot index (string) -> item

    public PlayerChestState() {
        this.capacity = 0;
        this.items = new HashMap<>();
    }

    public PlayerChestState(int capacity, Map<String, ChestItem> items) {
        this.capacity = capacity;
        this.items = (items != null) ? items : new HashMap<>();
    }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public Map<String, ChestItem> getItems() { return items; }
    public void setItems(Map<String, ChestItem> items) { this.items = (items != null) ? items : new HashMap<>(); }
}
