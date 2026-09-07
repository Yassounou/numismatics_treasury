package dev.yassou.numismaticstreasury.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

public record ModuleEnabledCondition(String module) implements ICondition {
    public static final MapCodec<ModuleEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("module").forGetter(ModuleEnabledCondition::module)
            ).apply(instance, ModuleEnabledCondition::new)
    );

    @Override
    public boolean test(IContext context) {
        TreasuryConfig.Modules modules = TreasuryConfig.get().modules;
        return switch (module) {
            case "server_shop" -> modules.serverShop;
            case "player_shop" -> modules.playerShop;
            case "auction_house" -> modules.auctionHouse;
            case "bank_teller" -> modules.bankTeller;
            case "portable_transfer_terminal" -> modules.portableTransferTerminal;
            default -> false;
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
