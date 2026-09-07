package dev.yassou.numismaticstreasury.server.auction;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.server.BankService;
import dev.yassou.numismaticstreasury.server.InventoryUtil;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-authoritative global marketplace and banking escrow. */
public final class AuctionService {
    private static long lastProcessedTick = Long.MIN_VALUE;

    private AuctionService() {
    }

    public static Result create(
            ServerPlayer seller,
            ListingType type,
            int price,
            int durationHours,
            int inventorySlot
    ) {
        TreasuryConfig.AuctionHouse config = TreasuryConfig.get().auctionHouse;
        if (!TreasuryConfig.get().modules.auctionHouse) {
            return Result.failure("message.numismatics_treasury.auction.disabled");
        }
        if (price < config.minimumPrice || price > config.maximumPrice) {
            return Result.failure(
                    "message.numismatics_treasury.auction.price_range",
                    config.minimumPrice,
                    config.maximumPrice
            );
        }
        if (!config.allowedDurationsHours.contains(durationHours)) {
            return Result.failure("message.numismatics_treasury.auction.duration_not_allowed");
        }
        TreasuryData data = TreasuryData.get(seller.getServer());
        if (data.listingCount(seller.getUUID()) >= config.maximumListingsPerPlayer) {
            return Result.failure("message.numismatics_treasury.auction.listing_limit");
        }
        if (inventorySlot < 0 || inventorySlot >= seller.getInventory().items.size()) {
            return Result.failure("message.numismatics_treasury.inventory_selection_invalid");
        }
        ItemStack selected = seller.getInventory().items.get(inventorySlot);
        if (selected.isEmpty()) {
            return Result.failure("message.numismatics_treasury.selected_item_missing");
        }

        long now = System.currentTimeMillis();
        long durationMillis;
        try {
            durationMillis = Math.multiplyExact((long) durationHours, 3_600_000L);
        } catch (ArithmeticException exception) {
            return Result.failure("message.numismatics_treasury.auction.duration_invalid");
        }
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID(),
                seller.getUUID(),
                seller.getGameProfile().getName(),
                selected.copy(),
                type,
                price,
                now,
                now + durationMillis
        );
        seller.getInventory().items.set(inventorySlot, ItemStack.EMPTY);
        seller.getInventory().setChanged();
        data.put(listing);
        return Result.success("message.numismatics_treasury.auction.created");
    }

    public static Result buy(ServerPlayer buyer, UUID listingId, long revision) {
        TreasuryData data = TreasuryData.get(buyer.getServer());
        AuctionListing listing = data.listing(listingId);
        if (!TreasuryConfig.get().modules.auctionHouse || listing == null) {
            return Result.failure("message.numismatics_treasury.auction.listing_missing");
        }
        if (listing.type() != ListingType.FIXED || listing.revision() != revision) {
            return Result.failure("message.numismatics_treasury.auction.listing_changed");
        }
        if (listing.expired(System.currentTimeMillis())) {
            finishExpired(buyer.getServer(), listing);
            return Result.failure("message.numismatics_treasury.auction.listing_expired");
        }
        if (listing.sellerUuid().equals(buyer.getUUID())) {
            return Result.failure("message.numismatics_treasury.auction.own_listing");
        }
        if (!BankService.debit(buyer, listing.price())) {
            return Result.failure(
                    "message.numismatics_treasury.balance_insufficient",
                    BankService.balance(buyer)
            );
        }
        int sellerAmount = sellerAmount(listing.price());
        if (sellerAmount > 0
                && !BankService.credit(listing.sellerUuid(), sellerAmount)) {
            BankService.credit(buyer, listing.price());
            return Result.failure("message.numismatics_treasury.auction.seller_account_unavailable");
        }
        data.remove(listing.id());
        ItemStack purchased = listing.item();
        List<ItemStack> undelivered = InventoryUtil.giveClaims(
                buyer,
                List.of(purchased)
        );
        for (ItemStack remainder : undelivered) {
            data.addClaim(buyer.getUUID(), remainder);
        }
        notifyPlayer(
                buyer.getServer(),
                listing.sellerUuid(),
                "notification.numismatics_treasury.auction.item_sold",
                buyer.getGameProfile().getName(),
                listing.item().getHoverName().getString(),
                sellerAmount
        );
        if (undelivered.isEmpty()) {
            return Result.success(
                    "message.numismatics_treasury.auction.purchase_success_inventory"
            );
        }
        int remainingCount = undelivered.stream().mapToInt(ItemStack::getCount).sum();
        int deliveredCount = purchased.getCount() - remainingCount;
        return deliveredCount > 0
                ? Result.success(
                        "message.numismatics_treasury.auction.purchase_success_partial",
                        deliveredCount,
                        remainingCount
                )
                : Result.success("message.numismatics_treasury.auction.purchase_success");
    }

    public static Result bid(
            ServerPlayer bidder,
            UUID listingId,
            long revision,
            int amount
    ) {
        TreasuryData data = TreasuryData.get(bidder.getServer());
        AuctionListing listing = data.listing(listingId);
        if (!TreasuryConfig.get().modules.auctionHouse || listing == null) {
            return Result.failure("message.numismatics_treasury.auction.listing_missing");
        }
        if (listing.type() != ListingType.AUCTION || listing.revision() != revision) {
            return Result.failure("message.numismatics_treasury.auction.listing_changed");
        }
        if (listing.expired(System.currentTimeMillis())) {
            finishExpired(bidder.getServer(), listing);
            return Result.failure("message.numismatics_treasury.auction.ended");
        }
        if (listing.sellerUuid().equals(bidder.getUUID())) {
            return Result.failure("message.numismatics_treasury.auction.own_listing");
        }
        if (amount < listing.minimumBid()) {
            return Result.failure(
                    "message.numismatics_treasury.auction.minimum_bid",
                    listing.minimumBid()
            );
        }
        TreasuryConfig.AuctionHouse config = TreasuryConfig.get().auctionHouse;
        if (amount < config.minimumPrice || amount > config.maximumPrice) {
            return Result.failure("message.numismatics_treasury.amount_out_of_range");
        }

        UUID previousBidder = listing.leadingBidderUuid();
        int previousAmount = listing.currentBid();
        boolean sameBidder = bidder.getUUID().equals(previousBidder);
        int debitAmount = sameBidder ? amount - previousAmount : amount;
        if (debitAmount <= 0 || !BankService.debit(bidder, debitAmount)) {
            return Result.failure(
                    "message.numismatics_treasury.balance_insufficient",
                    BankService.balance(bidder)
            );
        }

        listing.recordBid(
                bidder.getUUID(),
                bidder.getGameProfile().getName(),
                amount
        );
        data.changed();
        if (!sameBidder && previousBidder != null) {
            BankService.credit(previousBidder, previousAmount);
            notifyPlayer(
                    bidder.getServer(),
                    previousBidder,
                    "notification.numismatics_treasury.auction.outbid",
                    listing.item().getHoverName().getString()
            );
        }
        notifyPlayer(
                bidder.getServer(),
                listing.sellerUuid(),
                "notification.numismatics_treasury.auction.new_bid",
                bidder.getGameProfile().getName(),
                listing.item().getHoverName().getString(),
                amount
        );
        return Result.success("message.numismatics_treasury.auction.bid_success", amount);
    }

    public static Result cancel(ServerPlayer actor, UUID listingId) {
        TreasuryData data = TreasuryData.get(actor.getServer());
        AuctionListing listing = data.listing(listingId);
        if (listing == null) {
            return Result.failure("message.numismatics_treasury.auction.listing_missing");
        }
        boolean owner = listing.sellerUuid().equals(actor.getUUID());
        if (!owner && !actor.hasPermissions(2)) {
            return Result.failure("message.numismatics_treasury.auction.cancel_forbidden");
        }
        if (owner && listing.leadingBidderUuid() != null) {
            return Result.failure("message.numismatics_treasury.auction.cancel_with_bid");
        }
        data.remove(listing.id());
        data.addClaim(listing.sellerUuid(), listing.item());
        if (listing.leadingBidderUuid() != null) {
            BankService.credit(listing.leadingBidderUuid(), listing.currentBid());
        }
        if (!owner) {
            notifyPlayer(
                    actor.getServer(),
                    listing.sellerUuid(),
                    "notification.numismatics_treasury.auction.cancelled_by_operator",
                    listing.item().getHoverName().getString()
            );
        }
        return Result.success("message.numismatics_treasury.auction.cancelled");
    }

    public static Result claim(ServerPlayer player) {
        TreasuryData data = TreasuryData.get(player.getServer());
        List<ItemStack> claims = data.claims(player.getUUID());
        if (claims.isEmpty()) {
            return Result.failure("message.numismatics_treasury.auction.claim_empty");
        }
        List<ItemStack> remaining = InventoryUtil.giveClaims(player, claims);
        data.replaceClaims(player.getUUID(), remaining);
        int received = claims.size() - remaining.size();
        if (received <= 0) {
            return Result.failure("message.numismatics_treasury.inventory_full");
        }
        return Result.success(
                remaining.isEmpty()
                        ? "message.numismatics_treasury.auction.claim_received"
                        : "message.numismatics_treasury.auction.claim_received_partial",
                received
        );
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        if (tick == lastProcessedTick || Math.floorMod(tick, 20L) != 0L) return;
        lastProcessedTick = tick;
        if (!TreasuryConfig.get().modules.auctionHouse) return;
        List<AuctionListing> expired = TreasuryData.get(server).listings().stream()
                .filter(listing -> listing.expired(System.currentTimeMillis()))
                .toList();
        for (AuctionListing listing : expired) finishExpired(server, listing);
        if (!expired.isEmpty()) TreasuryNetwork.broadcastAuctionUpdate(server);
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (TreasuryNotification notification
                : TreasuryData.get(player.getServer()).drainNotifications(player.getUUID())) {
            player.sendSystemMessage(notification.component());
        }
    }

    private static void finishExpired(MinecraftServer server, AuctionListing listing) {
        TreasuryData data = TreasuryData.get(server);
        if (data.remove(listing.id()) == null) return;
        if (listing.type() == ListingType.AUCTION && listing.leadingBidderUuid() != null) {
            int sellerAmount = sellerAmount(listing.currentBid());
            if (sellerAmount > 0
                    && !BankService.credit(listing.sellerUuid(), sellerAmount)) {
                NumismaticsTreasury.LOGGER.error(
                        "Could not credit seller {} for auction {}",
                        listing.sellerUuid(),
                        listing.id()
                );
            }
            data.addClaim(listing.leadingBidderUuid(), listing.item());
            notifyPlayer(
                    server,
                    listing.sellerUuid(),
                    "notification.numismatics_treasury.auction.finished_seller",
                    listing.item().getHoverName().getString(),
                    sellerAmount
            );
            notifyPlayer(
                    server,
                    listing.leadingBidderUuid(),
                    "notification.numismatics_treasury.auction.won",
                    listing.item().getHoverName().getString()
            );
        } else {
            data.addClaim(listing.sellerUuid(), listing.item());
            notifyPlayer(
                    server,
                    listing.sellerUuid(),
                    "notification.numismatics_treasury.auction.expired",
                    listing.item().getHoverName().getString()
            );
        }
    }

    private static int sellerAmount(int price) {
        double percentage = TreasuryConfig.get().auctionHouse.commissionPercent;
        int commission = (int) Math.floor(price * percentage / 100.0D);
        return Math.max(0, price - commission);
    }

    private static void notifyPlayer(
            MinecraftServer server,
            UUID playerUuid,
            String key,
            Object... arguments
    ) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(key, arguments));
        } else {
            TreasuryData.get(server).addNotification(playerUuid, key, arguments);
        }
    }

    public record Result(boolean successful, Component message) {
        public static Result success(String key, Object... arguments) {
            return new Result(true, Component.translatable(key, arguments));
        }

        public static Result failure(String key, Object... arguments) {
            return new Result(false, Component.translatable(key, arguments));
        }
    }
}
