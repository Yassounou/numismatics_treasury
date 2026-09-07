package dev.yassou.numismaticstreasury.menu;

import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.registry.TreasuryContent;
import dev.yassou.numismaticstreasury.registry.TreasuryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class PlayerShopMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    private static final int PLAYER_SLOT_START = 1;
    private static final int PLAYER_SLOT_END = 37;

    private final SimpleContainer input = new SimpleContainer(1);
    private final ContainerData data;
    private final BlockPos pos;
    private final String ownerName;
    private final ItemStack initialShopItem;
    @Nullable private final ServerShopBlockEntity shop;
    private boolean handlingInput;

    public PlayerShopMenu(
            int containerId,
            Inventory playerInventory,
            RegistryFriendlyByteBuf buffer
    ) {
        this(
                containerId,
                playerInventory,
                null,
                new SimpleContainerData(3),
                buffer.readBlockPos(),
                buffer.readUtf(64),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readVarInt(),
                buffer.readVarLong()
        );
    }

    public PlayerShopMenu(
            int containerId,
            Inventory playerInventory,
            ServerShopBlockEntity shop
    ) {
        this(
                containerId,
                playerInventory,
                shop,
                serverData(shop),
                shop.getBlockPos(),
                shop.ownerName(),
                shop.template(),
                shop.price(),
                shop.stock()
        );
    }

    private PlayerShopMenu(
            int containerId,
            Inventory playerInventory,
            @Nullable ServerShopBlockEntity shop,
            ContainerData data,
            BlockPos pos,
            String ownerName,
            ItemStack initialShopItem,
            int initialPrice,
            long initialStock
    ) {
        super(TreasuryMenus.PLAYER_SHOP.get(), containerId);
        this.shop = shop;
        this.data = data;
        this.pos = pos;
        this.ownerName = ownerName;
        this.initialShopItem = initialShopItem.copyWithCount(1);
        if (shop == null) {
            data.set(0, initialPrice);
            data.set(1, (int) initialStock);
            data.set(2, (int) (initialStock >>> 32));
        }

        addSlot(new Slot(input, 0, 9, 38) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PlayerShopMenu.this.acceptsInput(stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        8 + column * 18,
                        147 + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    8 + column * 18,
                    205
            ));
        }
        addDataSlots(data);
        input.addListener(this::inputChanged);
    }

    private static ContainerData serverData(ServerShopBlockEntity shop) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> shop.price();
                    case 1 -> (int) shop.stock();
                    case 2 -> (int) (shop.stock() >>> 32);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public BlockPos blockPos() {
        return pos;
    }

    public String ownerName() {
        return ownerName;
    }

    public int price() {
        return Math.max(0, data.get(0));
    }

    public long stock() {
        return Integer.toUnsignedLong(data.get(1)) | (long) data.get(2) << 32;
    }

    public ItemStack shopItem() {
        return shop == null ? initialShopItem.copy() : shop.template();
    }

    public ItemStack inputStack() {
        return input.getItem(0).copy();
    }

    public boolean matches(ServerShopBlockEntity expected) {
        return shop == expected && pos.equals(expected.getBlockPos());
    }

    public int depositPendingInput() {
        if (shop == null) return 0;
        ItemStack pending = input.getItem(0);
        if (pending.isEmpty()
                || !ItemStack.isSameItemSameComponents(pending, shop.template())) {
            return 0;
        }
        int amount = pending.getCount();
        if (!shop.addStock(amount)) return -1;
        input.removeItemNoUpdate(0);
        broadcastChanges();
        return amount;
    }

    @Override
    public boolean stillValid(Player player) {
        if (shop == null) return true;
        return TreasuryConfig.get().modules.playerShop
                && shop.canManage(player)
                && shop.getBlockState().is(TreasuryContent.PLAYER_SHOP.get())
                && Container.stillValidBlockEntity(shop, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == INPUT_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (canDepositDirectly(stack)) {
            int amount = stack.getCount();
            if (shop == null || !shop.addStock(amount)) return ItemStack.EMPTY;
            stack.shrink(amount);
        } else if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, input);
    }

    private boolean acceptsInput(ItemStack stack) {
        ItemStack configured = shopItem();
        return configured.isEmpty()
                || stock() == 0L
                || ItemStack.isSameItemSameComponents(stack, configured);
    }

    private boolean canDepositDirectly(ItemStack stack) {
        if (shop == null || !shop.configured()) return false;
        return ItemStack.isSameItemSameComponents(stack, shop.template());
    }

    private void inputChanged(Container ignored) {
        if (handlingInput || shop == null) return;
        ItemStack stack = input.getItem(0);
        if (stack.isEmpty() || !canDepositDirectly(stack)) return;
        handlingInput = true;
        int amount = stack.getCount();
        if (shop.addStock(amount)) input.removeItemNoUpdate(0);
        handlingInput = false;
        broadcastChanges();
    }
}
