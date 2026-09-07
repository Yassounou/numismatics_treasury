package dev.yassou.numismaticstreasury.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.network.payload.OpenTreasuryScreenPayload;
import dev.yassou.numismaticstreasury.network.payload.TreasuryActionPayload;
import dev.yassou.numismaticstreasury.server.BankService;
import dev.yassou.numismaticstreasury.server.auction.AuctionListing;
import dev.yassou.numismaticstreasury.server.auction.TreasuryData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TreasuryNetwork {
    public static final Gson GSON = new Gson();
    private static final String PROTOCOL_VERSION = "1";

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
        data.addProperty("balance", BankService.balance(player));
        addItem(data, shop.template());
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
            addItem(value, listing.item());
            listings.add(value);
        }
        data.add("listings", listings);
        open(player, screen, data);
    }

    public static void addItem(JsonObject data, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            data.addProperty("itemId", "minecraft:air");
            data.addProperty("itemName", "No item");
            data.addProperty("count", 0);
            return;
        }
        data.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        data.addProperty("itemName", stack.getHoverName().getString());
        data.addProperty("count", stack.getCount());
    }

    private static void open(ServerPlayer player, String screen, JsonObject data) {
        PacketDistributor.sendToPlayer(
                player,
                new OpenTreasuryScreenPayload(screen, GSON.toJson(data))
        );
    }
}
