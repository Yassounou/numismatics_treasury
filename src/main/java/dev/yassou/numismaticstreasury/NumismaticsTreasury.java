package dev.yassou.numismaticstreasury;

import com.mojang.logging.LogUtils;
import dev.yassou.numismaticstreasury.block.ServerShopBlock;
import dev.yassou.numismaticstreasury.block.PlayerShopBlock;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import dev.yassou.numismaticstreasury.registry.TreasuryBlockEntities;
import dev.yassou.numismaticstreasury.registry.TreasuryContent;
import dev.yassou.numismaticstreasury.registry.TreasuryMenus;
import dev.yassou.numismaticstreasury.registry.TreasuryConditions;
import dev.yassou.numismaticstreasury.server.TreasuryCommands;
import dev.yassou.numismaticstreasury.server.auction.AuctionService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(NumismaticsTreasury.MOD_ID)
public final class NumismaticsTreasury {
    public static final String MOD_ID = "numismatics_treasury";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NumismaticsTreasury(IEventBus modEventBus) {
        TreasuryContent.register(modEventBus);
        TreasuryBlockEntities.register(modEventBus);
        TreasuryMenus.register(modEventBus);
        TreasuryConditions.register(modEventBus);
        modEventBus.addListener(TreasuryNetwork::register);
        modEventBus.addListener(TreasuryBlockEntities::registerCapabilities);

        TreasuryConfig.load();
        NeoForge.EVENT_BUS.addListener(TreasuryCommands::register);
        NeoForge.EVENT_BUS.addListener(AuctionService::onServerTick);
        NeoForge.EVENT_BUS.addListener(AuctionService::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(ServerShopBlock::forceAdminInteraction);
        NeoForge.EVENT_BUS.addListener(PlayerShopBlock::forceManagementInteraction);
        NeoForge.EVENT_BUS.addListener(PlayerShopBlock::protectStockAndOwnership);
        LOGGER.info("Numismatics Treasury loaded.");
    }
}
