package dev.yassou.numismaticstreasury.server;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** The only boundary between Treasury and Numismatics bank accounts. */
public final class BankService {
    private BankService() {
    }

    public static BankAccount account(ServerPlayer player) {
        return Numismatics.BANK.getOrCreateAccount(
                player.getUUID(),
                BankAccount.Type.PLAYER
        );
    }

    public static BankAccount existingAccount(UUID playerUuid) {
        return Numismatics.BANK.getAccount(playerUuid);
    }

    public static int balance(ServerPlayer player) {
        return account(player).getBalance();
    }

    public static int balance(UUID playerUuid) {
        BankAccount account = existingAccount(playerUuid);
        return account == null ? -1 : account.getBalance();
    }

    public static List<String> recipientNames(
            MinecraftServer server,
            UUID senderUuid
    ) {
        return Numismatics.BANK.accounts.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(senderUuid))
                .filter(entry -> entry.getValue().type == BankAccount.Type.PLAYER)
                .map(entry -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());
                    if (online != null) return online.getGameProfile().getName();
                    return server.getProfileCache().get(entry.getKey())
                            .map(profile -> profile.getName())
                            .orElse("");
                })
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static boolean debit(ServerPlayer player, int amount) {
        return amount > 0 && account(player).deduct(amount, false);
    }

    public static boolean debit(UUID playerUuid, int amount) {
        BankAccount account = existingAccount(playerUuid);
        return account != null && amount > 0 && account.deduct(amount, false);
    }

    public static void credit(ServerPlayer player, int amount) {
        credit(player.getUUID(), amount);
    }

    public static boolean credit(UUID playerUuid, int amount) {
        BankAccount account = existingAccount(playerUuid);
        if (account == null || amount <= 0) return false;
        account.deposit(amount);
        return true;
    }

    public static TransferResult transfer(
            ServerPlayer sender,
            UUID recipientUuid,
            String recipientName,
            int amount
    ) {
        if (recipientUuid.equals(sender.getUUID())) {
            return TransferResult.failure("message.numismatics_treasury.pay.self");
        }
        BankAccount recipient = existingAccount(recipientUuid);
        if (recipient == null) {
            return TransferResult.failure(
                    "message.numismatics_treasury.pay.no_account",
                    recipientName
            );
        }
        if (!debit(sender, amount)) {
            return TransferResult.failure(
                    "message.numismatics_treasury.balance_insufficient",
                    balance(sender)
            );
        }
        try {
            recipient.deposit(amount);
        } catch (RuntimeException exception) {
            account(sender).deposit(amount);
            throw exception;
        }
        return TransferResult.success(
                "message.numismatics_treasury.pay.sent",
                amount,
                recipientName
        );
    }

    public record TransferResult(boolean successful, Component message) {
        public static TransferResult success(String key, Object... arguments) {
            return new TransferResult(true, Component.translatable(key, arguments));
        }

        public static TransferResult failure(String key, Object... arguments) {
            return new TransferResult(false, Component.translatable(key, arguments));
        }
    }
}
