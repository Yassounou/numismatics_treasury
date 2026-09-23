package dev.yassou.numismaticstreasury.server.history;

import net.minecraft.nbt.CompoundTag;

public record HistoryEntry(
        long timestamp,
        Type type,
        long amount,
        int quantity,
        String itemId,
        String itemName,
        String counterparty,
        String detail
) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("timestamp", timestamp);
        tag.putString("type", type.name());
        tag.putLong("amount", amount);
        tag.putInt("quantity", quantity);
        tag.putString("itemId", safe(itemId));
        tag.putString("itemName", safe(itemName));
        tag.putString("counterparty", safe(counterparty));
        tag.putString("detail", safe(detail));
        return tag;
    }

    public static HistoryEntry load(CompoundTag tag) {
        Type type;
        try {
            type = Type.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return new HistoryEntry(
                tag.getLong("timestamp"),
                type,
                tag.getLong("amount"),
                Math.max(0, tag.getInt("quantity")),
                tag.getString("itemId"),
                tag.getString("itemName"),
                tag.getString("counterparty"),
                tag.getString("detail")
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public enum Type {
        TRANSFER_SENT,
        TRANSFER_RECEIVED,
        SERVER_SHOP_PURCHASE,
        SERVER_SHOP_SALE,
        PLAYER_SHOP_PURCHASE,
        PLAYER_SHOP_REVENUE,
        AUCTION_LISTED,
        AUCTION_PURCHASE,
        AUCTION_SALE,
        AUCTION_BID,
        AUCTION_REFUND,
        AUCTION_WON,
        AUCTION_EXPIRED
    }
}
