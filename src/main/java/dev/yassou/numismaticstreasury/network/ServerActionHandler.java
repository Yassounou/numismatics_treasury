package dev.yassou.numismaticstreasury.network;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.block.entity.ShopMode;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.network.payload.TreasuryActionPayload;
import dev.yassou.numismaticstreasury.server.BankService;
import dev.yassou.numismaticstreasury.server.InventoryUtil;
import dev.yassou.numismaticstreasury.server.ProfileResolver;
import dev.yassou.numismaticstreasury.server.auction.AuctionService;
import dev.yassou.numismaticstreasury.server.auction.ListingType;
import dev.yassou.numismaticstreasury.server.auction.TreasuryData;
import dev.yassou.numismaticstreasury.server.history.HistoryData;
import dev.yassou.numismaticstreasury.server.history.HistoryService;
import dev.yassou.numismaticstreasury.registry.TreasuryContent;
import dev.yassou.numismaticstreasury.menu.PlayerShopMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ServerActionHandler {
    private static final int MAX_TRANSACTION_ITEMS = 2_304;

    private ServerActionHandler() {
    }

    public static void handle(TreasuryActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleOnServer(payload, context));
    }

    private static void handleOnServer(TreasuryActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        try {
            JsonObject data = TreasuryNetwork.GSON.fromJson(payload.json(), JsonObject.class);
            if (data == null) data = new JsonObject();
            switch (payload.action()) {
                case "pay" -> pay(player, data);
                case "shop_config" -> configureShop(player, data);
                case "shop_trade" -> tradeShop(player, data, false);
                case "shop_sell_all" -> tradeShop(player, data, true);
                case "player_shop_config" -> configurePlayerShop(player, data);
                case "player_shop_deposit" -> depositPlayerShop(player, data);
                case "player_shop_withdraw" -> withdrawPlayerShop(player, data);
                case "player_shop_trade" -> tradePlayerShop(player, data);
                case "player_shop_menu_config" -> configurePlayerShopMenu(player, data);
                case "player_shop_menu_withdraw" -> withdrawPlayerShopMenu(player, data);
                case "player_shop_open_associates" -> openPlayerShopAssociates(player, data);
                case "player_shop_associate_save" -> savePlayerShopAssociate(player, data);
                case "player_shop_associate_remove" -> removePlayerShopAssociate(player, data);
                case "player_shop_associate_back" -> backToPlayerShop(player, data);
                case "history_open" -> openHistory(player, data);
                case "player_shop_open_stats" -> openPlayerShopStats(player, data);
                case "history_back" -> closeHistory(player, data);
                case "auction_create" -> auctionCreate(player, data);
                case "auction_buy" -> auctionBuy(player, data);
                case "auction_bid" -> auctionBid(player, data);
                case "auction_cancel" -> auctionCancel(player, data);
                case "auction_claim" -> auctionClaim(player, data);
                case "auction_refresh" -> auctionRefresh(player, data);
                default -> player.sendSystemMessage(Component.translatable(
                        "message.numismatics_treasury.action_unknown"));
            }
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.translatable(
                    "message.numismatics_treasury.request_invalid"));
        }
    }

    private static void pay(ServerPlayer player, JsonObject data) {
        boolean portable = data.has("portable") && data.get("portable").getAsBoolean();
        BlockPos terminal = null;
        if (portable) {
            if (!TreasuryConfig.get().modules.portableTransferTerminal) {
                message(player, false,
                        "message.numismatics_treasury.portable_transfer_terminal.disabled");
                return;
            }
            if (!holdsPortableTerminal(player)) {
                message(player, false,
                        "message.numismatics_treasury.portable_transfer_terminal.not_held");
                return;
            }
        } else {
            terminal = terminal(player, data, true);
            if (terminal == null) return;
            if (!TreasuryConfig.get().modules.bankTeller) {
                message(player, false,
                        "message.numismatics_treasury.transfer_teller.disabled");
                return;
            }
        }
        int amount = data.get("amount").getAsInt();
        String targetName = data.get("target").getAsString().strip();
        TreasuryConfig.Pay config = TreasuryConfig.get().pay;
        if (amount < config.minimum || amount > config.maximum) {
            message(player, false, "message.numismatics_treasury.amount_out_of_range");
            reopenBankTeller(player, portable, terminal);
            return;
        }
        Optional<GameProfile> target = ProfileResolver.resolve(player.getServer(), targetName);
        if (target.isEmpty()) {
            message(player, false, "message.numismatics_treasury.player_unknown", targetName);
        } else {
            BankService.TransferResult transfer = BankService.transfer(
                    player,
                    target.get().getId(),
                    target.get().getName(),
                    amount
            );
            message(player, transfer.successful(), transfer.message());
            ServerPlayer recipient = player.getServer().getPlayerList()
                    .getPlayer(target.get().getId());
            if (transfer.successful() && recipient != null) {
                recipient.sendSystemMessage(Component.translatable(
                        "notification.numismatics_treasury.pay.received",
                        player.getGameProfile().getName(),
                        amount
                ));
            }
            if (transfer.successful()) {
                HistoryService.recordTransfer(
                        player.getServer(),
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        target.get().getId(),
                        target.get().getName(),
                        amount
                );
            }
        }
        reopenBankTeller(player, portable, terminal);
    }

    private static boolean holdsPortableTerminal(ServerPlayer player) {
        return player.getMainHandItem().is(TreasuryContent.PORTABLE_TRANSFER_TERMINAL.get())
                || player.getOffhandItem().is(TreasuryContent.PORTABLE_TRANSFER_TERMINAL.get());
    }

    private static void reopenBankTeller(
            ServerPlayer player,
            boolean portable,
            BlockPos terminal
    ) {
        if (portable) TreasuryNetwork.openPortableBankTeller(player);
        else TreasuryNetwork.openBankTeller(player, terminal);
    }

    private static void configureShop(ServerPlayer player, JsonObject data) {
        if (!TreasuryConfig.get().modules.serverShop || !player.hasPermissions(2)) {
            message(player, false, "message.numismatics_treasury.operator_required");
            return;
        }
        BlockPos pos = BlockPos.of(data.get("pos").getAsLong());
        if (!near(player, pos)
                || !player.level().getBlockState(pos).is(TreasuryContent.NUMIS_SHOP.get())
                || !(player.level().getBlockEntity(pos) instanceof ServerShopBlockEntity shop)) {
            message(player, false, "message.numismatics_treasury.shop.missing");
            return;
        }
        int price = data.get("price").getAsInt();
        if (price <= 0 || price > 2_000_000_000) {
            message(player, false, "message.numismatics_treasury.price_invalid");
            return;
        }
        int lotSize = configuredLotSize(data, shop);
        if (!validLotSize(lotSize)) {
            message(player, false, "message.numismatics_treasury.quantity_invalid");
            return;
        }
        int inventorySlot = data.has("inventorySlot")
                ? data.get("inventorySlot").getAsInt() : -1;
        ItemStack template = inventorySlot >= 0
                && inventorySlot < player.getInventory().items.size()
                ? player.getInventory().items.get(inventorySlot)
                : shop.template();
        if (template.isEmpty()) {
            message(player, false, "message.numismatics_treasury.shop.item_required");
            return;
        }
        shop.configure(
                ShopMode.parse(data.get("mode").getAsString()),
                template,
                price,
                lotSize
        );
        message(player, true, "message.numismatics_treasury.shop.configured");
        TreasuryNetwork.openShopAdmin(player, shop);
    }

    private static void tradeShop(
            ServerPlayer player,
            JsonObject data,
            boolean sellAll
    ) {
        if (!TreasuryConfig.get().modules.serverShop) {
            message(player, false, "message.numismatics_treasury.shop.disabled");
            return;
        }
        BlockPos pos = BlockPos.of(data.get("pos").getAsLong());
        if (!near(player, pos)
                || !player.level().getBlockState(pos).is(TreasuryContent.NUMIS_SHOP.get())
                || !(player.level().getBlockEntity(pos) instanceof ServerShopBlockEntity shop)
                || !shop.configured()) {
            message(player, false, "message.numismatics_treasury.shop.unavailable");
            return;
        }
        if (sellAll && shop.mode() != ShopMode.BUY_FROM_PLAYER) {
            message(player, false, "message.numismatics_treasury.request_invalid");
            return;
        }
        int lots = sellAll
                ? InventoryUtil.countMatching(player, shop.template()) / shop.lotSize()
                : requestedLots(data);
        int quantity;
        try {
            quantity = Math.multiplyExact(shop.lotSize(), lots);
        } catch (ArithmeticException exception) {
            message(player, false, "message.numismatics_treasury.quantity_invalid");
            TreasuryNetwork.openShop(player, shop);
            return;
        }
        if (lots <= 0 || quantity <= 0 || quantity > MAX_TRANSACTION_ITEMS) {
            message(player, false, sellAll
                    ? "message.numismatics_treasury.shop.not_enough_items"
                    : "message.numismatics_treasury.quantity_invalid");
            TreasuryNetwork.openShop(player, shop);
            return;
        }
        int total;
        try {
            total = Math.multiplyExact(shop.price(), lots);
        } catch (ArithmeticException exception) {
            message(player, false, "message.numismatics_treasury.amount_too_large");
            return;
        }
        ItemStack template = shop.template();
        if (shop.mode() == ShopMode.SELL_TO_PLAYER) {
            if (!InventoryUtil.canFit(player, template, quantity)) {
                message(player, false, "message.numismatics_treasury.inventory_no_space");
            } else if (!BankService.debit(player, total)) {
                message(
                        player,
                        false,
                        "message.numismatics_treasury.balance_insufficient",
                        BankService.balance(player)
                );
            } else {
                InventoryUtil.give(player, template, quantity);
                message(player, true, "message.numismatics_treasury.shop.purchase", total);
                HistoryService.recordServerShop(player, template, quantity, total, true);
            }
        } else if (InventoryUtil.countMatching(player, template) < quantity) {
            message(player, false, "message.numismatics_treasury.shop.not_enough_items");
        } else {
            InventoryUtil.removeMatching(player, template, quantity);
            BankService.credit(player, total);
            message(player, true, "message.numismatics_treasury.shop.sale", total);
            HistoryService.recordServerShop(player, template, quantity, total, false);
        }
        TreasuryNetwork.openShop(player, shop);
    }

    private static void configurePlayerShop(ServerPlayer player, JsonObject data) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return;
        int price = data.get("price").getAsInt();
        if (price <= 0 || price > 2_000_000_000) {
            message(player, false, "message.numismatics_treasury.price_invalid");
            TreasuryNetwork.openPlayerShopAdmin(player, shop);
            return;
        }
        int lotSize = configuredLotSize(data, shop);
        if (!validLotSize(lotSize)) {
            message(player, false, "message.numismatics_treasury.quantity_invalid");
            TreasuryNetwork.openPlayerShopAdmin(player, shop);
            return;
        }
        int inventorySlot = data.has("inventorySlot")
                ? data.get("inventorySlot").getAsInt() : -1;
        ItemStack template = inventorySlot >= 0
                && inventorySlot < player.getInventory().items.size()
                ? player.getInventory().items.get(inventorySlot)
                : shop.template();
        if (template.isEmpty()) {
            message(player, false, "message.numismatics_treasury.player_shop.item_required");
            TreasuryNetwork.openPlayerShopAdmin(player, shop);
            return;
        }
        if (shop.stock() > 0L
                && !ItemStack.isSameItemSameComponents(template, shop.template())) {
            message(player, false, "message.numismatics_treasury.player_shop.empty_before_change");
            TreasuryNetwork.openPlayerShopAdmin(player, shop);
            return;
        }
        shop.configurePlayer(template, price, lotSize);
        message(player, true, "message.numismatics_treasury.player_shop.configured");
        TreasuryNetwork.openPlayerShopAdmin(player, shop);
    }

    private static void configurePlayerShopMenu(ServerPlayer player, JsonObject data) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return;
        if (!(player.containerMenu instanceof PlayerShopMenu menu)
                || !menu.matches(shop)) {
            message(player, false, "message.numismatics_treasury.player_shop.menu_invalid");
            return;
        }
        int price = data.get("price").getAsInt();
        if (price <= 0 || price > 2_000_000_000) {
            message(player, false, "message.numismatics_treasury.price_invalid");
            return;
        }
        int lotSize = configuredLotSize(data, shop);
        if (!validLotSize(lotSize)) {
            message(player, false, "message.numismatics_treasury.quantity_invalid");
            return;
        }
        ItemStack pending = menu.inputStack();
        ItemStack template = pending.isEmpty() ? shop.template() : pending;
        if (template.isEmpty()) {
            message(player, false, "message.numismatics_treasury.player_shop.item_required");
            return;
        }
        if (shop.stock() > 0L
                && !ItemStack.isSameItemSameComponents(template, shop.template())) {
            message(player, false, "message.numismatics_treasury.player_shop.empty_before_change");
            return;
        }
        shop.configurePlayer(template, price, lotSize);
        int deposited = menu.depositPendingInput();
        if (deposited < 0) {
            message(player, false, "message.numismatics_treasury.player_shop.stock_too_large");
            return;
        }
        message(player, true, "message.numismatics_treasury.player_shop.configured");
        player.openMenu(shop);
    }

    private static void withdrawPlayerShopMenu(ServerPlayer player, JsonObject data) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return;
        if (!(player.containerMenu instanceof PlayerShopMenu menu)
                || !menu.matches(shop)) {
            message(player, false, "message.numismatics_treasury.player_shop.menu_invalid");
            return;
        }
        if (!menu.inputStack().isEmpty()) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.clear_input_first");
            return;
        }
        ItemStack template = shop.template();
        if (template.isEmpty() || shop.stock() <= 0L) {
            message(player, false, "message.numismatics_treasury.player_shop.no_stock");
            return;
        }
        int amount = (int) Math.min(shop.stock(), InventoryUtil.capacity(player, template));
        if (amount <= 0) {
            message(player, false, "message.numismatics_treasury.inventory_no_space");
            return;
        }
        if (shop.removeStock(amount)) {
            InventoryUtil.give(player, template, amount);
            message(player, true, "message.numismatics_treasury.player_shop.withdrawn", amount);
        }
    }

    private static void openPlayerShopAssociates(
            ServerPlayer player,
            JsonObject data
    ) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return;
        if (!(player.containerMenu instanceof PlayerShopMenu menu)
                || !menu.matches(shop)) {
            message(player, false, "message.numismatics_treasury.player_shop.menu_invalid");
            return;
        }
        if (!shop.canEditRevenueSplit(player)) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.split_owner_only");
            return;
        }
        player.closeContainer();
        TreasuryNetwork.openPlayerShopAssociates(player, shop);
    }

    private static void savePlayerShopAssociate(
            ServerPlayer player,
            JsonObject data
    ) {
        ServerShopBlockEntity shop = editablePlayerShop(player, data);
        if (shop == null) return;

        String linkedName = data.get("linkedPlayer").getAsString().strip();
        if (linkedName.isEmpty()) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.link_name_required");
            return;
        }

        int percent = data.get("linkedPercent").getAsInt();
        if (percent < 1 || percent > 100) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.split_invalid");
            return;
        }
        Optional<GameProfile> linkedProfile = ProfileResolver.resolve(
                player.getServer(), linkedName);
        if (linkedProfile.isEmpty()) {
            message(player, false,
                    "message.numismatics_treasury.player_unknown", linkedName);
            return;
        }
        GameProfile profile = linkedProfile.get();
        if (profile.getId().equals(shop.ownerUuid())) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.link_self");
            return;
        }
        if (BankService.existingAccount(profile.getId()) == null) {
            message(player, false,
                    "message.numismatics_treasury.pay.no_account", profile.getName());
            return;
        }

        int previousPercent = shop.linkedPlayers().stream()
                .filter(linked -> linked.uuid().equals(profile.getId()))
                .mapToInt(ServerShopBlockEntity.LinkedPlayer::percent)
                .findFirst()
                .orElse(0);
        if (shop.linkedPercentTotal() - previousPercent + percent > 100) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.split_total_too_large");
            return;
        }
        if (previousPercent == 0
                && shop.linkedPlayers().size()
                >= ServerShopBlockEntity.MAX_LINKED_PLAYERS) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.link_limit",
                    ServerShopBlockEntity.MAX_LINKED_PLAYERS);
            return;
        }
        if (!shop.setLinkedPlayer(profile.getId(), profile.getName(), percent)) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.split_invalid");
            return;
        }
        message(player, true,
                "message.numismatics_treasury.player_shop.link_saved",
                profile.getName(), percent);
        if (previousPercent == 0) {
            notifyPlayerShopAssociateAdded(
                    player,
                    profile.getId(),
                    shop.ownerName(),
                    percent
            );
        } else if (previousPercent != percent) {
            notifyPlayerShopAssociateShareChanged(
                    player,
                    profile.getId(),
                    shop.ownerName(),
                    previousPercent,
                    percent
            );
        }
        TreasuryNetwork.openPlayerShopAssociates(player, shop);
    }

    private static void removePlayerShopAssociate(
            ServerPlayer player,
            JsonObject data
    ) {
        ServerShopBlockEntity shop = editablePlayerShop(player, data);
        if (shop == null) return;
        UUID linkedUuid = UUID.fromString(data.get("linkedUuid").getAsString());
        ServerShopBlockEntity.LinkedPlayer linked = shop.linkedPlayers().stream()
                .filter(value -> value.uuid().equals(linkedUuid))
                .findFirst()
                .orElse(null);
        if (linked == null || !shop.removeLinkedPlayer(linkedUuid)) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.link_not_found", "?");
            return;
        }
        message(player, true,
                "message.numismatics_treasury.player_shop.link_removed", linked.name());
        TreasuryNetwork.openPlayerShopAssociates(player, shop);
    }

    private static void backToPlayerShop(ServerPlayer player, JsonObject data) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop != null) player.openMenu(shop);
    }

    private static void openHistory(ServerPlayer player, JsonObject data) {
        if (!TreasuryConfig.get().history.enabled) {
            message(player, false, "message.numismatics_treasury.history.disabled");
            return;
        }
        String source = data.has("source") ? data.get("source").getAsString() : "command";
        boolean portable = data.has("portable") && data.get("portable").getAsBoolean();
        BlockPos returnPos = data.has("pos")
                ? BlockPos.of(data.get("pos").getAsLong()) : null;
        TreasuryNetwork.openHistoryStats(
                player,
                source,
                portable,
                returnPos,
                null
        );
    }

    private static void openPlayerShopStats(ServerPlayer player, JsonObject data) {
        if (!TreasuryConfig.get().history.enabled) {
            message(player, false, "message.numismatics_treasury.history.disabled");
            return;
        }
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return;
        if (!(player.containerMenu instanceof PlayerShopMenu menu)
                || !menu.matches(shop)) {
            message(player, false, "message.numismatics_treasury.player_shop.menu_invalid");
            return;
        }
        player.closeContainer();
        TreasuryNetwork.openHistoryStats(
                player,
                "player_shop",
                false,
                shop.getBlockPos(),
                shop
        );
    }

    private static void closeHistory(ServerPlayer player, JsonObject data) {
        String source = data.has("source") ? data.get("source").getAsString() : "command";
        if (source.equals("bank")) {
            boolean portable = data.has("portable") && data.get("portable").getAsBoolean();
            if (portable && holdsPortableTerminal(player)) {
                TreasuryNetwork.openPortableBankTeller(player);
            } else if (!portable && data.has("returnPos")) {
                BlockPos pos = BlockPos.of(data.get("returnPos").getAsLong());
                if (near(player, pos)
                        && player.level().getBlockState(pos)
                        .is(TreasuryContent.BANK_TELLER.get())) {
                    TreasuryNetwork.openBankTeller(player, pos);
                }
            }
            return;
        }
        if (source.equals("player_shop") && data.has("returnPos")) {
            JsonObject shopData = new JsonObject();
            shopData.addProperty("pos", data.get("returnPos").getAsLong());
            ServerShopBlockEntity shop = playerShop(player, shopData, true);
            if (shop != null) player.openMenu(shop);
        }
    }

    private static ServerShopBlockEntity editablePlayerShop(
            ServerPlayer player,
            JsonObject data
    ) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return null;
        if (!shop.canEditRevenueSplit(player)) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.split_owner_only");
            return null;
        }
        return shop;
    }

    private static void depositPlayerShop(ServerPlayer player, JsonObject data) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return;
        int slot = data.get("inventorySlot").getAsInt();
        if (slot < 0 || slot >= player.getInventory().items.size()) {
            message(player, false, "message.numismatics_treasury.inventory_selection_invalid");
            TreasuryNetwork.openPlayerShopAdmin(player, shop);
            return;
        }
        ItemStack selected = player.getInventory().items.get(slot);
        if (!shop.configured()) {
            message(player, false, "message.numismatics_treasury.player_shop.configure_first");
        } else if (selected.isEmpty()
                || !ItemStack.isSameItemSameComponents(selected, shop.template())) {
            message(player, false, "message.numismatics_treasury.player_shop.wrong_item");
        } else if (!shop.addStock(selected.getCount())) {
            message(player, false, "message.numismatics_treasury.player_shop.stock_too_large");
        } else {
            int deposited = selected.getCount();
            player.getInventory().items.set(slot, ItemStack.EMPTY);
            player.getInventory().setChanged();
            message(player, true, "message.numismatics_treasury.player_shop.deposited", deposited);
        }
        TreasuryNetwork.openPlayerShopAdmin(player, shop);
    }

    private static void withdrawPlayerShop(ServerPlayer player, JsonObject data) {
        ServerShopBlockEntity shop = playerShop(player, data, true);
        if (shop == null) return;
        ItemStack template = shop.template();
        int amount = (int) Math.min(shop.stock(), template.getMaxStackSize());
        if (template.isEmpty() || amount <= 0) {
            message(player, false, "message.numismatics_treasury.player_shop.no_stock");
        } else if (!InventoryUtil.canFit(player, template, amount)) {
            message(player, false, "message.numismatics_treasury.inventory_no_space");
        } else if (shop.removeStock(amount)) {
            InventoryUtil.give(player, template, amount);
            message(player, true, "message.numismatics_treasury.player_shop.withdrawn", amount);
        }
        TreasuryNetwork.openPlayerShopAdmin(player, shop);
    }

    private static void tradePlayerShop(ServerPlayer player, JsonObject data) {
        ServerShopBlockEntity shop = playerShop(player, data, false);
        if (shop == null) return;
        int lots = requestedLots(data);
        int quantity;
        try {
            quantity = Math.multiplyExact(shop.lotSize(), lots);
        } catch (ArithmeticException exception) {
            message(player, false, "message.numismatics_treasury.quantity_invalid");
            TreasuryNetwork.openPlayerShop(player, shop);
            return;
        }
        if (lots <= 0 || quantity <= 0 || quantity > MAX_TRANSACTION_ITEMS) {
            message(player, false, "message.numismatics_treasury.quantity_invalid");
            TreasuryNetwork.openPlayerShop(player, shop);
            return;
        }
        if (shop.ownerUuid() == null || shop.isAssociated(player.getUUID())) {
            message(player, false, "message.numismatics_treasury.player_shop.own_shop");
            return;
        }
        if (!shop.configured() || shop.stock() < quantity) {
            message(player, false, "message.numismatics_treasury.player_shop.insufficient_stock");
            TreasuryNetwork.openPlayerShop(player, shop);
            return;
        }
        ItemStack template = shop.template();
        if (!InventoryUtil.canFit(player, template, quantity)) {
            message(player, false, "message.numismatics_treasury.inventory_no_space");
            TreasuryNetwork.openPlayerShop(player, shop);
            return;
        }
        int total;
        try {
            total = Math.multiplyExact(shop.price(), lots);
        } catch (ArithmeticException exception) {
            message(player, false, "message.numismatics_treasury.amount_too_large");
            return;
        }
        UUID ownerUuid = shop.ownerUuid();
        List<RevenueShare> linkedShares = new ArrayList<>();
        int linkedTotal = 0;
        for (ServerShopBlockEntity.LinkedPlayer linkedPlayer : shop.linkedPlayers()) {
            int amount = shop.linkedPlayerShare(linkedPlayer, total);
            linkedTotal += amount;
            if (amount > 0) {
                linkedShares.add(new RevenueShare(linkedPlayer.uuid(), amount));
            }
        }
        int ownerShare = total - linkedTotal;
        if (BankService.existingAccount(ownerUuid) == null) {
            message(player, false, "message.numismatics_treasury.player_shop.owner_account_unavailable");
        } else if (linkedShares.stream().anyMatch(share ->
                BankService.existingAccount(share.playerUuid()) == null)) {
            message(player, false,
                    "message.numismatics_treasury.player_shop.linked_account_unavailable");
        } else if (!BankService.debit(player, total)) {
            message(player, false, "message.numismatics_treasury.balance_insufficient",
                    BankService.balance(player));
        } else {
            List<RevenueShare> credited = new ArrayList<>();
            boolean creditedAll = creditRevenueShare(
                    credited, new RevenueShare(ownerUuid, ownerShare));
            for (RevenueShare share : linkedShares) {
                if (!creditedAll) break;
                creditedAll = creditRevenueShare(credited, share);
            }
            if (!creditedAll) {
                for (RevenueShare share : credited) {
                    BankService.debit(share.playerUuid(), share.amount());
                }
                BankService.credit(player, total);
                message(player, false,
                        "message.numismatics_treasury.player_shop.payment_failed");
            } else {
                shop.removeStock(quantity);
                InventoryUtil.give(player, template, quantity);
                message(player, true,
                        "message.numismatics_treasury.player_shop.purchase", total);
                notifyPlayerShopBeneficiary(
                        player, ownerUuid, template, quantity, ownerShare);
                for (RevenueShare share : linkedShares) {
                    notifyPlayerShopBeneficiary(
                            player,
                            share.playerUuid(),
                            template,
                            quantity,
                            share.amount()
                    );
                }
                Map<UUID, HistoryData.BeneficiaryShare> distribution =
                        new LinkedHashMap<>();
                distribution.put(ownerUuid, new HistoryData.BeneficiaryShare(
                        shop.ownerName(), ownerShare));
                for (ServerShopBlockEntity.LinkedPlayer linked : shop.linkedPlayers()) {
                    distribution.put(linked.uuid(),
                            new HistoryData.BeneficiaryShare(
                                    linked.name(),
                                    shop.linkedPlayerShare(linked, total)
                            ));
                }
                HistoryService.recordPlayerShopSale(
                        player, shop, template, quantity, total, distribution);
            }
        }
        TreasuryNetwork.openPlayerShop(player, shop);
    }

    private static int configuredLotSize(
            JsonObject data,
            ServerShopBlockEntity shop
    ) {
        return data.has("lotSize") ? data.get("lotSize").getAsInt() : shop.lotSize();
    }

    private static int requestedLots(JsonObject data) {
        return data.has("lots") ? data.get("lots").getAsInt() : 0;
    }

    private static boolean validLotSize(int lotSize) {
        return lotSize > 0 && lotSize <= MAX_TRANSACTION_ITEMS;
    }

    private static ServerShopBlockEntity playerShop(
            ServerPlayer player,
            JsonObject data,
            boolean management
    ) {
        if (!TreasuryConfig.get().modules.playerShop) {
            message(player, false, "message.numismatics_treasury.player_shop.disabled");
            return null;
        }
        BlockPos pos = BlockPos.of(data.get("pos").getAsLong());
        if (!near(player, pos)
                || !player.level().getBlockState(pos).is(TreasuryContent.PLAYER_SHOP.get())
                || !(player.level().getBlockEntity(pos) instanceof ServerShopBlockEntity shop)) {
            message(player, false, "message.numismatics_treasury.player_shop.missing");
            return null;
        }
        if (management && !shop.canManage(player)) {
            message(player, false, "message.numismatics_treasury.player_shop.not_owner");
            return null;
        }
        return shop;
    }

    private static void notifyPlayerShopBeneficiary(
            ServerPlayer buyer,
            UUID beneficiaryUuid,
            ItemStack item,
            int quantity,
            int received
    ) {
        if (beneficiaryUuid == null) return;
        Object[] arguments = {
                buyer.getGameProfile().getName(),
                item.getHoverName().getString(),
                quantity,
                received
        };
        ServerPlayer beneficiary = buyer.getServer().getPlayerList()
                .getPlayer(beneficiaryUuid);
        if (beneficiary != null) {
            beneficiary.sendSystemMessage(Component.translatable(
                    "notification.numismatics_treasury.player_shop.sold",
                    arguments
            ));
        } else {
            TreasuryData.get(buyer.getServer()).addNotification(
                    beneficiaryUuid,
                    "notification.numismatics_treasury.player_shop.sold",
                    arguments
            );
        }
    }

    private static void notifyPlayerShopAssociateAdded(
            ServerPlayer editor,
            UUID associateUuid,
            String ownerName,
            int percent
    ) {
        Object[] arguments = {ownerName, percent};
        ServerPlayer associate = editor.getServer().getPlayerList()
                .getPlayer(associateUuid);
        if (associate != null) {
            associate.sendSystemMessage(Component.translatable(
                    "notification.numismatics_treasury.player_shop.associate_added",
                    arguments
            ));
        } else {
            TreasuryData.get(editor.getServer()).addNotification(
                    associateUuid,
                    "notification.numismatics_treasury.player_shop.associate_added",
                    arguments
            );
        }
    }

    private static void notifyPlayerShopAssociateShareChanged(
            ServerPlayer editor,
            UUID associateUuid,
            String ownerName,
            int previousPercent,
            int percent
    ) {
        Object[] arguments = {ownerName, previousPercent, percent};
        ServerPlayer associate = editor.getServer().getPlayerList()
                .getPlayer(associateUuid);
        if (associate != null) {
            associate.sendSystemMessage(Component.translatable(
                    "notification.numismatics_treasury.player_shop.associate_share_changed",
                    arguments
            ));
        } else {
            TreasuryData.get(editor.getServer()).addNotification(
                    associateUuid,
                    "notification.numismatics_treasury.player_shop.associate_share_changed",
                    arguments
            );
        }
    }

    private static boolean creditRevenueShare(
            List<RevenueShare> credited,
            RevenueShare share
    ) {
        if (share.amount() <= 0) return true;
        if (!BankService.credit(share.playerUuid(), share.amount())) return false;
        credited.add(share);
        return true;
    }

    private record RevenueShare(UUID playerUuid, int amount) {
    }

    private static void auctionCreate(ServerPlayer player, JsonObject data) {
        BlockPos terminal = terminal(player, data, false);
        if (terminal == null) return;
        AuctionService.Result result = AuctionService.create(
                player,
                ListingType.parse(data.get("type").getAsString()),
                data.get("price").getAsInt(),
                data.get("durationHours").getAsInt(),
                data.get("inventorySlot").getAsInt()
        );
        message(player, result.successful(), result.message());
        if (result.successful()) {
            TreasuryNetwork.broadcastAuctionUpdate(player.getServer(), player);
            TreasuryNetwork.openAuctionHouse(player, terminal, "MINE");
        } else {
            TreasuryNetwork.openAuctionHouse(player, terminal);
        }
    }

    private static void auctionBuy(ServerPlayer player, JsonObject data) {
        BlockPos terminal = terminal(player, data, false);
        if (terminal == null) return;
        result(player, AuctionService.buy(
                player,
                UUID.fromString(data.get("id").getAsString()),
                data.get("revision").getAsLong()
        ), true, terminal);
    }

    private static void auctionBid(ServerPlayer player, JsonObject data) {
        BlockPos terminal = terminal(player, data, false);
        if (terminal == null) return;
        result(player, AuctionService.bid(
                player,
                UUID.fromString(data.get("id").getAsString()),
                data.get("revision").getAsLong(),
                data.get("amount").getAsInt()
        ), true, terminal);
    }

    private static void auctionCancel(ServerPlayer player, JsonObject data) {
        BlockPos terminal = terminal(player, data, false);
        if (terminal == null) return;
        result(player, AuctionService.cancel(
                player,
                UUID.fromString(data.get("id").getAsString())
        ), true, terminal);
    }

    private static void auctionClaim(ServerPlayer player, JsonObject data) {
        BlockPos terminal = terminal(player, data, false);
        if (terminal == null) return;
        result(player, AuctionService.claim(player), false, terminal);
    }

    private static void auctionRefresh(ServerPlayer player, JsonObject data) {
        BlockPos terminal = terminal(player, data, false);
        if (terminal != null) TreasuryNetwork.openAuctionHouse(player, terminal);
    }

    private static void result(
            ServerPlayer player,
            AuctionService.Result result,
            boolean marketChanged,
            BlockPos terminal
    ) {
        message(player, result.successful(), result.message());
        if (result.successful() && marketChanged) {
            TreasuryNetwork.broadcastAuctionUpdate(player.getServer());
        } else {
            TreasuryNetwork.openAuctionHouse(player, terminal);
        }
    }

    private static BlockPos terminal(
            ServerPlayer player,
            JsonObject data,
            boolean bankTeller
    ) {
        if (!data.has("pos")) {
            message(player, false, "message.numismatics_treasury.terminal_invalid");
            return null;
        }
        BlockPos pos = BlockPos.of(data.get("pos").getAsLong());
        boolean correctBlock = player.level().hasChunkAt(pos) && player.level()
                .getBlockState(pos)
                .is(bankTeller
                        ? TreasuryContent.BANK_TELLER.get()
                        : TreasuryContent.AUCTION_HOUSE.get());
        if (!near(player, pos) || !correctBlock) {
            message(player, false, "message.numismatics_treasury.terminal_too_far");
            return null;
        }
        return pos;
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.canInteractWithBlock(pos, 4.0D);
    }

    private static void message(
            ServerPlayer player,
            boolean success,
            String key,
            Object... arguments
    ) {
        message(player, success, Component.translatable(key, arguments));
    }

    private static void message(ServerPlayer player, boolean success, Component text) {
        player.sendSystemMessage(text.copy().withStyle(
                success ? ChatFormatting.GREEN : ChatFormatting.RED
        ));
    }
}
