package dev.yassou.numismaticstreasury.registry;

import com.mojang.serialization.MapCodec;
import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.config.ModuleEnabledCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class TreasuryConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.CONDITION_CODECS,
                    NumismaticsTreasury.MOD_ID
            );
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ModuleEnabledCondition>>
            MODULE_ENABLED = CONDITIONS.register("module_enabled", () -> ModuleEnabledCondition.CODEC);

    private TreasuryConditions() {
    }

    public static void register(IEventBus bus) {
        CONDITIONS.register(bus);
    }
}
