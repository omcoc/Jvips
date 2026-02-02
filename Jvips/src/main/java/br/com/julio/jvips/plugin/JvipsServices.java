package br.com.julio.jvips.plugin;

import br.com.julio.jvips.core.JvipsCoreFacade;

public final class JvipsServices {

    private static volatile JvipsPlugin plugin;

    private JvipsServices() {}

    public static void setPlugin(JvipsPlugin p) {
        plugin = p;
    }

    // Mantive o seu nome original, mas agora consistente
    public static JvipsPlugin plugin() {
        return plugin;
    }

    // Getter “padrão” (era o que o Interaction estava tentando chamar)
    public static JvipsPlugin getPlugin() {
        return plugin;
    }

    public static JvipsCoreFacade core() {
        JvipsPlugin p = plugin;
        return (p == null) ? null : p.getCore();
    }
}
