package dev.yassou.numismaticstreasury.server;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static int countMatching(Player player, ItemStack template) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean removeMatching(Player player, ItemStack template, int amount) {
        if (amount <= 0 || countMatching(player, template) < amount) return false;
        int remaining = amount;
        Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.items.size() && remaining > 0; index++) {
            ItemStack stack = inventory.items.get(index);
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    public static boolean canFit(Player player, ItemStack template, int amount) {
        return capacity(player, template) >= amount;
    }

    public static int capacity(Player player, ItemStack template) {
        int capacity = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) capacity += template.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, template)) {
                capacity += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return capacity;
    }

    public static void give(Player player, ItemStack template, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(template.getMaxStackSize(), remaining);
            ItemStack part = template.copyWithCount(
                    batch
            );
            player.getInventory().add(part);
            remaining -= batch;
        }
    }

    public static List<ItemStack> giveClaims(Player player, List<ItemStack> claims) {
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack claim : claims) {
            ItemStack copy = claim.copy();
            if (!player.getInventory().add(copy) && !copy.isEmpty()) {
                remaining.add(copy);
            } else if (!copy.isEmpty()) {
                remaining.add(copy);
            }
        }
        return remaining;
    }
}
