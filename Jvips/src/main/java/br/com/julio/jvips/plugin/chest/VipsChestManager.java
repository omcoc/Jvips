package br.com.julio.jvips.plugin.chest;

import br.com.julio.jvips.core.model.ChestItem;
import br.com.julio.jvips.core.model.PlayerChestState;
import br.com.julio.jvips.core.storage.VipsChestStore;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.bson.BsonDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerencia criação, carregamento e persistência dos baús virtuais VIP.
 *
 * O JSON (vipschest.json) guarda até 54 slots.
 * O container da GUI tem o tamanho da permissão.
 * Ao abrir o baú, itens excedentes (slots acima da permissão) são dropados no chão.
 */
public final class VipsChestManager {

    public static final int MAX_STORAGE_SLOTS = 54;

    private final VipsChestStore store;

    public VipsChestManager(VipsChestStore store) {
        this.store = store;
    }

    public VipsChestStore getStore() {
        return store;
    }

    /**
     * Cria um container com o tamanho visível e carrega os itens do JSON.
     */
    public ItemContainer createViewContainer(String playerUuid, int visibleCapacity) {
        int cap = Math.max(9, Math.min(visibleCapacity, MAX_STORAGE_SLOTS));
        ItemContainer container = SimpleItemContainer.getNewContainer((short) cap);

        PlayerChestState state = store.getChest(playerUuid);
        if (state != null && state.getItems() != null) {
            for (Map.Entry<String, ChestItem> entry : state.getItems().entrySet()) {
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    if (slot < 0 || slot >= cap) continue;

                    ChestItem ci = entry.getValue();
                    if (ci == null || ci.getId() == null || ci.getId().isEmpty()) continue;

                    ItemStack stack = createItemStack(ci);
                    if (stack != null && !stack.isEmpty()) {
                        container.setItemStackForSlot((short) slot, stack);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return container;
    }

    /**
     * Salva o container fazendo MERGE: slots visíveis do container + slots ocultos do JSON.
     */
    public void saveWithMerge(String playerUuid, ItemContainer container, int visibleCapacity) {
        int cap = Math.max(9, Math.min(visibleCapacity, MAX_STORAGE_SLOTS));

        PlayerChestState previous = store.getChest(playerUuid);
        Map<String, ChestItem> previousItems = (previous != null && previous.getItems() != null)
                ? new HashMap<>(previous.getItems())
                : new HashMap<>();

        Map<String, ChestItem> mergedItems = new HashMap<>();

        // Slots visíveis: do container
        for (int slot = 0; slot < cap; slot++) {
            ItemStack stack = container.getItemStack((short) slot);
            if (stack != null && !stack.isEmpty()) {
                mergedItems.put(String.valueOf(slot), toChestItem(stack));
            }
        }

        // Slots ocultos: preserva do JSON anterior
        for (int slot = cap; slot < MAX_STORAGE_SLOTS; slot++) {
            String key = String.valueOf(slot);
            ChestItem hidden = previousItems.get(key);
            if (hidden != null && hidden.getId() != null && !hidden.getId().isEmpty()) {
                mergedItems.put(key, hidden);
            }
        }

        PlayerChestState state = new PlayerChestState();
        state.setCapacity(MAX_STORAGE_SLOTS);
        state.setItems(mergedItems);
        store.saveChest(playerUuid, state);
    }

    /**
     * Dropa itens excedentes no chão (slots >= visibleCapacity) e os remove do JSON.
     * Chamado ANTES de abrir a GUI quando o jogador teve redução de permissão.
     *
     * Usa ItemComponent.generateItemDrop para criar a entidade ECS e
     * adiciona ao mundo via CommandBuffer (obtido do Store via cast ou reflection).
     *
     * @return número de itens dropados
     */
    public int dropExcessItems(
            String playerUuid,
            int visibleCapacity,
            Player player,
            Ref<EntityStore> playerRef,
            Store<EntityStore> storeParam,
            com.hypixel.hytale.server.core.universe.world.World world
    ) {
        PlayerChestState state = this.store.getChest(playerUuid);
        if (state == null || state.getItems() == null) return 0;

        int cap = Math.max(9, Math.min(visibleCapacity, MAX_STORAGE_SLOTS));

        // Coletar itens excedentes
        List<String> slotsToRemove = new ArrayList<>();
        List<ItemStack> itemsToDrop = new ArrayList<>();

        for (int slot = cap; slot < MAX_STORAGE_SLOTS; slot++) {
            String key = String.valueOf(slot);
            ChestItem ci = state.getItems().get(key);
            if (ci == null || ci.getId() == null || ci.getId().isEmpty()) continue;

            ItemStack stack = createItemStack(ci);
            if (stack != null && !stack.isEmpty()) {
                itemsToDrop.add(stack);
                slotsToRemove.add(key);
            }
        }

        if (itemsToDrop.isEmpty()) return 0;

        // Obter posição do jogador
        TransformComponent transform = player.getTransformComponent();
        if (transform == null) {
            System.err.println("[Jvips] Cannot drop excess items: player has no TransformComponent");
            return 0;
        }

        Vector3d position = transform.getPosition();
        Vector3f velocity = new Vector3f(0f, 0.2f, 0f);

        // Store implements ComponentAccessor e tem addEntity — usamos direto
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                storeParam,  // Store implements ComponentAccessor
                itemsToDrop,
                position,
                velocity
        );

        if (holders == null || holders.length == 0) {
            System.err.println("[Jvips] generateItemDrops returned empty");
            return 0;
        }

        // Spawnar entidades no mundo via Store.addEntity
        int dropped = 0;
        for (Holder<EntityStore> holder : holders) {
            if (holder != null) {
                try {
                    storeParam.addEntity(holder, AddReason.SPAWN);
                    dropped++;
                } catch (Exception e) {
                    System.err.println("[Jvips] Failed to spawn dropped item: " + e.getMessage());
                }
            }
        }

        // Remover itens dropados do JSON
        if (dropped > 0) {
            for (int i = 0; i < Math.min(dropped, slotsToRemove.size()); i++) {
                state.getItems().remove(slotsToRemove.get(i));
            }
            this.store.saveChest(playerUuid, state);
        }

        return dropped;
    }

    // ========================= Utilitários =========================

    private ChestItem toChestItem(ItemStack stack) {
        ChestItem ci = new ChestItem();
        ci.setId(stack.getItemId());
        ci.setQuantity(stack.getQuantity());

        try {
            ci.setDurability(stack.getDurability());
            ci.setMaxDurability(stack.getMaxDurability());
        } catch (Exception ignored) {
            ci.setDurability(0.0);
            ci.setMaxDurability(0.0);
        }

        ci.setOverrideDroppedItemAnimation(false);

        try {
            org.bson.BsonDocument meta = stack.getMetadata();
            if (meta != null && !meta.isEmpty()) {
                ci.setMetadata(meta.toJson());
            }
        } catch (Exception ignored) {}

        return ci;
    }

    ItemStack createItemStack(ChestItem ci) {
        ItemStack stack;

        if (ci.getMetadata() != null && !ci.getMetadata().trim().isEmpty()) {
            try {
                BsonDocument md = BsonDocument.parse(ci.getMetadata());
                stack = new ItemStack(ci.getId(), ci.getQuantity(), md);
            } catch (Throwable ignored) {
                stack = new ItemStack(ci.getId(), ci.getQuantity());
            }
        } else {
            stack = new ItemStack(ci.getId(), ci.getQuantity());
        }

        try {
            if (ci.getMaxDurability() > 0) {
                stack = stack.withMaxDurability(ci.getMaxDurability());
                stack = stack.withDurability(ci.getDurability());
            }
        } catch (Throwable ignored) {}

        return stack;
    }
}
