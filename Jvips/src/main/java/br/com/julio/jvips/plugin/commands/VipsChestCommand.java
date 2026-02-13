package br.com.julio.jvips.plugin.commands;

import br.com.julio.jvips.core.text.JvipsTextParser;
import br.com.julio.jvips.plugin.JvipsPlugin;
import br.com.julio.jvips.plugin.chest.VipsChestManager;
import br.com.julio.jvips.plugin.chest.VipsChestWindow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.Page;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * /vips chest → abre o baú virtual VIP do jogador.
 *
 * Ao abrir:
 * 1) Dropa itens excedentes no chão (se a permissão diminuiu)
 * 2) Abre a GUI com o tamanho da permissão
 *
 * Ao fechar:
 * - Salva slots visíveis + preserva itens ocultos via merge
 */
public final class VipsChestCommand extends AbstractWorldCommand {

    private static final int[] CHEST_SIZES = { 54, 45, 36, 27, 18, 9 };

    private final JvipsPlugin plugin;

    public VipsChestCommand(JvipsPlugin plugin) {
        super("chest", "Opens your VIP virtual chest.");
        this.plugin = plugin;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store
    ) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.playerMustBeOnline", null)
            ));
            return;
        }

        Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
        if (playerRef == null || !playerRef.isValid()) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.playerMustBeOnline", null)
            ));
            return;
        }

        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.playerMustBeOnline", null)
            ));
            return;
        }

        CommandSender sender = ctx.sender();
        int chestSize = resolveChestSize(sender);

        if (chestSize <= 0) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("chest.noPermission", null)
            ));
            return;
        }

        String uuid = Objects.requireNonNull(player.getUuid()).toString();

        VipsChestManager chestManager = plugin.getCore().getChestManager();
        if (chestManager == null) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.chestUnavailable", null)
            ));
            return;
        }

        // ===== 1) Dropar itens excedentes no chão =====
        try {
            int dropped = chestManager.dropExcessItems(
                    uuid, chestSize, player, playerRef, store, world
            );

            if (dropped > 0) {
                System.out.println("[Jvips] Dropped " + dropped + " excess items for " + uuid);
            }
        } catch (Exception e) {
            System.err.println("[Jvips] Error dropping excess items: " + e.getMessage());
            // Continua mesmo com erro — melhor abrir o baú sem drop do que não abrir
        }

        // ===== 2) Criar container e abrir GUI =====
        ItemContainer container = chestManager.createViewContainer(uuid, chestSize);
        if (container == null) {
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.chestUnavailable", null)
            ));
            return;
        }

        int rows = Math.max(1, chestSize / 9);

        VipsChestWindow window = new VipsChestWindow(
                container, chestSize, rows, uuid, chestManager
        );

        try {
            PageManager pageManager = player.getPageManager();
            if (pageManager == null) {
                ctx.sendMessage(JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.chestUnavailable", null)
                ));
                return;
            }

            boolean success = pageManager.setPageWithWindows(
                    playerRef, store, Page.Bench, true, window
            );

            if (!success) {
                ctx.sendMessage(JvipsTextParser.parseToMessage(
                        plugin.getMessages().format("error.chestUnavailable", null)
                ));
            }
        } catch (Exception e) {
            System.err.println("[Jvips] Failed to open chest for " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            ctx.sendMessage(JvipsTextParser.parseToMessage(
                    plugin.getMessages().format("error.chestUnavailable", null)
            ));
        }
    }

    private int resolveChestSize(CommandSender sender) {
        for (int size : CHEST_SIZES) {
            if (sender.hasPermission("jvips.chest." + size)) {
                return size;
            }
        }
        return 0;
    }
}
