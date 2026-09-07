package dev.yassou.numismaticstreasury.block.entity;

import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.registry.TreasuryBlockEntities;
import dev.yassou.numismaticstreasury.registry.TreasuryContent;
import dev.yassou.numismaticstreasury.menu.PlayerShopMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ServerShopBlockEntity extends BlockEntity implements MenuProvider {
    private ShopMode mode = ShopMode.SELL_TO_PLAYER;
    private ItemStack template = ItemStack.EMPTY;
    private int price;
    private UUID ownerUuid;
    private String ownerName = "";
    private long stock;
    private final IItemHandler automatedInput = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            // This is a one-way virtual input, not the long stock itself. Keeping
            // the exposed slot empty makes automation probe insertItem(), where
            // the configured item and the remaining long capacity are validated.
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!canAutomate(slot, stack)) return stack;
            long available = Long.MAX_VALUE - stock;
            if (available <= 0L) return stack;
            int accepted = (int) Math.min((long) stack.getCount(), available);
            if (accepted <= 0) return stack;
            if (!simulate) {
                if (level == null || level.isClientSide || !addStock(accepted)) {
                    return stack;
                }
            }
            return accepted == stack.getCount()
                    ? ItemStack.EMPTY
                    : stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? Integer.MAX_VALUE : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canAutomate(slot, stack);
        }
    };

    public ServerShopBlockEntity(BlockPos pos, BlockState state) {
        super(TreasuryBlockEntities.SERVER_SHOP.get(), pos, state);
    }

    public ShopMode mode() { return mode; }
    public ItemStack template() { return template.copy(); }
    public int price() { return price; }
    @Nullable public UUID ownerUuid() { return ownerUuid; }
    public String ownerName() { return ownerName; }
    public long stock() { return stock; }
    public boolean configured() { return !template.isEmpty() && price > 0; }

    public IItemHandler automatedInput() {
        return automatedInput;
    }

    private boolean canAutomate(int slot, ItemStack stack) {
        return slot == 0
                && !stack.isEmpty()
                && getBlockState().is(TreasuryContent.PLAYER_SHOP.get())
                && TreasuryConfig.get().modules.playerShop
                && configured()
                && ItemStack.isSameItemSameComponents(stack, template);
    }

    public boolean canManage(Player player) {
        return player.hasPermissions(2)
                || ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null ? "" : ownerName;
        sync();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(
                "screen.numismatics_treasury.player_shop.manage_title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(
            int containerId,
            Inventory inventory,
            Player player
    ) {
        return new PlayerShopMenu(containerId, inventory, this);
    }

    @Override
    public void writeClientSideData(
            AbstractContainerMenu menu,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(worldPosition);
        buffer.writeUtf(ownerName, 64);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, template);
        buffer.writeVarInt(price);
        buffer.writeVarLong(stock);
    }

    public void configure(ShopMode mode, ItemStack template, int price) {
        this.mode = mode;
        this.template = template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
        this.price = Math.max(0, price);
        sync();
    }

    public void configurePlayer(ItemStack template, int price) {
        this.mode = ShopMode.SELL_TO_PLAYER;
        this.template = template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
        this.price = Math.max(0, price);
        sync();
    }

    public boolean addStock(long amount) {
        if (amount <= 0L) return false;
        try {
            stock = Math.addExact(stock, amount);
        } catch (ArithmeticException exception) {
            return false;
        }
        sync();
        return true;
    }

    public boolean removeStock(long amount) {
        if (amount <= 0L || amount > stock) return false;
        stock -= amount;
        sync();
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = ShopMode.parse(tag.getString("mode"));
        template = ItemStack.parseOptional(registries, tag.getCompound("item"));
        price = Math.max(0, tag.getInt("price"));
        ownerUuid = tag.hasUUID("ownerUuid") ? tag.getUUID("ownerUuid") : null;
        ownerName = tag.getString("ownerName");
        stock = Math.max(0L, tag.getLong("stock"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("mode", mode.name());
        tag.put("item", template.saveOptional(registries));
        tag.putInt("price", price);
        if (ownerUuid != null) tag.putUUID("ownerUuid", ownerUuid);
        tag.putString("ownerName", ownerName);
        tag.putLong("stock", stock);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
