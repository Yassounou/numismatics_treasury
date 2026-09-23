package dev.yassou.numismaticstreasury.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class TreasuryButton extends Button {
    private static final ResourceLocation NORMAL = sprite("button/normal");
    private static final ResourceLocation HOVER = sprite("button/hover");
    private static final ResourceLocation PRESSED = sprite("button/pressed");
    private static final ResourceLocation DISABLED = sprite("button/disabled");
    private static final ResourceLocation GREEN_NORMAL = sprite("green_button/normal");
    private static final ResourceLocation GREEN_HOVER = sprite("green_button/hover");
    private static final ResourceLocation GREEN_PRESSED = sprite("green_button/pressed");
    private static final ResourceLocation GREEN_DISABLED = sprite("green_button/disabled");

    private final boolean green;
    private final boolean selected;
    private final OnPress requestedOnPress;
    private final Component confirmationMessage;
    private final ResourceLocation icon;
    private final float iconScale;
    private final int iconYOffset;
    private final boolean iconWithText;
    private boolean pressedByMouse;
    private long keyboardPressedUntil;
    private boolean awaitingConfirmation;
    private long confirmationExpiresAt;
    private Component messageBeforeConfirmation;

    private TreasuryButton(Builder builder) {
        super(builder);
        green = builder.green;
        selected = builder.selected;
        requestedOnPress = builder.requestedOnPress;
        confirmationMessage = builder.confirmationMessage;
        icon = builder.icon;
        iconScale = builder.iconScale;
        iconYOffset = builder.iconYOffset;
        iconWithText = builder.iconWithText;
        if (icon != null) setTooltip(Tooltip.create(getMessage()));
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void onPress() {
        if (confirmationMessage == null) {
            requestedOnPress.onPress(this);
            return;
        }
        long now = Util.getMillis();
        if (!awaitingConfirmation || now >= confirmationExpiresAt) {
            awaitingConfirmation = true;
            confirmationExpiresAt = now + 3_000L;
            messageBeforeConfirmation = getMessage();
            setMessage(confirmationMessage);
            return;
        }
        awaitingConfirmation = false;
        if (messageBeforeConfirmation != null) setMessage(messageBeforeConfirmation);
        requestedOnPress.onPress(this);
    }

    public void resetConfirmation(Component message) {
        awaitingConfirmation = false;
        confirmationExpiresAt = 0L;
        messageBeforeConfirmation = null;
        setMessage(message);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        graphics.blitSprite(currentSprite(minecraft), getX(), getY(), getWidth(), getHeight());
        int iconCenterX = getX() + getWidth() / 2;
        int textX = 0;
        if (icon != null && iconWithText) {
            int iconWidth = Math.round(16.0F * iconScale);
            int textWidth = minecraft.font.width(getMessage());
            int contentWidth = iconWidth + 4 + textWidth;
            int start = getX() + (getWidth() - contentWidth) / 2;
            iconCenterX = start + iconWidth / 2;
            textX = start + iconWidth + 4;
        }
        if (icon != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(
                    iconCenterX,
                    getY() + getHeight() / 2.0F + iconYOffset,
                    0.0F
            );
            graphics.pose().scale(iconScale, iconScale, 1.0F);
            graphics.blit(
                    icon,
                    -8,
                    -8,
                    0,
                    0,
                    16,
                    16,
                    16,
                    16
            );
            graphics.pose().popPose();
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        int textColor = getFGColor() | (Mth.ceil(alpha * 255.0F) << 24);
        if (icon == null) {
            renderString(graphics, minecraft.font, textColor);
        } else if (iconWithText) {
            graphics.drawString(
                    minecraft.font,
                    getMessage(),
                    textX,
                    getY() + (getHeight() - minecraft.font.lineHeight) / 2,
                    textColor,
                    false
            );
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        pressedByMouse = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        pressedByMouse = false;
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled) keyboardPressedUntil = Util.getMillis() + 100L;
        return handled;
    }

    private ResourceLocation currentSprite(Minecraft minecraft) {
        if (awaitingConfirmation && Util.getMillis() >= confirmationExpiresAt) {
            awaitingConfirmation = false;
            if (messageBeforeConfirmation != null) setMessage(messageBeforeConfirmation);
            messageBeforeConfirmation = null;
        }
        if (pressedByMouse) {
            if (minecraft.mouseHandler.isLeftPressed()) return green ? GREEN_PRESSED : PRESSED;
            pressedByMouse = false;
        }
        if (Util.getMillis() < keyboardPressedUntil || selected || awaitingConfirmation) {
            return green ? GREEN_PRESSED : PRESSED;
        }
        if (!active) return green ? GREEN_DISABLED : DISABLED;
        if (isHoveredOrFocused()) return green ? GREEN_HOVER : HOVER;
        return green ? GREEN_NORMAL : NORMAL;
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(NumismaticsTreasury.MOD_ID, name);
    }

    public static final class Builder extends Button.Builder {
        private boolean green;
        private boolean selected;
        private final OnPress requestedOnPress;
        private Component confirmationMessage;
        private ResourceLocation icon;
        private float iconScale = 1.0F;
        private int iconYOffset;
        private boolean iconWithText;

        private Builder(Component message, OnPress onPress) {
            super(message, ignored -> { });
            requestedOnPress = onPress;
        }

        public Builder green() { green = true; return this; }
        public Builder selected(boolean value) { selected = value; return this; }
        public Builder icon(ResourceLocation value) { icon = value; return this; }
        public Builder iconScale(float value) {
            iconScale = Mth.clamp(value, 0.25F, 2.0F);
            return this;
        }
        public Builder iconYOffset(int value) { iconYOffset = value; return this; }
        public Builder iconWithText() { iconWithText = true; return this; }
        public Builder confirmation(Component message) {
            confirmationMessage = message;
            return this;
        }
        @Override public Builder bounds(int x, int y, int width, int height) {
            super.bounds(x, y, width, height); return this;
        }
        @Override public Builder size(int width, int height) {
            super.size(width, height); return this;
        }
        @Override public Builder pos(int x, int y) {
            super.pos(x, y); return this;
        }
        @Override public TreasuryButton build() { return new TreasuryButton(this); }
    }
}
