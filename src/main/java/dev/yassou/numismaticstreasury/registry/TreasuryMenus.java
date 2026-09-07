package dev.yassou.numismaticstreasury.registry;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.menu.PlayerShopMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TreasuryMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NumismaticsTreasury.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<PlayerShopMenu>> PLAYER_SHOP =
            MENUS.register(
                    "player_shop",
                    () -> IMenuTypeExtension.create(PlayerShopMenu::new)
            );

    private TreasuryMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
