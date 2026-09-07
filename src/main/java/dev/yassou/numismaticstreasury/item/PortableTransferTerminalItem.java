package dev.yassou.numismaticstreasury.item;

import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PortableTransferTerminalItem extends Item {
    public PortableTransferTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (!TreasuryConfig.get().modules.portableTransferTerminal) {
                player.sendSystemMessage(Component.translatable(
                        "message.numismatics_treasury.portable_transfer_terminal.disabled"));
            } else if (player instanceof ServerPlayer serverPlayer) {
                TreasuryNetwork.openPortableBankTeller(serverPlayer);
                player.getCooldowns().addCooldown(this, 10);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
