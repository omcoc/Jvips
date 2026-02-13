package br.com.julio.jvips.core.items;

import br.com.julio.jvips.core.CommandTemplate;
import br.com.julio.jvips.core.model.VipDefinition;
import br.com.julio.jvips.core.util.DurationFormatter;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoucherItemFactory {

    private VoucherItemFactory() {}

    public static ItemStack create(
            String itemId,
            VipDefinition vip,
            String playerName,
            String issuedToUuid,
            String voucherId,
            long issuedAt,
            String signature
    ) {
        return create(itemId, vip, playerName, issuedToUuid, voucherId, issuedAt, signature, 0);
    }

    public static ItemStack create(
            String itemId,
            VipDefinition vip,
            String playerName,
            String issuedToUuid,
            String voucherId,
            long issuedAt,
            String signature,
            long customDurationSeconds
    ) {
        BsonDocument meta = new BsonDocument();

        // =============================
        // METADATA FUNCIONAL (server)
        // =============================
        meta.append("jvips:type", new BsonString("vip_voucher"));
        meta.append("jvips:vipId", new BsonString(vip.getId()));
        meta.append("jvips:issuedTo", new BsonString(issuedToUuid));
        meta.append("jvips:voucherId", new BsonString(voucherId));
        meta.append("jvips:issuedAt", new BsonInt64(issuedAt));
        meta.append("jvips:sig", new BsonString(signature));

        // Duração custom (0 = padrão do vips.json)
        if (customDurationSeconds > 0) {
            meta.append("jvips:customDuration", new BsonInt64(customDurationSeconds));
        }

        // =============================
        // TranslationProperties
        // =============================
        BsonDocument tp = new BsonDocument();
        tp.append("Name", new BsonString("jvips.items.voucher.name"));
        tp.append("Description", new BsonString("jvips.items.voucher.description"));
        meta.append("TranslationProperties", tp);

        // =============================
        // Lore / display_name
        // =============================
        long effectiveDuration = customDurationSeconds > 0 ? customDurationSeconds : vip.getDurationSeconds();

        Map<String, String> vars = new HashMap<>();
        vars.put("player", playerName);
        vars.put("voucherIdShort", safeShort(voucherId));
        vars.put("durationHuman", DurationFormatter.format(effectiveDuration));

        if (vip.getVoucher() != null && vip.getVoucher().getName() != null) {
            meta.append("display_name", new BsonString(CommandTemplate.apply(vip.getVoucher().getName(), vars)));
        }

        BsonArray loreArray = new BsonArray();
        List<String> lore = (vip.getVoucher() != null) ? vip.getVoucher().getLore() : null;
        if (lore != null) {
            for (String line : lore) {
                loreArray.add(new BsonString(CommandTemplate.apply(line, vars)));
            }
        }
        meta.append("lore", loreArray);

        return new ItemStack(itemId, 1, meta);
    }

    private static String safeShort(String id) {
        if (id == null) return "--------";
        return id.length() >= 8 ? id.substring(0, 8) : id;
    }
}
