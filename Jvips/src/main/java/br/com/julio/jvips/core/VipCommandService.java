package br.com.julio.jvips.core;

import br.com.julio.jvips.core.model.VipDefinition;

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
        return runCommandsSequential(vip.getCommandsOnActivate(), playerName);
    }

    public CompletableFuture<Void> runExpireCommands(VipDefinition vip, String playerName) {
        if (vip == null) return CompletableFuture.completedFuture(null);
        return runCommandsSequential(vip.getCommandsOnExpire(), playerName);
    }

    private CompletableFuture<Void> runCommandsSequential(List<String> commands, String playerName) {
        if (commands == null || commands.isEmpty()) return CompletableFuture.completedFuture(null);

        Map<String, String> vars = new HashMap<>();
        vars.put("player", playerName);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (String cmd : commands) {
            String resolved = CommandTemplate.apply(cmd, vars);
            if (resolved == null || resolved.trim().isEmpty()) continue;

            chain = chain.thenCompose(v -> dispatcher.dispatchAsConsole(resolved));
        }

        return chain;
    }
}
