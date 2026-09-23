package dev.yassou.numismaticstreasury.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import dev.yassou.numismaticstreasury.server.history.HistoryService;
import dev.yassou.numismaticstreasury.server.auction.TreasuryData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

public final class TreasuryCommands {
    private TreasuryCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("pay")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer source = context.getSource().getPlayer();
                                    return SharedSuggestionProvider.suggest(
                                            source == null
                                                    ? context.getSource().getOnlinePlayerNames()
                                                    : BankService.recipientNames(
                                                            context.getSource().getServer(),
                                                            source.getUUID()
                                                    ),
                                            builder
                                    );
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> pay(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "player"),
                                                IntegerArgumentType.getInteger(context, "amount")
                                        ))))
        );
        event.getDispatcher().register(
                Commands.literal("numismatics_treasury")
                        .then(Commands.literal("history").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!TreasuryConfig.get().history.enabled) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.numismatics_treasury.history.disabled"
                                ).withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            TreasuryNetwork.openHistoryStats(
                                    player, "command", false, null, null);
                            return 1;
                        }))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                            TreasuryConfig.ReloadResult result = TreasuryConfig.load();
                            if (result.successful()) {
                                context.getSource().getServer().reloadResources(
                                        context.getSource().getServer()
                                                .getPackRepository()
                                                .getSelectedIds()
                                );
                                context.getSource().sendSuccess(
                                        () -> Component.translatable(
                                                "message.numismatics_treasury.config.result_path",
                                                result.message(),
                                                TreasuryConfig.path().toString()
                                        ),
                                        true
                                );
                                return 1;
                            }
                            context.getSource().sendFailure(result.message());
                            return 0;
                        }))
        );
    }

    private static int pay(ServerPlayer sender, String targetName, int amount) {
        if (!TreasuryConfig.get().modules.payCommand) {
            sender.sendSystemMessage(Component.translatable(
                            "message.numismatics_treasury.pay.disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        TreasuryConfig.Pay config = TreasuryConfig.get().pay;
        if (amount < config.minimum || amount > config.maximum) {
            sender.sendSystemMessage(Component.translatable(
                            "message.numismatics_treasury.amount_out_of_range")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Optional<GameProfile> target = ProfileResolver.resolve(sender.getServer(), targetName);
        if (target.isEmpty()) {
            sender.sendSystemMessage(Component.translatable(
                            "message.numismatics_treasury.player_unknown",
                            targetName)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        BankService.TransferResult result = BankService.transfer(
                sender,
                target.get().getId(),
                target.get().getName(),
                amount
        );
        sender.sendSystemMessage(result.message().copy().withStyle(
                result.successful() ? ChatFormatting.GREEN : ChatFormatting.RED
        ));
        if (result.successful()) {
            ServerPlayer recipient = sender.getServer().getPlayerList()
                    .getPlayer(target.get().getId());
            if (recipient != null && TreasuryData.get(sender.getServer())
                    .notificationsEnabled(recipient.getUUID())) {
                recipient.sendSystemMessage(Component.translatable(
                        "notification.numismatics_treasury.pay.received",
                        sender.getGameProfile().getName(),
                        amount
                ).withStyle(ChatFormatting.GREEN));
            }
            HistoryService.recordTransfer(
                    sender.getServer(),
                    sender.getUUID(),
                    sender.getGameProfile().getName(),
                    target.get().getId(),
                    target.get().getName(),
                    amount
            );
            return 1;
        }
        return 0;
    }
}
