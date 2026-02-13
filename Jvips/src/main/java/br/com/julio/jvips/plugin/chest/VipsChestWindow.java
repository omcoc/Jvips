package br.com.julio.jvips.plugin.chest;

import com.google.gson.JsonObject;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Janela do baú virtual VIP.
 * Container tem tamanho da permissão.
 * Ao fechar, salva via merge (preserva itens ocultos no JSON).
 */
public final class VipsChestWindow extends Window implements ItemContainerWindow {

    private final ItemContainer container;
    private final int visibleCapacity;
    private final int rows;
    private final String playerUuid;
    private final VipsChestManager manager;

    public VipsChestWindow(ItemContainer container, int visibleCapacity, int rows,
                           String playerUuid, VipsChestManager manager) {
        super(WindowType.Container);
        this.container = container;
        this.visibleCapacity = visibleCapacity;
        this.rows = rows;
        this.playerUuid = playerUuid;
        this.manager = manager;
    }

    @Nonnull
    @Override
    public JsonObject getData() {
        JsonObject data = new JsonObject();
        data.addProperty("rows", rows);
        return data;
    }

    @Override
    protected boolean onOpen0(Ref<EntityStore> ref, Store<EntityStore> store) {
        return true;
    }

    @Override
    protected void onClose0(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        try {
            manager.saveWithMerge(playerUuid, container, visibleCapacity);
        } catch (Exception e) {
            System.err.println("[Jvips] Failed to save chest for " + playerUuid + ": " + e.getMessage());
        }
    }

    @Override
    public ItemContainer getItemContainer() {
        return container;
    }
}
