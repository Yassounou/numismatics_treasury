package dev.yassou.numismaticstreasury.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Compact player inventory used to choose an item without a vanilla container screen. */
public final class InventoryPicker {
    public static final int WIDTH = 9 * 18;
    public static final int HEIGHT = 4 * 18;

    private final int x;
    private final int y;
    private int selectedSlot;

    public InventoryPicker(int x, int y, int selectedSlot) {
        this.x = x;
        this.y = y;
        this.selectedSlot = selectedSlot;
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public void clearSelection() {
        selectedSlot = -1;
    }

    public ItemStack selectedStack(Player player) {
        if (player == null || selectedSlot < 0
                || selectedSlot >= player.getInventory().items.size()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().items.get(selectedSlot);
    }

    public boolean mouseClicked(Player player, double mouseX, double mouseY, int button) {
        if (button != 0 || mouseX < x || mouseX >= x + WIDTH
                || mouseY < y || mouseY >= y + HEIGHT) {
            return false;
        }
        int column = (int) (mouseX - x) / 18;
        int row = (int) (mouseY - y) / 18;
        int slot = inventorySlot(row, column);
        ItemStack stack = player.getInventory().items.get(slot);
        selectedSlot = stack.isEmpty() ? -1 : slot;
        return true;
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            Player player,
            int mouseX,
            int mouseY
    ) {
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = inventorySlot(row, column);
                int slotX = x + column * 18;
                int slotY = y + row * 18;
                boolean selected = slot == selectedSlot;
                boolean hovered = mouseX >= slotX && mouseX < slotX + 18
                        && mouseY >= slotY && mouseY < slotY + 18;
                int border = selected ? 0xFFE0B04B : hovered ? 0xFF888888 : 0xFF4B4B4B;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, border);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xEE181818);
                ItemStack stack = player.getInventory().items.get(slot);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, slotX + 1, slotY + 1);
                    graphics.renderItemDecorations(font, stack, slotX + 1, slotY + 1);
                }
            }
        }
    }

    public ItemStack hoveredStack(Player player, int mouseX, int mouseY) {
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + HEIGHT) {
            return ItemStack.EMPTY;
        }
        int column = (mouseX - x) / 18;
        int row = (mouseY - y) / 18;
        return player.getInventory().items.get(inventorySlot(row, column));
    }

    private static int inventorySlot(int displayRow, int column) {
        return displayRow < 3 ? 9 + displayRow * 9 + column : column;
    }
}
