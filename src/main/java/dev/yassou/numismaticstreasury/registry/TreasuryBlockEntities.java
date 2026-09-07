package dev.yassou.numismaticstreasury.registry;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TreasuryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NumismaticsTreasury.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServerShopBlockEntity>>
            SERVER_SHOP = TYPES.register(
                    "numis_shop",
                    () -> BlockEntityType.Builder.of(
                            ServerShopBlockEntity::new,
                            TreasuryContent.NUMIS_SHOP.get(),
                            TreasuryContent.PLAYER_SHOP.get()
                    ).build(null)
            );

    private TreasuryBlockEntities() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        blockEntity instanceof ServerShopBlockEntity shop
                                ? shop.automatedInput() : null,
                TreasuryContent.PLAYER_SHOP.get()
        );
    }
}
