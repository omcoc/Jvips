package br.com.julio.jvips.core.text;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.MaybeBool;
import com.hypixel.hytale.server.core.Message;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte strings com markup estilo Hytale (ex.: <#FFD700><b>Texto</b></#FFD700>,
 * <gold>, <DARK_PURPLE>, <gradient:#e89d1f:#e4e95d>Texto</gradient>) para {@link Message}.
 *
 * Importante: o chat NÃO interpreta tags automaticamente quando você usa Message.raw().
 * Você precisa gerar um Message com FormattedMessage(s) (ou equivalente), igual plugins como EssentialsPlus.
 *
 * Tags suportadas (o que a API expõe via FormattedMessage):
 *  - Cores: <#RRGGBB>...</#RRGGBB> e nomes (ex.: <gold>, <dark_purple>, <DARK_PURPLE>)
 *  - Estilos: <b>/<bold>, <i>/<italic>, <u>/<underline>, <mono>/<monospace>
 *  - Gradiente: <gradient:#a:#b[:#c...]>Texto</gradient> (cor por caractere)
 *
 * Tags não suportadas pela API (ex.: strikethrough/obfuscated) são aceitas e ignoradas (não quebram o texto).
 */
public final class JvipsTextParser {

    private static final Pattern TAG = Pattern.compile("<(/?)([^>]+)>");

    private static final Map<String, Color> NAMED = new HashMap<>();

    static {
        // Paleta “clássica” (16 cores) + alguns aliases
        NAMED.put("black", rgb(0, 0, 0));

        NAMED.put("dark_blue", rgb(0, 0, 170));
        NAMED.put("darkblue", rgb(0, 0, 170));

        NAMED.put("dark_green", rgb(0, 170, 0));
        NAMED.put("darkgreen", rgb(0, 170, 0));

        NAMED.put("dark_aqua", rgb(0, 170, 170));
        NAMED.put("darkaqua", rgb(0, 170, 170));
        NAMED.put("dark_cyan", rgb(0, 170, 170));
        NAMED.put("darkcyan", rgb(0, 170, 170));

        NAMED.put("dark_red", rgb(170, 0, 0));
        NAMED.put("darkred", rgb(170, 0, 0));

        NAMED.put("dark_purple", rgb(170, 0, 170));
        NAMED.put("darkpurple", rgb(170, 0, 170));
        NAMED.put("purple", rgb(170, 0, 170));

        NAMED.put("gold", rgb(255, 170, 0));
        NAMED.put("orange", rgb(255, 170, 0));

        NAMED.put("gray", rgb(170, 170, 170));
        NAMED.put("grey", rgb(170, 170, 170));

        NAMED.put("dark_gray", rgb(85, 85, 85));
        NAMED.put("dark_grey", rgb(85, 85, 85));
        NAMED.put("darkgray", rgb(85, 85, 85));
        NAMED.put("darkgrey", rgb(85, 85, 85));

        NAMED.put("blue", rgb(85, 85, 255));
        NAMED.put("green", rgb(85, 255, 85));
        NAMED.put("aqua", rgb(85, 255, 255));
        NAMED.put("cyan", rgb(85, 255, 255));

        NAMED.put("red", rgb(255, 85, 85));

        NAMED.put("light_purple", rgb(255, 85, 255));
        NAMED.put("lightpurple", rgb(255, 85, 255));
        NAMED.put("magenta", rgb(255, 85, 255));

        NAMED.put("yellow", rgb(255, 255, 85));
        NAMED.put("white", rgb(255, 255, 255));
    }

    private JvipsTextParser() {}

    public static Message parseToMessage(String input) {
        if (input == null || input.isEmpty()) {
            return Message.raw("");
        }

        List<Segment> segments = parseToSegments(input);

        if (segments.isEmpty()) {
            return Message.raw("");
        }

        List<Message> msgs = new ArrayList<>();
        for (Segment s : segments) {
            if (s.text == null || s.text.isEmpty()) continue;
            msgs.add(toMessage(s));
        }

        if (msgs.isEmpty()) {
            return Message.raw("");
        }

        return joinAsSingleMessage(msgs);
}

    
/**
 * Alguns sistemas de UI (ex.: EventTitle) não renderizam corretamente Message.join(...).
 * Eles esperam um único FormattedMessage com "extras/children" anexados.
 * Aqui tentamos construir um root FormattedMessage e anexar os segmentos como extras via reflexão.
 * Se não for possível (diferenças de versão), caímos no join padrão.
 */
private static Message joinAsSingleMessage(List<Message> parts) {
    if (parts == null || parts.isEmpty()) return Message.raw("");
    if (parts.size() == 1) return parts.get(0);

    try {
        // Coleta os FormattedMessage internos de cada Message
        List<FormattedMessage> children = new ArrayList<>();
        for (Message m : parts) {
            FormattedMessage fm = extractFormattedMessage(m);
            if (fm != null) children.add(fm);
        }
        if (children.isEmpty()) {
            return Message.join(parts.toArray(new Message[0]));
        }

        // IMPORTANTE:
        // Algumas UIs (EventTitle) ignoram mensagens cujo root.rawText está vazio.
        // Então usamos o PRIMEIRO segmento como root (com texto), e anexamos o resto como "extras/children".
        FormattedMessage root = children.remove(0);

        if (!children.isEmpty()) {
            if (!attachChildren(root, children)) {
                return Message.join(parts.toArray(new Message[0]));
            }
        }

        return new Message(root);
    } catch (Throwable t) {
        return Message.join(parts.toArray(new Message[0]));
    }
}

private static FormattedMessage extractFormattedMessage(Message msg) {
    if (msg == null) return null;

    // Caso a classe Message exponha direto (método/field), tentamos por reflexão.
    try {
        // 1) método zero-arg que retorna FormattedMessage
        for (java.lang.reflect.Method m : msg.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && FormattedMessage.class.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                Object r = m.invoke(msg);
                if (r instanceof FormattedMessage) return (FormattedMessage) r;
            }
        }
    } catch (Throwable ignored) {}

    try {
        // 2) field do tipo FormattedMessage
        for (java.lang.reflect.Field f : msg.getClass().getDeclaredFields()) {
            if (FormattedMessage.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object r = f.get(msg);
                if (r instanceof FormattedMessage) return (FormattedMessage) r;
            }
        }
    } catch (Throwable ignored) {}

    // 3) fallback: não conseguimos extrair; não anexamos
    return null;
}

private static boolean attachChildren(FormattedMessage root, List<FormattedMessage> children) {
    // Field names comuns entre versões
    String[] candidates = {"extras", "extra", "children", "components", "siblings"};
    Class<?> c = root.getClass();

    for (String name : candidates) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            Class<?> t = f.getType();

            if (java.util.List.class.isAssignableFrom(t)) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> list = (java.util.List<Object>) f.get(root);
                if (list == null) {
                    list = new java.util.ArrayList<>();
                    f.set(root, list);
                }
                list.addAll(children);
                return true;
            }

            if (t.isArray() && FormattedMessage.class.isAssignableFrom(t.getComponentType())) {
                FormattedMessage[] arr = children.toArray(new FormattedMessage[0]);
                f.set(root, arr);
                return true;
            }
        } catch (Throwable ignored) {}
    }

    // Se não houver campo com nome conhecido, tentamos qualquer field List que aceite FormattedMessage
    try {
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            f.setAccessible(true);
            Class<?> t = f.getType();
            if (!java.util.List.class.isAssignableFrom(t)) continue;

            Object cur = f.get(root);
            if (cur == null) {
                java.util.ArrayList<Object> list = new java.util.ArrayList<>();
                list.addAll(children);
                f.set(root, list);
                return true;
            } else if (cur instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> list = (java.util.List<Object>) cur;
                list.addAll(children);
                return true;
            }
        }
    } catch (Throwable ignored) {}

    return false;
}

private static List<Segment> parseToSegments(String text) {
        List<Segment> out = new ArrayList<>();

        Deque<Style> stack = new ArrayDeque<>();
        Style cur = Style.DEFAULT;

        Deque<GradientContext> gradients = new ArrayDeque<>();

        Matcher m = TAG.matcher(text);
        int pos = 0;

        while (m.find()) {
            if (m.start() > pos) {
                String chunk = text.substring(pos, m.start());
                appendText(out, gradients, chunk, cur);
            }

            boolean closing = !m.group(1).isEmpty();
            String tagRaw = m.group(2).trim();

            if (closing) {
                // fecha gradient (se for o caso) antes de restaurar estilo
                if (tagRaw.equalsIgnoreCase("gradient")) {
                    flushGradient(out, gradients);
                }

                if (!stack.isEmpty()) {
                    cur = stack.pop();
                } else {
                    cur = Style.DEFAULT;
                }
            } else {
                String low = tagRaw.toLowerCase(Locale.ROOT);

                // reset (abre e já reseta tudo)
                if (low.equals("r") || low.equals("reset")) {
                    stack.clear();
                    gradients.clear();
                    cur = Style.DEFAULT;
                    pos = m.end();
                    continue;
                }

                // abre tag: salva estado e aplica mudanças
                stack.push(cur);

                if (low.startsWith("gradient:")) {
                    // gradient context não altera estilo; só muda a cor por caractere do texto até </gradient>
                    String stopsRaw = tagRaw.substring("gradient:".length());
                    gradients.push(new GradientContext(parseGradientStops(stopsRaw)));
                    // cur permanece, mas texto dentro do gradiente pode ser afetado por outras tags (bold/italic/underline/mono)
                } else {
                    cur = applyOpenTag(cur, tagRaw);
                }
            }

            pos = m.end();
        }

        if (pos < text.length()) {
            appendText(out, gradients, text.substring(pos), cur);
        }

        // Se houver gradients não fechados, flush para não perder texto.
        while (!gradients.isEmpty()) {
            flushGradient(out, gradients);
        }

        // Merge final (reduz nº de segmentos)
        return mergeAdjacent(out);
    }

    private static void appendText(List<Segment> out, Deque<GradientContext> gradients, String chunk, Style style) {
        if (chunk == null || chunk.isEmpty()) return;

        if (gradients.isEmpty()) {
            out.add(new Segment(chunk, style));
            return;
        }

        // Dentro de gradient: guardamos caractere a caractere com o estilo atual (sem cor; a cor vem do gradiente)
        GradientContext g = gradients.peek();
        for (int i = 0; i < chunk.length(); i++) {
            g.addChar(chunk.charAt(i), style);
        }
    }

    private static void flushGradient(List<Segment> out, Deque<GradientContext> gradients) {
        GradientContext g = gradients.poll(); // remove o topo
        if (g == null) return;

        List<CharStyle> chars = g.chars;
        int n = chars.size();
        if (n == 0) return;

        // Se não houver stops válidos, “vira texto normal”
        if (g.stops.isEmpty()) {
            StringBuilder sb = new StringBuilder(n);
            for (CharStyle cs : chars) sb.append(cs.ch);
            out.add(new Segment(sb.toString(), Style.DEFAULT));
            return;
        }

        // cria segmentos (merge durante a geração)
        SegmentBuilder builder = new SegmentBuilder();
        for (int i = 0; i < n; i++) {
            CharStyle cs = chars.get(i);
            Color c = gradientColorAt(g.stops, i, n);

            Style s = cs.style.withColor(c);
            builder.append(out, String.valueOf(cs.ch), s);
        }
        builder.flush(out);
    }

    private static List<Segment> mergeAdjacent(List<Segment> in) {
        if (in.isEmpty()) return in;

        List<Segment> out = new ArrayList<>(in.size());
        Segment prev = null;

        for (Segment s : in) {
            if (s.text == null || s.text.isEmpty()) continue;

            if (prev != null && prev.style.equals(s.style)) {
                prev = new Segment(prev.text + s.text, prev.style);
                out.set(out.size() - 1, prev);
            } else {
                out.add(s);
                prev = s;
            }
        }
        return out;
    }

    private static Style applyOpenTag(Style base, String tagRaw) {
        String tag = tagRaw.trim();
        String low = tag.toLowerCase(Locale.ROOT);

        // estilos suportados pela API
        if (low.equals("b") || low.equals("bold")) return base.withBold(true);
        if (low.equals("i") || low.equals("italic")) return base.withItalic(true);
        if (low.equals("u") || low.equals("underline") || low.equals("underlined")) return base.withUnderlined(true);
        if (low.equals("mono") || low.equals("monospace")) return base.withMonospace(true);

        // estilos “aceitos” mas ignorados (API não expõe)
        if (low.equals("s") || low.equals("strikethrough") || low.equals("strike")) return base;
        if (low.equals("obf") || low.equals("obfuscated") || low.equals("magic")) return base;

        // cor hex (aceita tanto "#rrggbb" quanto "rrggbb")
        if (low.startsWith("#") && low.length() == 7) {
            return base.withColor(parseHex(low));
        }
        if (low.length() == 6 && low.matches("[0-9a-f]{6}")) {
            return base.withColor(parseHex("#" + low));
        }

        // cores nomeadas: inclui DARK_PURPLE etc.
        Color named = NAMED.get(low);
        if (named == null) named = NAMED.get(low.replace(' ', '_'));
        if (named != null) return base.withColor(named);

        return base;
    }

    private static Message toMessage(Segment seg) {
        FormattedMessage fm = new FormattedMessage();
        fm.rawText = seg.text;

        if (seg.style.color != null) fm.color = toHex(seg.style.color);

        if (seg.style.bold) fm.bold = MaybeBool.True;
        if (seg.style.italic) fm.italic = MaybeBool.True;
        if (seg.style.underlined) fm.underlined = MaybeBool.True;
        if (seg.style.monospace) fm.monospace = MaybeBool.True;

        return new Message(fm);
    }

    private static List<Color> parseGradientStops(String colorsRaw) {
        List<Color> stops = new ArrayList<>();
        if (colorsRaw == null) return stops;

        String[] parts = colorsRaw.split(":");
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty()) continue;
            if (!s.startsWith("#")) continue;
            if (s.length() != 7) continue;
            Color c = parseHex(s.toLowerCase(Locale.ROOT));
            if (c != null) stops.add(c);
        }
        return stops;
    }

    private static Color gradientColorAt(List<Color> stops, int index, int length) {
        if (stops.size() == 1) return stops.get(0);

        float t = (length <= 1) ? 0f : (index / (float) (length - 1));
        float step = 1f / (stops.size() - 1);

        int seg = Math.min((int) (t / step), stops.size() - 2);
        float localT = (t - (seg * step)) / step;

        Color a = stops.get(seg);
        Color b = stops.get(seg + 1);

        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * localT);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * localT);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * localT);

        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static Color parseHex(String hex) {
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return new Color(r, g, b);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r, g, b);
    }

    private static final class Segment {
        final String text;
        final Style style;

        Segment(String text, Style style) {
            this.text = text;
            this.style = style;
        }
    }

    private static final class Style {
        static final Style DEFAULT = new Style(null, false, false, false, false);

        final Color color;
        final boolean bold;
        final boolean italic;
        final boolean underlined;
        final boolean monospace;

        Style(Color color, boolean bold, boolean italic, boolean underlined, boolean monospace) {
            this.color = color;
            this.bold = bold;
            this.italic = italic;
            this.underlined = underlined;
            this.monospace = monospace;
        }

        Style withColor(Color c) { return new Style(c, bold, italic, underlined, monospace); }
        Style withBold(boolean v) { return new Style(color, v, italic, underlined, monospace); }
        Style withItalic(boolean v) { return new Style(color, bold, v, underlined, monospace); }
        Style withUnderlined(boolean v) { return new Style(color, bold, italic, v, monospace); }
        Style withMonospace(boolean v) { return new Style(color, bold, italic, underlined, v); }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Style)) return false;
            Style s = (Style) o;
            return eq(color, s.color)
                    && bold == s.bold
                    && italic == s.italic
                    && underlined == s.underlined
                    && monospace == s.monospace;
        }

        @Override
        public int hashCode() {
            int h = 17;
            h = h * 31 + (color == null ? 0 : color.getRGB());
            h = h * 31 + (bold ? 1 : 0);
            h = h * 31 + (italic ? 1 : 0);
            h = h * 31 + (underlined ? 1 : 0);
            h = h * 31 + (monospace ? 1 : 0);
            return h;
        }

        private static boolean eq(Color a, Color b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return a.getRGB() == b.getRGB();
        }
    }

    private static final class CharStyle {
        final char ch;
        final Style style;

        CharStyle(char ch, Style style) {
            this.ch = ch;
            // cor será aplicada pelo gradiente; preserva flags e monospace
            this.style = new Style(null, style.bold, style.italic, style.underlined, style.monospace);
        }
    }

    private static final class GradientContext {
        final List<Color> stops;
        final List<CharStyle> chars = new ArrayList<>(64);

        GradientContext(List<Color> stops) {
            this.stops = (stops != null) ? stops : new ArrayList<>();
        }

        void addChar(char ch, Style style) {
            chars.add(new CharStyle(ch, style));
        }
    }

    private static final class SegmentBuilder {
        private Style style;
        private Color color;
        private boolean has;
        private final StringBuilder sb = new StringBuilder(64);

        void append(List<Segment> out, String text, Style s) {
            if (text == null || text.isEmpty()) return;

            if (!has) {
                has = true;
                style = s;
                sb.append(text);
                return;
            }

            if (style.equals(s)) {
                sb.append(text);
                return;
            }

            flush(out);
            has = true;
            style = s;
            sb.append(text);
        }

        void flush(List<Segment> out) {
            if (!has) return;
            out.add(new Segment(sb.toString(), style));
            sb.setLength(0);
            has = false;
            style = null;
        }
    }

/**
 * Remove tags de markup (ex.: <#FFF>, </#FFF>, <b>, <gradient:...>, etc) retornando apenas texto.
 * Útil para locais onde a UI não suporta mensagens ricas (ex.: alguns EventTitle).
 */
public static String stripMarkup(String s) {
    if (s == null) return "";
    // Remove qualquer tag <...>
    return s.replaceAll("<[^>]+>", "");
}

}
