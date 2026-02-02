package br.com.julio.jvips.core;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface CommandDispatcher {
    CompletableFuture<Void> dispatchAsConsole(String rawCmd);
}
