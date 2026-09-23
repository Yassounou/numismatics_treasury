package dev.yassou.numismaticstreasury.client;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.client.hud.ShopHoverOverlay;
import dev.yassou.numismaticstreasury.client.renderer.ServerShopRenderer;
import dev.yassou.numismaticstreasury.client.screen.AuctionHouseScreen;
import dev.yassou.numismaticstreasury.client.screen.BankTellerScreen;
import dev.yassou.numismaticstreasury.client.screen.ServerShopScreen;
import dev.yassou.numismaticstreasury.client.screen.PlayerShopScreen;
import dev.yassou.numismaticstreasury.client.screen.PlayerShopManagementScreen;
import dev.yassou.numismaticstreasury.client.screen.PlayerShopAssociatesScreen;
import dev.yassou.numismaticstreasury.client.screen.HistoryStatsScreen;
import dev.yassou.numismaticstreasury.network.ClientPayloadBridge;
import dev.yassou.numismaticstreasury.registry.TreasuryBlockEntities;
import dev.yassou.numismaticstreasury.registry.TreasuryMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = NumismaticsTreasury.MOD_ID, dist = Dist.CLIENT)
public final class NumismaticsTreasuryClient {
    public NumismaticsTreasuryClient(IEventBus modEventBus) {
        ClientPayloadBridge.install(payload -> {
            Minecraft minecraft = Minecraft.getInstance();
            switch (payload.screen()) {
                case "bank" -> minecraft.setScreen(new BankTellerScreen(payload.json()));
                case "shop" -> minecraft.setScreen(new ServerShopScreen(payload.json(), false));
                case "shop_admin" -> minecraft.setScreen(new ServerShopScreen(payload.json(), true));
                case "player_shop" -> minecraft.setScreen(new PlayerShopScreen(payload.json(), false));
                case "player_shop_associates" -> minecraft.setScreen(
                        new PlayerShopAssociatesScreen(payload.json()));
                case "history_stats" -> minecraft.setScreen(
                        new HistoryStatsScreen(payload.json()));
                case "auction" -> {
                    if (minecraft.screen instanceof AuctionHouseScreen screen) {
                        screen.refresh(payload.json());
                    } else {
                        minecraft.setScreen(new AuctionHouseScreen(payload.json()));
                    }
                }
                case "auction_update" -> {
                    if (minecraft.screen instanceof AuctionHouseScreen screen) {
                        screen.refresh(payload.json());
                    }
                }
                default -> NumismaticsTreasury.LOGGER.warn(
                        "Unknown Treasury screen {}", payload.screen());
            }
        });
        modEventBus.addListener(NumismaticsTreasuryClient::registerRenderers);
        modEventBus.addListener(NumismaticsTreasuryClient::registerMenuScreens);
        NeoForge.EVENT_BUS.addListener(ShopHoverOverlay::render);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                TreasuryBlockEntities.SERVER_SHOP.get(),
                ServerShopRenderer::new
        );
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(
                TreasuryMenus.PLAYER_SHOP.get(),
                PlayerShopManagementScreen::new
        );
    }
}
