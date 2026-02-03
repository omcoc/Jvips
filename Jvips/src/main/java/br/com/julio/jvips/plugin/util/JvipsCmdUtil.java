package br.com.julio.jvips.plugin.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.lang.reflect.Method;
import java.util.UUID;

public final class JvipsCmdUtil {

    private JvipsCmdUtil() {}

    /** Permite console/system; player precisa jvips.admin */
    public static boolean hasAdmin(CommandContext ctx) {
        try {
            // tenta ctx.getSender()
            Object sender = invokeNoArg(ctx, "getSender");
            if (sender == null) return true;

            // se for Player diretamente (algumas builds expõem)
            try {
                Class<?> playerClass = Class.forName("com.hypixel.hytale.server.core.entity.entities.Player");
                if (playerClass.isInstance(sender)) {
                    Method hasPerm = playerClass.getMethod("hasPermission", String.class);
                    Object ok = hasPerm.invoke(sender, "jvips.admin");
                    return ok instanceof Boolean && (Boolean) ok;
                }
            } catch (Throwable ignored) {}

            // reflection: sender.hasPermission("jvips.admin")
            try {
                Method m = sender.getClass().getMethod("hasPermission", String.class);
                Object ok = m.invoke(sender, "jvips.admin");
                if (ok instanceof Boolean) return (Boolean) ok;
            } catch (Throwable ignored) {}

            // não é player => console/system => permitido
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Extrai argumento i do CommandContext (tenta vários formatos). */
    public static String arg(CommandContext ctx, int i) {
        // 1) ctx.getArgs() -> String[]
        try {
            Object a = invokeNoArg(ctx, "getArgs");
            if (a instanceof String[] arr) {
                return (i >= 0 && i < arr.length) ? arr[i] : null;
            }
        } catch (Throwable ignored) {}

        // 2) ctx.getArguments() -> List<String>
        try {
            Object a = invokeNoArg(ctx, "getArguments");
            if (a instanceof java.util.List<?> list) {
                if (i >= 0 && i < list.size()) {
                    Object v = list.get(i);
                    return (v != null) ? v.toString() : null;
                }
            }
        } catch (Throwable ignored) {}

        // 3) ctx.getArgument(i)
        try {
            Method m = ctx.getClass().getMethod("getArgument", int.class);
            Object v = m.invoke(ctx, i);
            return v != null ? v.toString() : null;
        } catch (Throwable ignored) {}

        return null;
    }

    /** Aceita UUID direto; se for nome, por enquanto retorna null (melhor prática: usar UUID). */
    public static String resolveUuidBestEffort(String playerOrUuid) {
        if (playerOrUuid == null) return null;

        // UUID direto
        try {
            UUID u = UUID.fromString(playerOrUuid);
            return u.toString();
        } catch (Throwable ignored) {}

        // Nesta etapa, sem API garantida de offline lookup.
        // Recomendação: no CurseForge, documentar que add/remove aceitam UUID (ou player online se você implementar lookup depois).
        return null;
    }

    private static Object invokeNoArg(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }
}
