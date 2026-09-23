package dev.yassou.numismaticstreasury.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.network.payload.OpenTreasuryScreenPayload;
import dev.yassou.numismaticstreasury.network.payload.TreasuryActionPayload;
import dev.yassou.numismaticstreasury.server.BankService;
import dev.yassou.numismaticstreasury.server.InventoryUtil;
import dev.yassou.numismaticstreasury.server.auction.AuctionListing;
import dev.yassou.numismaticstreasury.server.auction.TreasuryData;
import dev.yassou.numismaticstreasury.server.history.HistoryData;
import dev.yassou.numismaticstreasury.server.history.HistoryService;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TreasuryNetwork {
    public static final Gson GSON = new Gson();
    private static final String PROTOCOL_VERSION = "6";

    private TreasuryNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                OpenTreasuryScreenPayload.TYPE,
                OpenTreasuryScreenPayload.STREAM_CODEC,
                ClientPayloadBridge::handle
        );
        registrar.playToServer(
                TreasuryActionPayload.TYPE,
                TreasuryActionPayload.STREAM_CODEC,
                ServerActionHandler::handle
        );
    }

    public static void sendAction(String action, JsonObject data) {
        PacketDistributor.sendToServer(new TreasuryActionPayload(action, GSON.toJson(data)));
    }

    public static void openBankTeller(ServerPlayer player, BlockPos pos) {
        JsonObject data = new JsonObject();
        data.addProperty("pos", pos.asLong());
        data.addProperty("portable", false);
        addBankData(player, data);
        open(player, "bank", data);
    }

    public static void openPortableBankTeller(ServerPlayer player) {
        JsonObject data = new JsonObject();
        data.addProperty("portable", true);
        addBankData(player, data);
        open(player, "bank", data);
    }

    private static void addBankData(ServerPlayer player, JsonObject data) {
        data.addProperty("balance", BankService.balance(player));
        data.addProperty("operator", player.hasPermissions(2));
        data.addProperty("minimum", TreasuryConfig.get().pay.minimum);
        data.addProperty("maximum", TreasuryConfig.get().pay.maximum);
        JsonArray recipients = new JsonArray();
        BankService.recipientNames(player.getServer(), player.getUUID())
                .forEach(recipients::add);
        data.add("recipients", recipients);
    }

    public static void openShop(ServerPlayer player, ServerShopBlockEntity shop) {
        JsonObject data = shopData(player, shop);
        data.addProperty("admin", false);
        open(player, "shop", data);
    }

    public static void openShopAdmin(ServerPlayer player, ServerShopBlockEntity shop) {
        JsonObject data = shopData(player, shop);
        data.addProperty("admin", true);
        open(player, "shop_admin", data);
    }

    public static void openPlayerShop(ServerPlayer player, ServerShopBlockEntity shop) {
        open(player, "player_shop", playerShopData(player, shop));
    }

    public static void openPlayerShopAdmin(ServerPlayer player, ServerShopBlockEntity shop) {
        open(player, "player_shop_admin", playerShopData(player, shop));
    }

    public static void openPlayerShopAssociates(
            ServerPlayer player,
            ServerShopBlockEntity shop
    ) {
        JsonObject data = new JsonObject();
        data.addProperty("pos", shop.getBlockPos().asLong());
        data.addProperty("ownerName", shop.ownerName());
        data.addProperty("linkedTotal", shop.linkedPercentTotal());
        data.addProperty("maxAssociates", ServerShopBlockEntity.MAX_LINKED_PLAYERS);
        JsonArray playerNames = new JsonArray();
        BankService.recipientNames(player.getServer(), shop.ownerUuid())
                .forEach(playerNames::add);
        data.add("playerNames", playerNames);
        JsonArray associates = new JsonArray();
        for (ServerShopBlockEntity.LinkedPlayer linked : shop.linkedPlayers()) {
            JsonObject value = new JsonObject();
            value.addProperty("uuid", linked.uuid().toString());
            value.addProperty("name", linked.name());
            value.addProperty("percent", linked.percent());
            associates.add(value);
        }
        data.add("associates", associates);
        open(player, "player_shop_associates", data);
    }

    public static void openPlayerShopWithdraw(
            ServerPlayer player,
            ServerShopBlockEntity shop,
            String source
    ) {
        ItemStack item = shop.template();
        int capacity = item.isEmpty() ? 0 : InventoryUtil.capacity(player, item);
        JsonObject data = new JsonObject();
        data.addProperty("pos", shop.getBlockPos().asLong());
        data.addProperty("source", source);
        data.addProperty("stock", shop.stock());
        data.addProperty("capacity", capacity);
        data.addProperty("maximum", Math.min(shop.stock(), (long) capacity));
        addItem(data, item, player.registryAccess(), true);
        open(player, "player_shop_withdraw", data);
    }

    public static void openHistoryStats(
            ServerPlayer player,
            String source,
            boolean portable,
            @Nullable BlockPos returnPos,
            @Nullable ServerShopBlockEntity contextShop
    ) {
        HistoryData saved = HistoryData.get(player.getServer());
        if (contextShop != null) HistoryService.ensureShop(contextShop);

        JsonObject data = new JsonObject();
        data.addProperty("source", source);
        data.addProperty("portable", portable);
        if (returnPos != null) data.addProperty("returnPos", returnPos.asLong());
        data.addProperty("balance", BankService.balance(player));
        data.addProperty("operator", player.hasPermissions(2));
        data.addProperty(
                "notificationsEnabled",
                TreasuryData.get(player.getServer()).notificationsEnabled(player.getUUID())
        );
        if (contextShop != null) {
            data.addProperty("selectedShop", HistoryService.shopKey(contextShop));
        }

        HistoryData.PlayerTotals totals = saved.totals(player.getUUID());
        JsonObject playerTotals = new JsonObject();
        playerTotals.addProperty("earned", totals.earned());
        playerTotals.addProperty("spent", totals.spent());
        playerTotals.addProperty("sent", totals.sent());
        playerTotals.addProperty("received", totals.received());
        playerTotals.addProperty("shopEarned", totals.shopEarned());
        playerTotals.addProperty("shopSpent", totals.shopSpent());
        playerTotals.addProperty("auctionEarned", totals.auctionEarned());
        playerTotals.addProperty("auctionSpent", totals.auctionSpent());
        playerTotals.addProperty("transactions", totals.transactions());
        data.add("totals", playerTotals);

        JsonArray entries = new JsonArray();
        saved.entries(player.getUUID()).stream().limit(200).forEach(entry -> {
            JsonObject value = new JsonObject();
            value.addProperty("timestamp", entry.timestamp());
            value.addProperty("type", entry.type().name());
            value.addProperty("amount", entry.amount());
            value.addProperty("quantity", entry.quantity());
            if (entry.item() != null && !entry.item().isEmpty()) {
                addItem(value, entry.item(), player.registryAccess(), true);
            } else {
                value.addProperty("itemId", entry.itemId());
                value.addProperty("itemName", entry.itemName());
            }
            value.addProperty("counterparty", entry.counterparty());
            value.addProperty("detail", entry.detail());
            entries.add(value);
        });
        data.add("entries", entries);

        Map<String, HistoryData.ShopStats> visibleShops = new LinkedHashMap<>();
        saved.shopsFor(player.getUUID()).forEach(shop -> visibleShops.put(shop.key(), shop));
        if (contextShop != null) {
            HistoryData.ShopStats selected = saved.shop(HistoryService.shopKey(contextShop));
            if (selected != null) visibleShops.put(selected.key(), selected);
        }
        JsonArray shops = new JsonArray();
        visibleShops.values().forEach(shop -> shops.add(
                shopStats(shop, player.registryAccess())));
        data.add("shops", shops);

        if (player.hasPermissions(2)
                && TreasuryConfig.get().history.operatorGlobalStatistics) {
            HistoryData.GlobalStats global = saved.global();
            JsonObject server = new JsonObject();
            server.addProperty("transactions", global.transactions());
            server.addProperty("totalVolume", global.totalVolume());
            server.addProperty("transferVolume", global.transferVolume());
            server.addProperty("serverShopVolume", global.serverShopVolume());
            server.addProperty("playerShopVolume", global.playerShopVolume());
            server.addProperty("auctionVolume", global.auctionVolume());
            server.addProperty("commissionCollected", global.commissionCollected());
            data.add("server", server);
        }
        open(player, "history_stats", data);
    }

    private static JsonObject shopStats(
            HistoryData.ShopStats shop,
            HolderLookup.Provider registries
    ) {
        long now = System.currentTimeMillis();
        JsonObject value = new JsonObject();
        value.addProperty("key", shop.key());
        value.addProperty("ownerName", shop.ownerName());
        value.addProperty("dimension", shop.dimension());
        value.addProperty("pos", shop.pos());
        value.addProperty("itemId", shop.itemId());
        value.addProperty("itemName", shop.itemName());
        if (!shop.item().isEmpty()) {
            addItem(value, shop.item(), registries, true);
        }
        value.addProperty("startedAt", shop.startedAt());
        value.addProperty("sales", shop.sales());
        value.addProperty("itemsSold", shop.itemsSold());
        value.addProperty("grossRevenue", shop.grossRevenue());
        value.addProperty("bestSale", shop.bestSale());
        value.addProperty("lastSaleAt", shop.lastSaleAt());
        addPeriod(value, "day", shop.period(now - 86_400_000L));
        addPeriod(value, "week", shop.period(now - 7L * 86_400_000L));
        addPeriod(value, "month", shop.period(now - 30L * 86_400_000L));
        JsonArray beneficiaries = new JsonArray();
        shop.beneficiaries().forEach((uuid, beneficiary) -> {
            JsonObject saved = new JsonObject();
            saved.addProperty("uuid", uuid.toString());
            saved.addProperty("name", beneficiary.name());
            saved.addProperty("revenue", beneficiary.revenue());
            beneficiaries.add(saved);
        });
        value.add("beneficiaries", beneficiaries);
        return value;
    }

    private static void addPeriod(
            JsonObject value,
            String name,
            HistoryData.PeriodStats period
    ) {
        JsonObject saved = new JsonObject();
        saved.addProperty("sales", period.sales());
        saved.addProperty("items", period.items());
        saved.addProperty("revenue", period.revenue());
        value.add(name, saved);
    }

    private static JsonObject playerShopData(
            ServerPlayer player,
            ServerShopBlockEntity shop
    ) {
        JsonObject data = shopData(player, shop);
        data.addProperty("stock", shop.stock());
        data.addProperty("ownerName", shop.ownerName());
        return data;
    }

    private static JsonObject shopData(ServerPlayer player, ServerShopBlockEntity shop) {
        JsonObject data = new JsonObject();
        data.addProperty("pos", shop.getBlockPos().asLong());
        data.addProperty("mode", shop.mode().name());
        data.addProperty("price", shop.price());
        data.addProperty("lotSize", shop.lotSize());
        data.addProperty("balance", BankService.balance(player));
        addItem(data, shop.template(), player.registryAccess(), true);
        return data;
    }

    public static void openAuctionHouse(ServerPlayer player, BlockPos pos) {
        sendAuctionData(player, "auction", pos, null);
    }

    public static void openAuctionHouse(
            ServerPlayer player,
            BlockPos pos,
            String openTab
    ) {
        sendAuctionData(player, "auction", pos, openTab);
    }

    public static void broadcastAuctionUpdate(MinecraftServer server) {
        broadcastAuctionUpdate(server, null);
    }

    public static void broadcastAuctionUpdate(
            MinecraftServer server,
            ServerPlayer excludedPlayer
    ) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != excludedPlayer) {
                sendAuctionData(player, "auction_update", null, null);
            }
        }
    }

    private static void sendAuctionData(
            ServerPlayer player,
            String screen,
            BlockPos terminalPos,
            String openTab
    ) {
        JsonObject data = new JsonObject();
        if (terminalPos != null) data.addProperty("pos", terminalPos.asLong());
        if (openTab != null) data.addProperty("openTab", openTab);
        data.addProperty("balance", BankService.balance(player));
        data.addProperty("operator", player.hasPermissions(2));
        data.addProperty(
                "commissionPercent",
                TreasuryConfig.get().auctionHouse.commissionPercent
        );
        data.addProperty(
                "maximumListings",
                TreasuryConfig.get().auctionHouse.maximumListingsPerPlayer
        );
        JsonArray durations = new JsonArray();
        TreasuryConfig.get().auctionHouse.allowedDurationsHours.forEach(durations::add);
        data.add("durations", durations);

        TreasuryData saved = TreasuryData.get(player.getServer());
        data.addProperty("claimCount", saved.claims(player.getUUID()).size());
        JsonArray listings = new JsonArray();
        for (AuctionListing listing : saved.listings()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", listing.id().toString());
            value.addProperty("sellerUuid", listing.sellerUuid().toString());
            value.addProperty("sellerName", listing.sellerName());
            value.addProperty("type", listing.type().name());
            value.addProperty("price", listing.price());
            value.addProperty("currentBid", listing.currentBid());
            value.addProperty("minimumBid", listing.minimumBid());
            value.addProperty("leadingBidderName", listing.leadingBidderName());
            value.addProperty("createdAt", listing.createdAt());
            value.addProperty("expiresAt", listing.expiresAt());
            value.addProperty("revision", listing.revision());
            value.addProperty("mine", listing.sellerUuid().equals(player.getUUID()));
            addItem(value, listing.item(), player.registryAccess(), false);
            listings.add(value);
        }
        data.add("listings", listings);
        open(player, screen, data);
    }

    public static void addItem(
            JsonObject data,
            ItemStack stack,
            HolderLookup.Provider registries,
            boolean includeComponents
    ) {
        if (stack == null || stack.isEmpty()) {
            data.addProperty("itemId", "minecraft:air");
            data.addProperty("itemName", "No item");
            data.addProperty("count", 0);
            return;
        }
        data.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        data.addProperty("itemName", stack.getHoverName().getString());
        data.addProperty("count", stack.getCount());
        if (includeComponents) {
            data.addProperty("itemNbt", stack.saveOptional(registries).toString());
        }
    }

    private static void open(ServerPlayer player, String screen, JsonObject data) {
        PacketDistributor.sendToPlayer(
                player,
                new OpenTreasuryScreenPayload(screen, GSON.toJson(data))
        );
    }
}
