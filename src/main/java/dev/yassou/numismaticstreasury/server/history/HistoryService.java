package dev.yassou.numismaticstreasury.server.history;

import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

public final class HistoryService {
    private HistoryService() {
    }

    public static void recordTransfer(
            MinecraftServer server,
            UUID senderUuid,
            String senderName,
            UUID recipientUuid,
            String recipientName,
            int amount
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordTransfers) return;
        long now = System.currentTimeMillis();
        HistoryData data = HistoryData.get(server);
        data.add(senderUuid, entry(
                now, HistoryEntry.Type.TRANSFER_SENT, -amount,
                0, ItemStack.EMPTY, recipientName, ""));
        data.add(recipientUuid, entry(
                now, HistoryEntry.Type.TRANSFER_RECEIVED, amount,
                0, ItemStack.EMPTY, senderName, ""));
        data.recordGlobal(HistoryData.GlobalCategory.TRANSFER, amount, 0L);
    }

    public static void recordServerShop(
            ServerPlayer player,
            ItemStack item,
            int quantity,
            int amount,
            boolean purchase
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordServerShops) return;
        HistoryData data = HistoryData.get(player.getServer());
        data.add(player.getUUID(), entry(
                System.currentTimeMillis(),
                purchase
                        ? HistoryEntry.Type.SERVER_SHOP_PURCHASE
                        : HistoryEntry.Type.SERVER_SHOP_SALE,
                purchase ? -amount : amount,
                quantity,
                item,
                "Server Shop",
                ""
        ));
        data.recordGlobal(HistoryData.GlobalCategory.SERVER_SHOP, amount, 0L);
    }

    public static void recordPlayerShopSale(
            ServerPlayer buyer,
            ServerShopBlockEntity shop,
            ItemStack item,
            int quantity,
            int total,
            Map<UUID, HistoryData.BeneficiaryShare> shares
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordPlayerShops) return;
        HistoryData data = HistoryData.get(buyer.getServer());
        long now = System.currentTimeMillis();
        data.add(buyer.getUUID(), entry(
                now,
                HistoryEntry.Type.PLAYER_SHOP_PURCHASE,
                -total,
                quantity,
                item,
                shop.ownerName(),
                ""
        ));
        shares.forEach((uuid, share) -> {
            if (share.amount() > 0L) {
                data.add(uuid, entry(
                        now,
                        HistoryEntry.Type.PLAYER_SHOP_REVENUE,
                        share.amount(),
                        quantity,
                        item,
                        buyer.getGameProfile().getName(),
                        shop.ownerName()
                ));
            }
        });
        data.recordShopSale(
                shopKey(shop),
                shop.ownerUuid(),
                shop.ownerName(),
                shop.getLevel().dimension().location().toString(),
                shop.getBlockPos().asLong(),
                itemId(item),
                itemName(item),
                buyer.getGameProfile().getName(),
                quantity,
                total,
                shares
        );
        data.recordGlobal(HistoryData.GlobalCategory.PLAYER_SHOP, total, 0L);
    }

    public static void recordAuctionListed(
            ServerPlayer seller,
            ItemStack item,
            int price,
            String type
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordAuctionHouse) return;
        HistoryData.get(seller.getServer()).add(seller.getUUID(), entry(
                System.currentTimeMillis(),
                HistoryEntry.Type.AUCTION_LISTED,
                price,
                item.getCount(),
                item,
                "Auction House",
                type
        ));
    }

    public static void recordAuctionPurchase(
            MinecraftServer server,
            UUID buyerUuid,
            String buyerName,
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            int price,
            int sellerAmount
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordAuctionHouse) return;
        long now = System.currentTimeMillis();
        HistoryData data = HistoryData.get(server);
        data.add(buyerUuid, entry(
                now, HistoryEntry.Type.AUCTION_PURCHASE, -price,
                item.getCount(), item, sellerName, ""));
        data.add(sellerUuid, entry(
                now, HistoryEntry.Type.AUCTION_SALE, sellerAmount,
                item.getCount(), item, buyerName, ""));
        data.recordGlobal(
                HistoryData.GlobalCategory.AUCTION,
                price,
                Math.max(0, price - sellerAmount)
        );
    }

    public static void recordAuctionBid(
            ServerPlayer bidder,
            ItemStack item,
            int amount,
            String sellerName
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordAuctionHouse) return;
        HistoryData.get(bidder.getServer()).add(bidder.getUUID(), entry(
                System.currentTimeMillis(),
                HistoryEntry.Type.AUCTION_BID,
                -amount,
                item.getCount(),
                item,
                sellerName,
                ""
        ));
    }

    public static void recordAuctionRefund(
            MinecraftServer server,
            UUID playerUuid,
            ItemStack item,
            int amount,
            String detail
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordAuctionHouse) return;
        HistoryData.get(server).add(playerUuid, entry(
                System.currentTimeMillis(),
                HistoryEntry.Type.AUCTION_REFUND,
                amount,
                item.getCount(),
                item,
                "Auction House",
                detail
        ));
    }

    public static void recordAuctionWon(
            MinecraftServer server,
            UUID winnerUuid,
            String winnerName,
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            int price,
            int sellerAmount
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordAuctionHouse) return;
        long now = System.currentTimeMillis();
        HistoryData data = HistoryData.get(server);
        data.add(winnerUuid, entry(
                now, HistoryEntry.Type.AUCTION_WON, -price,
                item.getCount(), item, sellerName, ""));
        data.add(sellerUuid, entry(
                now, HistoryEntry.Type.AUCTION_SALE, sellerAmount,
                item.getCount(), item, winnerName, ""));
        data.recordGlobal(
                HistoryData.GlobalCategory.AUCTION,
                price,
                Math.max(0, price - sellerAmount)
        );
    }

    public static void recordAuctionExpired(
            MinecraftServer server,
            UUID sellerUuid,
            ItemStack item
    ) {
        if (!enabled() || !TreasuryConfig.get().history.recordAuctionHouse) return;
        HistoryData.get(server).add(sellerUuid, entry(
                System.currentTimeMillis(),
                HistoryEntry.Type.AUCTION_EXPIRED,
                0L,
                item.getCount(),
                item,
                "Auction House",
                ""
        ));
    }

    public static HistoryData.ShopStats ensureShop(ServerShopBlockEntity shop) {
        HistoryData data = HistoryData.get(shop.getLevel().getServer());
        ItemStack item = shop.template();
        return data.ensureShop(
                shopKey(shop),
                shop.ownerUuid(),
                shop.ownerName(),
                shop.getLevel().dimension().location().toString(),
                shop.getBlockPos().asLong(),
                itemId(item),
                itemName(item)
        );
    }

    public static void removeShop(ServerShopBlockEntity shop) {
        if (shop.getLevel() == null || shop.getLevel().getServer() == null) return;
        HistoryData.get(shop.getLevel().getServer()).removeShop(shopKey(shop));
    }

    public static String shopKey(ServerShopBlockEntity shop) {
        String dimension = shop.getLevel() == null
                ? "unknown" : shop.getLevel().dimension().location().toString();
        return dimension + "|" + shop.getBlockPos().asLong() + "|" + shop.ownerUuid();
    }

    private static HistoryEntry entry(
            long timestamp,
            HistoryEntry.Type type,
            long amount,
            int quantity,
            ItemStack item,
            String counterparty,
            String detail
    ) {
        return new HistoryEntry(
                timestamp,
                type,
                amount,
                Math.max(0, quantity),
                itemId(item),
                itemName(item),
                counterparty == null ? "" : counterparty,
                detail == null ? "" : detail
        );
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? "minecraft:air"
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String itemName(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString();
    }

    private static boolean enabled() {
        return TreasuryConfig.get().history.enabled;
    }

}
