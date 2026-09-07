package dev.yassou.numismaticstreasury.client.gui;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class TreasuryFrame {
    private static final ResourceLocation SPRITE = ResourceLocation.fromNamespaceAndPath(
            NumismaticsTreasury.MOD_ID,
            "cadre"
    );

    private TreasuryFrame() {
    }

    public static void renderAround(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(SPRITE, x - 5, y - 5, width + 10, height + 10);
    }
}
