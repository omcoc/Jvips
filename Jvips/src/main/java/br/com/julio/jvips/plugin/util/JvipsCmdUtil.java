package br.com.julio.jvips.plugin.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


import java.util.UUID;

public final class JvipsCmdUtil {

    private JvipsCmdUtil() {}

    /** Permite console/system; player precisa jvips.admin */
    public static boolean hasAdmin(CommandContext ctx) {
        // Se não for player (ex: console), permite
        if (!ctx.isPlayer()) {
            return true;
        }
        // Se for player, verifica permissão
        CommandSender sender = ctx.sender();
        return sender.hasPermission("jvips.admin");
    }

    /** Aceita UUID direto; se for nome, por enquanto retorna null. */
    public static String resolveUuidBestEffort(String playerOrUuid) {
        if (playerOrUuid == null) return null;

        try {
            UUID u = UUID.fromString(playerOrUuid);
            return u.toString();
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Resolve o executor do comando para PlayerRef.
     * Retorna null se for console/system.
     */
    public static Ref<EntityStore> resolvePlayerRef(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            return null;
        }
        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        return (ref != null && ref.isValid()) ? ref : null;
    }
}
