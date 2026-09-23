package dev.yassou.numismaticstreasury.server.history;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public record HistoryEntry(
        long timestamp,
        Type type,
        long amount,
        int quantity,
        String itemId,
        String itemName,
        ItemStack item,
        String counterparty,
        String detail
) {
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("timestamp", timestamp);
        tag.putString("type", type.name());
        tag.putLong("amount", amount);
        tag.putInt("quantity", quantity);
        tag.putString("itemId", safe(itemId));
        tag.putString("itemName", safe(itemName));
        if (item != null && !item.isEmpty()) {
            tag.put("item", item.saveOptional(registries));
        }
        tag.putString("counterparty", safe(counterparty));
        tag.putString("detail", safe(detail));
        return tag;
    }

    public static HistoryEntry load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
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
                tag.contains("item", Tag.TAG_COMPOUND)
                        ? ItemStack.parseOptional(registries, tag.getCompound("item"))
                        : ItemStack.EMPTY,
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
