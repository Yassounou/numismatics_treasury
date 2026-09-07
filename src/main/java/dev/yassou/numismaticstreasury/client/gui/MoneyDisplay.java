package dev.yassou.numismaticstreasury.client.gui;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class MoneyDisplay {
    public static final int GOLD = 0xFFFFAA00;
    private static final int BACKGROUND = 0xF016120B;
    private static final ResourceLocation COINS = ResourceLocation.fromNamespaceAndPath(
            NumismaticsTreasury.MOD_ID,
            "textures/gui/coins.png"
    );

    private MoneyDisplay() {
    }

    public static String exact(long amount) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, amount)).replace(',', ' ');
    }

    public static int badgeWidth(Font font, long amount) {
        return 12 + 14 + 5
                + font.width(Component.translatable("screen.numismatics_treasury.balance"))
                + 4 + font.width(exact(amount)) + 12;
    }

    public static void renderBadge(GuiGraphics graphics, Font font, int x, int y, long amount) {
        int width = badgeWidth(font, amount);
        int height = 18;
        graphics.fill(x + 2, y, x + width - 2, y + height, BACKGROUND);
        graphics.fill(x, y + 2, x + width, y + height - 2, BACKGROUND);
        outline(graphics, x, y, width, height, GOLD);
        renderIcon(graphics, x + 8, y + 5);
        Component label = Component.translatable("screen.numismatics_treasury.balance");
        graphics.drawString(font, label, x + 27, y + 5, GOLD, false);
        graphics.drawString(
                font,
                exact(amount),
                x + 31 + font.width(label),
                y + 5,
                GOLD,
                false
        );
    }

    public static void renderIcon(GuiGraphics graphics, int x, int y) {
        graphics.blit(COINS, x, y, 0, 0, 14, 9, 14, 9);
    }

    public static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + 1, y + height - 2, color);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
    }
}
