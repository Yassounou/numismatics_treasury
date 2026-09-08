package dev.yassou.numismaticstreasury.registry;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.block.AuctionHouseBlock;
import dev.yassou.numismaticstreasury.block.BankTellerBlock;
import dev.yassou.numismaticstreasury.block.PlayerShopBlock;
import dev.yassou.numismaticstreasury.block.ServerShopBlock;
import dev.yassou.numismaticstreasury.item.PortableTransferTerminalItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TreasuryContent {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(NumismaticsTreasury.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(NumismaticsTreasury.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NumismaticsTreasury.MOD_ID);

    public static final DeferredBlock<ServerShopBlock> NUMIS_SHOP = BLOCKS.registerBlock(
            "numis_shop",
            ServerShopBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .noLootTable()
    );
    public static final DeferredBlock<PlayerShopBlock> PLAYER_SHOP = BLOCKS.registerBlock(
            "player_shop",
            PlayerShopBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.5F, 3_600_000.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
    );
    public static final DeferredBlock<AuctionHouseBlock> AUCTION_HOUSE = BLOCKS.registerBlock(
            "auction_house",
            AuctionHouseBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
    );
    public static final DeferredBlock<BankTellerBlock> BANK_TELLER = BLOCKS.registerBlock(
            "bank_teller",
            BankTellerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );

    public static final DeferredItem<BlockItem> NUMIS_SHOP_ITEM =
            ITEMS.registerSimpleBlockItem(NUMIS_SHOP);
    public static final DeferredItem<BlockItem> PLAYER_SHOP_ITEM =
            ITEMS.registerSimpleBlockItem(PLAYER_SHOP);
    public static final DeferredItem<BlockItem> AUCTION_HOUSE_ITEM =
            ITEMS.registerSimpleBlockItem(AUCTION_HOUSE);
    public static final DeferredItem<BlockItem> BANK_TELLER_ITEM =
            ITEMS.registerSimpleBlockItem(BANK_TELLER);
    public static final DeferredItem<Item> TREASURY_ICON = ITEMS.registerSimpleItem(
            "treasury_icon",
            new Item.Properties()
    );
    public static final DeferredItem<PortableTransferTerminalItem> PORTABLE_TRANSFER_TERMINAL =
            ITEMS.register(
                    "portable_transfer_terminal",
                    () -> new PortableTransferTerminalItem(new Item.Properties().stacksTo(1))
            );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "treasury",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.numismatics_treasury"))
                    .icon(() -> new ItemStack(BANK_TELLER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(NUMIS_SHOP_ITEM.get());
                        output.accept(PLAYER_SHOP_ITEM.get());
                        output.accept(AUCTION_HOUSE_ITEM.get());
                        output.accept(BANK_TELLER_ITEM.get());
                        output.accept(PORTABLE_TRANSFER_TERMINAL.get());
                    })
                    .build()
    );

    private TreasuryContent() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
