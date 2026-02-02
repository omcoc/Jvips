package br.com.julio.jvips.plugin.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.query.Query;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.bson.BsonDocument;
import org.bson.BsonValue;

public final class VoucherDropBlockSystem extends EntityEventSystem<EntityStore, DropItemEvent.PlayerRequest> {

    private static final String META_TYPE = "jvips:type";
    private static final String TYPE_VIP_VOUCHER = "vip_voucher";

    public VoucherDropBlockSystem() {
        super(DropItemEvent.PlayerRequest.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any(); // simples e funciona; dentro do handle você já filtra Player == null
    }

    @Override
    public void handle(
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commands,
            DropItemEvent.PlayerRequest event
    ) {
        // Pega o Player do entity que disparou o evento
        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        Inventory inv = player.getInventory();
        if (inv == null) return;

        ItemContainer container = inv.getSectionById(event.getInventorySectionId());
        if (container == null) return;

        ItemStack stack = container.getItemStack(event.getSlotId());
        if (stack == null || stack.isEmpty()) return;

        if (isVipVoucher(stack)) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("Você não pode dropar este voucher."));
        }
    }

    private boolean isVipVoucher(ItemStack stack) {
        // Observação: sua versão marca getMetadata() como deprecated, mas ainda funciona.
        BsonDocument meta = stack.getMetadata();
        if (meta == null) return false;

        BsonValue v = meta.get(META_TYPE);
        if (v == null || !v.isString()) return false;

        return TYPE_VIP_VOUCHER.equals(v.asString().getValue());
    }
}
