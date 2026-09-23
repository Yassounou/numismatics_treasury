package dev.yassou.numismaticstreasury.server.auction;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Global server-wide auction data, stored in the overworld SavedData. */
public final class TreasuryData extends SavedData {
    private static final Factory<TreasuryData> FACTORY =
            new Factory<>(TreasuryData::new, TreasuryData::load);

    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> itemClaims = new LinkedHashMap<>();
    private final Map<UUID, List<TreasuryNotification>> notifications = new LinkedHashMap<>();
    private final Set<UUID> disabledNotifications = new LinkedHashSet<>();

    public static TreasuryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FACTORY,
                NumismaticsTreasury.MOD_ID
        );
    }

    public List<AuctionListing> listings() {
        return listings.values().stream()
                .sorted(Comparator.comparingLong(AuctionListing::createdAt).reversed())
                .toList();
    }

    public AuctionListing listing(UUID id) {
        return listings.get(id);
    }

    public void put(AuctionListing listing) {
        listings.put(listing.id(), listing);
        setDirty();
    }

    public AuctionListing remove(UUID id) {
        AuctionListing removed = listings.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    public void changed() {
        setDirty();
    }

    public int listingCount(UUID sellerUuid) {
        return (int) listings.values().stream()
                .filter(listing -> listing.sellerUuid().equals(sellerUuid))
                .count();
    }

    public void addClaim(UUID playerUuid, ItemStack stack) {
        if (stack.isEmpty()) return;
        itemClaims.computeIfAbsent(playerUuid, ignored -> new ArrayList<>())
                .add(stack.copy());
        setDirty();
    }

    public List<ItemStack> claims(UUID playerUuid) {
        return itemClaims.getOrDefault(playerUuid, List.of()).stream()
                .map(ItemStack::copy)
                .toList();
    }

    public void replaceClaims(UUID playerUuid, List<ItemStack> claims) {
        if (claims.isEmpty()) itemClaims.remove(playerUuid);
        else {
            List<ItemStack> stored = new ArrayList<>(claims.size());
            claims.forEach(claim -> stored.add(claim.copy()));
            itemClaims.put(playerUuid, stored);
        }
        setDirty();
    }

    public void addNotification(UUID playerUuid, String key, Object... arguments) {
        if (!notificationsEnabled(playerUuid)) return;
        List<String> values = new ArrayList<>();
        for (Object argument : arguments) {
            values.add(argument instanceof net.minecraft.network.chat.Component component
                    ? component.getString() : String.valueOf(argument));
        }
        notifications.computeIfAbsent(playerUuid, ignored -> new ArrayList<>())
                .add(new TreasuryNotification(key, values));
        setDirty();
    }

    public List<TreasuryNotification> drainNotifications(UUID playerUuid) {
        List<TreasuryNotification> pending = notifications.remove(playerUuid);
        if (pending == null || pending.isEmpty()) return List.of();
        setDirty();
        return List.copyOf(pending);
    }

    public boolean notificationsEnabled(UUID playerUuid) {
        return playerUuid != null && !disabledNotifications.contains(playerUuid);
    }

    public void setNotificationsEnabled(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) return;
        boolean changed = enabled
                ? disabledNotifications.remove(playerUuid)
                : disabledNotifications.add(playerUuid);
        if (!enabled) changed |= notifications.remove(playerUuid) != null;
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag listingTags = new ListTag();
        for (AuctionListing listing : listings.values()) {
            listingTags.add(listing.save(registries));
        }
        tag.put("listings", listingTags);

        ListTag claimOwners = new ListTag();
        itemClaims.forEach((playerUuid, claims) -> {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("playerUuid", playerUuid);
            ListTag items = new ListTag();
            for (ItemStack claim : claims) {
                items.add(claim.saveOptional(registries));
            }
            owner.put("items", items);
            claimOwners.add(owner);
        });
        tag.put("itemClaims", claimOwners);

        ListTag notificationOwners = new ListTag();
        notifications.forEach((playerUuid, pending) -> {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("playerUuid", playerUuid);
            ListTag values = new ListTag();
            for (TreasuryNotification notification : pending) {
                values.add(notification.save());
            }
            owner.put("notifications", values);
            notificationOwners.add(owner);
        });
        tag.put("notifications", notificationOwners);

        ListTag disabled = new ListTag();
        for (UUID playerUuid : disabledNotifications) {
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", playerUuid);
            disabled.add(player);
        }
        tag.put("disabledNotifications", disabled);
        return tag;
    }

    private static TreasuryData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        TreasuryData data = new TreasuryData();
        for (Tag value : tag.getList("listings", Tag.TAG_COMPOUND)) {
            AuctionListing listing = AuctionListing.load((CompoundTag) value, registries);
            if (!listing.item().isEmpty()) data.listings.put(listing.id(), listing);
        }
        for (Tag value : tag.getList("itemClaims", Tag.TAG_COMPOUND)) {
            CompoundTag owner = (CompoundTag) value;
            if (!owner.hasUUID("playerUuid")) continue;
            List<ItemStack> items = new ArrayList<>();
            for (Tag itemTag : owner.getList("items", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(
                        registries,
                        (CompoundTag) itemTag
                );
                if (!stack.isEmpty()) items.add(stack);
            }
            if (!items.isEmpty()) data.itemClaims.put(owner.getUUID("playerUuid"), items);
        }
        for (Tag value : tag.getList("notifications", Tag.TAG_COMPOUND)) {
            CompoundTag owner = (CompoundTag) value;
            if (!owner.hasUUID("playerUuid")) continue;
            List<TreasuryNotification> pending = new ArrayList<>();
            for (Tag notificationTag : owner.getList("notifications", Tag.TAG_COMPOUND)) {
                TreasuryNotification notification = TreasuryNotification.load(
                        (CompoundTag) notificationTag
                );
                if (!notification.key().isBlank()) pending.add(notification);
            }
            if (!pending.isEmpty()) {
                data.notifications.put(owner.getUUID("playerUuid"), pending);
            }
        }
        for (Tag value : tag.getList("disabledNotifications", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) value;
            if (player.hasUUID("uuid")) {
                data.disabledNotifications.add(player.getUUID("uuid"));
            }
        }
        return data;
    }
}
