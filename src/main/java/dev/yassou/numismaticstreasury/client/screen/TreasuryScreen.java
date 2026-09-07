package dev.yassou.numismaticstreasury.client.screen;

import dev.yassou.numismaticstreasury.client.gui.TreasuryFrame;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class TreasuryScreen extends Screen {
    protected static final int PANEL = 0xEE171717;
    protected static final int PANEL_ALT = 0xEE242424;
    protected static final int SEPARATOR = 0xFF4B4B4B;
    protected static final int MUTED = 0xFFAAAAAA;
    protected static final int TEXT = 0xFFF2F2F2;
    protected static final int ACCENT = 0xFFE0B04B;
    protected static final int SUCCESS = 0xFF67C56B;
    protected static final int ERROR = 0xFFE46F61;

    protected int panelLeft;
    protected int panelTop;
    protected int panelWidth;
    protected int panelHeight;

    protected TreasuryScreen(Component title) {
        super(title);
    }

    protected final void layout(int maxWidth, int maxHeight) {
        panelWidth = Math.max(1, Math.min(maxWidth, width - 16));
        panelHeight = Math.max(1, Math.min(maxHeight, height - 16));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
    }

    protected final void renderPanel(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL);
        TreasuryFrame.renderAround(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.drawString(font, title, panelLeft + 12, panelTop + 10, ACCENT, false);
        graphics.fill(
                panelLeft + 1,
                panelTop + 29,
                panelLeft + panelWidth - 1,
                panelTop + 30,
                SEPARATOR
        );
    }

    protected static void outline(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        MoneyDisplay.outline(graphics, x, y, width, height, color);
    }

    @Override
    public final void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // Each Treasury screen renders the same flat veil; no vanilla blur.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
