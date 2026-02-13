package br.com.julio.jvips.core.items;

import br.com.julio.jvips.core.CommandTemplate;
import br.com.julio.jvips.core.model.CommandVoucherDefinition;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cria itens para Command Vouchers.
 * Metadata: jvips:type = "command_voucher" (diferencia do vip_voucher)
 */
public final class CommandVoucherItemFactory {

    private CommandVoucherItemFactory() {}

    public static ItemStack create(
            CommandVoucherDefinition def,
            String playerName,
            String issuedToUuid,
            String voucherId,
            long issuedAt
    ) {
        String itemId = (def.getVoucher() != null && def.getVoucher().getItemId() != null
                && !def.getVoucher().getItemId().trim().isEmpty())
                ? def.getVoucher().getItemId().trim()
                : "Jvips_Voucher";

        BsonDocument meta = new BsonDocument();

        // =============================
        // METADATA FUNCIONAL
        // =============================
        meta.append("jvips:type", new BsonString("command_voucher"));
        meta.append("jvips:cmdVoucherId", new BsonString(def.getId()));
        meta.append("jvips:issuedTo", new BsonString(issuedToUuid));
        meta.append("jvips:voucherId", new BsonString(voucherId));
        meta.append("jvips:issuedAt", new BsonInt64(issuedAt));

        // =============================
        // Lore / display_name
        // =============================
        Map<String, String> vars = new HashMap<>();
        vars.put("player", playerName);
        vars.put("voucherIdShort", safeShort(voucherId));

        if (def.getVoucher() != null && def.getVoucher().getName() != null) {
            meta.append("display_name", new BsonString(CommandTemplate.apply(def.getVoucher().getName(), vars)));
        }

        BsonArray loreArray = new BsonArray();
        List<String> lore = (def.getVoucher() != null) ? def.getVoucher().getLore() : null;
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
