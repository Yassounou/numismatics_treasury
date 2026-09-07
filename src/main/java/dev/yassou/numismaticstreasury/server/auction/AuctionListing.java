package dev.yassou.numismaticstreasury.server.auction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class AuctionListing {
    private final UUID id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final ListingType type;
    private final int price;
    private final long createdAt;
    private final long expiresAt;
    private int currentBid;
    private UUID leadingBidderUuid;
    private String leadingBidderName;
    private long revision;

    public AuctionListing(
            UUID id,
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            ListingType type,
            int price,
            long createdAt,
            long expiresAt
    ) {
        this(id, sellerUuid, sellerName, item, type, price, createdAt, expiresAt,
                0, null, "", 1L);
    }

    private AuctionListing(
            UUID id,
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            ListingType type,
            int price,
            long createdAt,
            long expiresAt,
            int currentBid,
            UUID leadingBidderUuid,
            String leadingBidderName,
            long revision
    ) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item.copy();
        this.type = type;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.currentBid = currentBid;
        this.leadingBidderUuid = leadingBidderUuid;
        this.leadingBidderName = leadingBidderName == null ? "" : leadingBidderName;
        this.revision = Math.max(1L, revision);
    }

    public UUID id() { return id; }
    public UUID sellerUuid() { return sellerUuid; }
    public String sellerName() { return sellerName; }
    public ItemStack item() { return item.copy(); }
    public ListingType type() { return type; }
    public int price() { return price; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public int currentBid() { return currentBid; }
    public UUID leadingBidderUuid() { return leadingBidderUuid; }
    public String leadingBidderName() { return leadingBidderName; }
    public long revision() { return revision; }

    public int minimumBid() {
        if (type != ListingType.AUCTION) return price;
        if (currentBid <= 0) return price;
        return currentBid == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentBid + 1;
    }

    public void recordBid(UUID bidderUuid, String bidderName, int amount) {
        currentBid = amount;
        leadingBidderUuid = bidderUuid;
        leadingBidderName = bidderName;
        revision++;
    }

    public boolean expired(long now) {
        return now >= expiresAt;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("sellerUuid", sellerUuid);
        tag.putString("sellerName", sellerName);
        tag.put("item", item.saveOptional(registries));
        tag.putString("type", type.name());
        tag.putInt("price", price);
        tag.putLong("createdAt", createdAt);
        tag.putLong("expiresAt", expiresAt);
        tag.putInt("currentBid", currentBid);
        if (leadingBidderUuid != null) {
            tag.putUUID("leadingBidderUuid", leadingBidderUuid);
            tag.putString("leadingBidderName", leadingBidderName);
        }
        tag.putLong("revision", revision);
        return tag;
    }

    public static AuctionListing load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        return new AuctionListing(
                tag.getUUID("id"),
                tag.getUUID("sellerUuid"),
                tag.getString("sellerName"),
                ItemStack.parseOptional(registries, tag.getCompound("item")),
                ListingType.parse(tag.getString("type")),
                Math.max(1, tag.getInt("price")),
                tag.getLong("createdAt"),
                tag.getLong("expiresAt"),
                Math.max(0, tag.getInt("currentBid")),
                tag.hasUUID("leadingBidderUuid")
                        ? tag.getUUID("leadingBidderUuid") : null,
                tag.getString("leadingBidderName"),
                tag.getLong("revision")
        );
    }
}
