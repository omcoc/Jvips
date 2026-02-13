package br.com.julio.jvips.core;

import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.util.DurationFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class VipCommandService {

    private final CommandDispatcher dispatcher;

    public VipCommandService(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public CompletableFuture<Void> runActivateCommands(VipDefinition vip, String playerName) {
        if (vip == null) return CompletableFuture.completedFuture(null);
        Map<String, String> vars = baseVars(vip, playerName);
        return runCommandsSequential(vip.getCommandsOnActivate(), vars);
    }

    public CompletableFuture<Void> runExpireCommands(VipDefinition vip, String playerName) {
        if (vip == null) return CompletableFuture.completedFuture(null);
        Map<String, String> vars = baseVars(vip, playerName);
        return runCommandsSequential(vip.getCommandsOnExpire(), vars);
    }

    private Map<String, String> baseVars(VipDefinition vip, String playerName) {
        Map<String, String> vars = new HashMap<>();
        vars.put("player", safe(playerName));
        vars.put("vipId", safe(vip.getId()));
        vars.put("vipDisplay", safe(vip.getDisplayName()));
        vars.put("durationSeconds", String.valueOf(vip.getDurationSeconds()));
        vars.put("durationHuman", DurationFormatter.format(vip.getDurationSeconds()));
        return vars;
    }

    private CompletableFuture<Void> runCommandsSequential(List<String> commands, Map<String, String> vars) {
        if (commands == null || commands.isEmpty()) return CompletableFuture.completedFuture(null);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (String cmd : commands) {
            String resolved = CommandTemplate.apply(cmd, vars);
            if (resolved == null || resolved.trim().isEmpty()) continue;
            chain = chain.thenCompose(v -> dispatcher.dispatchAsConsole(resolved));
        }

        return chain;
    }

    /**
     * Despacha um único comando já resolvido (sem template vars).
     * Usado pelo CommandVoucher que já faz replace de {player} internamente.
     */
    public CompletableFuture<Void> dispatchCommand(String resolvedCommand) {
        if (resolvedCommand == null || resolvedCommand.trim().isEmpty())
            return CompletableFuture.completedFuture(null);
        return dispatcher.dispatchAsConsole(resolvedCommand);
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }
}
