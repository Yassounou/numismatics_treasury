package dev.yassou.numismaticstreasury.server.history;

import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Financial history stored separately from shops and auction data. */
public final class HistoryData extends SavedData {
    private static final String DATA_NAME = "numismatics_treasury_history";
    private static final Factory<HistoryData> FACTORY =
            new Factory<>(HistoryData::new, HistoryData::load);

    private final Map<UUID, List<HistoryEntry>> playerEntries = new LinkedHashMap<>();
    private final Map<UUID, PlayerTotals> playerTotals = new LinkedHashMap<>();
    private final Map<String, ShopStats> shops = new LinkedHashMap<>();
    private final GlobalStats global = new GlobalStats();

    public static HistoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(UUID playerUuid, HistoryEntry entry) {
        if (playerUuid == null || entry == null) return;
        List<HistoryEntry> entries = playerEntries.computeIfAbsent(
                playerUuid,
                ignored -> new ArrayList<>()
        );
        entries.addFirst(entry);
        prune(entries, TreasuryConfig.get().history.maximumEntriesPerPlayer);
        playerTotals.computeIfAbsent(playerUuid, ignored -> new PlayerTotals())
                .apply(entry);
        setDirty();
    }

    public List<HistoryEntry> entries(UUID playerUuid) {
        List<HistoryEntry> entries = playerEntries.get(playerUuid);
        if (entries == null) return List.of();
        int previousSize = entries.size();
        prune(entries, TreasuryConfig.get().history.maximumEntriesPerPlayer);
        if (entries.size() != previousSize) setDirty();
        return List.copyOf(entries);
    }

    public PlayerTotals totals(UUID playerUuid) {
        return playerTotals.getOrDefault(playerUuid, new PlayerTotals()).copy();
    }

    public ShopStats ensureShop(
            String key,
            UUID ownerUuid,
            String ownerName,
            String dimension,
            long pos,
            String itemId,
            String itemName,
            ItemStack item
    ) {
        ShopStats stats = shops.computeIfAbsent(key, ignored -> new ShopStats(
                key,
                ownerUuid,
                ownerName,
                dimension,
                pos,
                System.currentTimeMillis()
        ));
        stats.update(ownerUuid, ownerName, itemId, itemName, item);
        setDirty();
        return stats;
    }

    public void recordShopSale(
            String key,
            UUID ownerUuid,
            String ownerName,
            String dimension,
            long pos,
            String itemId,
            String itemName,
            ItemStack item,
            String buyerName,
            int quantity,
            int total,
            Map<UUID, BeneficiaryShare> shares
    ) {
        ShopStats stats = ensureShop(
                key, ownerUuid, ownerName, dimension, pos, itemId, itemName, item);
        stats.recordSale(buyerName, quantity, total, shares);
        pruneSales(stats.recentSales);
        setDirty();
    }

    public ShopStats shop(String key) {
        return shops.get(key);
    }

    public List<ShopStats> shopsFor(UUID playerUuid) {
        shops.values().forEach(stats -> pruneSales(stats.recentSales));
        return shops.values().stream()
                .filter(stats -> stats.ownerUuid.equals(playerUuid)
                        || stats.beneficiaries.containsKey(playerUuid))
                .sorted(Comparator.comparingLong(ShopStats::lastSaleAt).reversed())
                .toList();
    }

    public void removeShop(String key) {
        if (shops.remove(key) != null) setDirty();
    }

    public GlobalStats global() {
        return global.copy();
    }

    public void recordGlobal(GlobalCategory category, long amount, long commission) {
        global.transactions++;
        global.totalVolume += Math.max(0L, amount);
        switch (category) {
            case TRANSFER -> global.transferVolume += Math.max(0L, amount);
            case SERVER_SHOP -> global.serverShopVolume += Math.max(0L, amount);
            case PLAYER_SHOP -> global.playerShopVolume += Math.max(0L, amount);
            case AUCTION -> global.auctionVolume += Math.max(0L, amount);
        }
        global.commissionCollected += Math.max(0L, commission);
        setDirty();
    }

    private static void prune(List<HistoryEntry> entries, int maximum) {
        long cutoff = System.currentTimeMillis()
                - TreasuryConfig.get().history.retentionDays * 86_400_000L;
        entries.removeIf(entry -> entry.timestamp() < cutoff);
        if (entries.size() > maximum) entries.subList(maximum, entries.size()).clear();
    }

    private static void pruneSales(List<ShopSale> sales) {
        long cutoff = System.currentTimeMillis()
                - TreasuryConfig.get().history.retentionDays * 86_400_000L;
        sales.removeIf(sale -> sale.timestamp < cutoff);
        int maximum = TreasuryConfig.get().history.maximumEntriesPerShop;
        if (sales.size() > maximum) sales.subList(maximum, sales.size()).clear();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        playerEntries.forEach((uuid, entries) -> {
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", uuid);
            ListTag savedEntries = new ListTag();
            entries.forEach(entry -> savedEntries.add(entry.save(registries)));
            player.put("entries", savedEntries);
            PlayerTotals totals = playerTotals.get(uuid);
            if (totals != null) player.put("totals", totals.save());
            players.add(player);
        });
        tag.put("players", players);

        ListTag savedShops = new ListTag();
        shops.values().forEach(shop -> savedShops.add(shop.save(registries)));
        tag.put("shops", savedShops);
        tag.put("global", global.save());
        return tag;
    }

    private static HistoryData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        HistoryData data = new HistoryData();
        for (Tag value : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) value;
            if (!player.hasUUID("uuid")) continue;
            UUID uuid = player.getUUID("uuid");
            List<HistoryEntry> entries = new ArrayList<>();
            for (Tag saved : player.getList("entries", Tag.TAG_COMPOUND)) {
                HistoryEntry entry = HistoryEntry.load((CompoundTag) saved, registries);
                if (entry != null) entries.add(entry);
            }
            entries.sort(Comparator.comparingLong(HistoryEntry::timestamp).reversed());
            data.playerEntries.put(uuid, entries);
            if (player.contains("totals", Tag.TAG_COMPOUND)) {
                data.playerTotals.put(uuid, PlayerTotals.load(player.getCompound("totals")));
            }
        }
        for (Tag value : tag.getList("shops", Tag.TAG_COMPOUND)) {
            ShopStats shop = ShopStats.load((CompoundTag) value, registries);
            if (shop != null) data.shops.put(shop.key, shop);
        }
        if (tag.contains("global", Tag.TAG_COMPOUND)) {
            data.global.loadFrom(tag.getCompound("global"));
        }
        return data;
    }

    public enum GlobalCategory {
        TRANSFER,
        SERVER_SHOP,
        PLAYER_SHOP,
        AUCTION
    }

    public record BeneficiaryShare(String name, long amount) {
    }

    public static final class PlayerTotals {
        private long earned;
        private long spent;
        private long sent;
        private long received;
        private long shopEarned;
        private long shopSpent;
        private long auctionEarned;
        private long auctionSpent;
        private long transactions;

        private void apply(HistoryEntry entry) {
            transactions++;
            long amount = Math.abs(entry.amount());
            switch (entry.type()) {
                case TRANSFER_SENT -> sent += amount;
                case TRANSFER_RECEIVED -> received += amount;
                case SERVER_SHOP_PURCHASE, PLAYER_SHOP_PURCHASE -> {
                    spent += amount;
                    shopSpent += amount;
                }
                case SERVER_SHOP_SALE, PLAYER_SHOP_REVENUE -> {
                    earned += amount;
                    shopEarned += amount;
                }
                case AUCTION_PURCHASE, AUCTION_WON -> {
                    spent += amount;
                    auctionSpent += amount;
                }
                case AUCTION_SALE -> {
                    earned += amount;
                    auctionEarned += amount;
                }
                default -> {
                }
            }
        }

        public long earned() { return earned; }
        public long spent() { return spent; }
        public long sent() { return sent; }
        public long received() { return received; }
        public long shopEarned() { return shopEarned; }
        public long shopSpent() { return shopSpent; }
        public long auctionEarned() { return auctionEarned; }
        public long auctionSpent() { return auctionSpent; }
        public long transactions() { return transactions; }

        private PlayerTotals copy() {
            PlayerTotals copy = new PlayerTotals();
            copy.earned = earned;
            copy.spent = spent;
            copy.sent = sent;
            copy.received = received;
            copy.shopEarned = shopEarned;
            copy.shopSpent = shopSpent;
            copy.auctionEarned = auctionEarned;
            copy.auctionSpent = auctionSpent;
            copy.transactions = transactions;
            return copy;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("earned", earned);
            tag.putLong("spent", spent);
            tag.putLong("sent", sent);
            tag.putLong("received", received);
            tag.putLong("shopEarned", shopEarned);
            tag.putLong("shopSpent", shopSpent);
            tag.putLong("auctionEarned", auctionEarned);
            tag.putLong("auctionSpent", auctionSpent);
            tag.putLong("transactions", transactions);
            return tag;
        }

        private static PlayerTotals load(CompoundTag tag) {
            PlayerTotals totals = new PlayerTotals();
            totals.earned = tag.getLong("earned");
            totals.spent = tag.getLong("spent");
            totals.sent = tag.getLong("sent");
            totals.received = tag.getLong("received");
            totals.shopEarned = tag.getLong("shopEarned");
            totals.shopSpent = tag.getLong("shopSpent");
            totals.auctionEarned = tag.getLong("auctionEarned");
            totals.auctionSpent = tag.getLong("auctionSpent");
            totals.transactions = tag.getLong("transactions");
            return totals;
        }
    }

    public static final class ShopStats {
        private final String key;
        private UUID ownerUuid;
        private String ownerName;
        private final String dimension;
        private final long pos;
        private String itemId = "minecraft:air";
        private String itemName = "";
        private ItemStack item = ItemStack.EMPTY;
        private final long startedAt;
        private long sales;
        private long itemsSold;
        private long grossRevenue;
        private long bestSale;
        private long lastSaleAt;
        private final List<ShopSale> recentSales = new ArrayList<>();
        private final Map<UUID, BeneficiaryTotal> beneficiaries = new LinkedHashMap<>();

        private ShopStats(
                String key,
                UUID ownerUuid,
                String ownerName,
                String dimension,
                long pos,
                long startedAt
        ) {
            this.key = key;
            this.ownerUuid = ownerUuid;
            this.ownerName = safe(ownerName);
            this.dimension = safe(dimension);
            this.pos = pos;
            this.startedAt = startedAt;
        }

        private void update(
                UUID ownerUuid,
                String ownerName,
                String itemId,
                String itemName,
                ItemStack item
        ) {
            this.ownerUuid = ownerUuid;
            this.ownerName = safe(ownerName);
            this.itemId = safe(itemId);
            this.itemName = safe(itemName);
            this.item = item == null || item.isEmpty()
                    ? ItemStack.EMPTY : item.copyWithCount(1);
        }

        private void recordSale(
                String buyerName,
                int quantity,
                int total,
                Map<UUID, BeneficiaryShare> shares
        ) {
            long now = System.currentTimeMillis();
            sales++;
            itemsSold += Math.max(0, quantity);
            grossRevenue += Math.max(0, total);
            bestSale = Math.max(bestSale, total);
            lastSaleAt = now;
            recentSales.addFirst(new ShopSale(
                    now,
                    safe(buyerName),
                    Math.max(0, quantity),
                    Math.max(0, total)
            ));
            shares.forEach((uuid, share) -> beneficiaries.computeIfAbsent(
                    uuid,
                    ignored -> new BeneficiaryTotal(share.name, 0L)
            ).add(share.name, share.amount));
        }

        public String key() { return key; }
        public UUID ownerUuid() { return ownerUuid; }
        public String ownerName() { return ownerName; }
        public String dimension() { return dimension; }
        public long pos() { return pos; }
        public String itemId() { return itemId; }
        public String itemName() { return itemName; }
        public ItemStack item() { return item.copy(); }
        public long startedAt() { return startedAt; }
        public long sales() { return sales; }
        public long itemsSold() { return itemsSold; }
        public long grossRevenue() { return grossRevenue; }
        public long bestSale() { return bestSale; }
        public long lastSaleAt() { return lastSaleAt; }
        public Map<UUID, BeneficiaryTotal> beneficiaries() {
            return Map.copyOf(beneficiaries);
        }

        public PeriodStats period(long since) {
            long sales = 0L;
            long items = 0L;
            long revenue = 0L;
            for (ShopSale sale : recentSales) {
                if (sale.timestamp < since) continue;
                sales++;
                items += sale.quantity;
                revenue += sale.total;
            }
            return new PeriodStats(sales, items, revenue);
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", key);
            if (ownerUuid != null) tag.putUUID("ownerUuid", ownerUuid);
            tag.putString("ownerName", ownerName);
            tag.putString("dimension", dimension);
            tag.putLong("pos", pos);
            tag.putString("itemId", itemId);
            tag.putString("itemName", itemName);
            if (!item.isEmpty()) tag.put("item", item.saveOptional(registries));
            tag.putLong("startedAt", startedAt);
            tag.putLong("sales", sales);
            tag.putLong("itemsSold", itemsSold);
            tag.putLong("grossRevenue", grossRevenue);
            tag.putLong("bestSale", bestSale);
            tag.putLong("lastSaleAt", lastSaleAt);
            ListTag savedSales = new ListTag();
            recentSales.forEach(sale -> savedSales.add(sale.save()));
            tag.put("recentSales", savedSales);
            ListTag savedBeneficiaries = new ListTag();
            beneficiaries.forEach((uuid, total) -> {
                CompoundTag saved = total.save();
                saved.putUUID("uuid", uuid);
                savedBeneficiaries.add(saved);
            });
            tag.put("beneficiaries", savedBeneficiaries);
            return tag;
        }

        private static ShopStats load(
                CompoundTag tag,
                HolderLookup.Provider registries
        ) {
            if (!tag.hasUUID("ownerUuid") || tag.getString("key").isBlank()) return null;
            ShopStats stats = new ShopStats(
                    tag.getString("key"),
                    tag.getUUID("ownerUuid"),
                    tag.getString("ownerName"),
                    tag.getString("dimension"),
                    tag.getLong("pos"),
                    tag.getLong("startedAt")
            );
            stats.itemId = tag.getString("itemId");
            stats.itemName = tag.getString("itemName");
            stats.item = tag.contains("item", Tag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(registries, tag.getCompound("item"))
                    : ItemStack.EMPTY;
            stats.sales = tag.getLong("sales");
            stats.itemsSold = tag.getLong("itemsSold");
            stats.grossRevenue = tag.getLong("grossRevenue");
            stats.bestSale = tag.getLong("bestSale");
            stats.lastSaleAt = tag.getLong("lastSaleAt");
            for (Tag value : tag.getList("recentSales", Tag.TAG_COMPOUND)) {
                stats.recentSales.add(ShopSale.load((CompoundTag) value));
            }
            for (Tag value : tag.getList("beneficiaries", Tag.TAG_COMPOUND)) {
                CompoundTag saved = (CompoundTag) value;
                if (saved.hasUUID("uuid")) {
                    stats.beneficiaries.put(
                            saved.getUUID("uuid"),
                            BeneficiaryTotal.load(saved)
                    );
                }
            }
            return stats;
        }
    }

    public static final class BeneficiaryTotal {
        private String name;
        private long revenue;

        private BeneficiaryTotal(String name, long revenue) {
            this.name = safe(name);
            this.revenue = revenue;
        }

        private void add(String name, long amount) {
            this.name = safe(name);
            revenue += Math.max(0L, amount);
        }

        public String name() { return name; }
        public long revenue() { return revenue; }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putLong("revenue", revenue);
            return tag;
        }

        private static BeneficiaryTotal load(CompoundTag tag) {
            return new BeneficiaryTotal(tag.getString("name"), tag.getLong("revenue"));
        }
    }

    public record PeriodStats(long sales, long items, long revenue) {
    }

    private static final class ShopSale {
        private final long timestamp;
        private final String buyerName;
        private final int quantity;
        private final int total;

        private ShopSale(long timestamp, String buyerName, int quantity, int total) {
            this.timestamp = timestamp;
            this.buyerName = buyerName;
            this.quantity = quantity;
            this.total = total;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("timestamp", timestamp);
            tag.putString("buyerName", buyerName);
            tag.putInt("quantity", quantity);
            tag.putInt("total", total);
            return tag;
        }

        private static ShopSale load(CompoundTag tag) {
            return new ShopSale(
                    tag.getLong("timestamp"),
                    tag.getString("buyerName"),
                    tag.getInt("quantity"),
                    tag.getInt("total")
            );
        }
    }

    public static final class GlobalStats {
        private long transactions;
        private long totalVolume;
        private long transferVolume;
        private long serverShopVolume;
        private long playerShopVolume;
        private long auctionVolume;
        private long commissionCollected;

        public long transactions() { return transactions; }
        public long totalVolume() { return totalVolume; }
        public long transferVolume() { return transferVolume; }
        public long serverShopVolume() { return serverShopVolume; }
        public long playerShopVolume() { return playerShopVolume; }
        public long auctionVolume() { return auctionVolume; }
        public long commissionCollected() { return commissionCollected; }

        private GlobalStats copy() {
            GlobalStats copy = new GlobalStats();
            copy.transactions = transactions;
            copy.totalVolume = totalVolume;
            copy.transferVolume = transferVolume;
            copy.serverShopVolume = serverShopVolume;
            copy.playerShopVolume = playerShopVolume;
            copy.auctionVolume = auctionVolume;
            copy.commissionCollected = commissionCollected;
            return copy;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("transactions", transactions);
            tag.putLong("totalVolume", totalVolume);
            tag.putLong("transferVolume", transferVolume);
            tag.putLong("serverShopVolume", serverShopVolume);
            tag.putLong("playerShopVolume", playerShopVolume);
            tag.putLong("auctionVolume", auctionVolume);
            tag.putLong("commissionCollected", commissionCollected);
            return tag;
        }

        private void loadFrom(CompoundTag tag) {
            transactions = tag.getLong("transactions");
            totalVolume = tag.getLong("totalVolume");
            transferVolume = tag.getLong("transferVolume");
            serverShopVolume = tag.getLong("serverShopVolume");
            playerShopVolume = tag.getLong("playerShopVolume");
            auctionVolume = tag.getLong("auctionVolume");
            commissionCollected = tag.getLong("commissionCollected");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
