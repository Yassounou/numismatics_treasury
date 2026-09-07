package dev.yassou.numismaticstreasury.client.hud;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.block.entity.ShopMode;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class ShopHoverOverlay {
    private static final ResourceLocation BUY_ICON = ResourceLocation.fromNamespaceAndPath(
            NumismaticsTreasury.MOD_ID,
            "textures/gui/shop_buy.png"
    );
    private static final ResourceLocation SELL_ICON = ResourceLocation.fromNamespaceAndPath(
            NumismaticsTreasury.MOD_ID,
            "textures/gui/shop_sell.png"
    );

    private static final long FADE_IN_MILLIS = 360L;
    private static final long FADE_OUT_MILLIS = 140L;
    private static final int BACKGROUND_TOP = 0xF0100010;
    private static final int BACKGROUND_BOTTOM = 0xF0160818;
    private static final int BUY_COLOR = 0xFF55FF55;
    private static final int BUY_BORDER_BOTTOM = 0xFF167A28;
    private static final int SELL_COLOR = 0xFFFF5555;
    private static final int SELL_BORDER_BOTTOM = 0xFF7A2020;
    private static final int PRICE_COLOR = 0xFFFF55FF;

    private static BlockPos displayedPos;
    private static long shownSince;
    private static long fadingSince = -1L;

    private ShopHoverOverlay() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            clear();
            return;
        }

        boolean chatOpen = minecraft.screen instanceof ChatScreen;
        if (minecraft.screen != null && !chatOpen) {
            clear();
            return;
        }

        long now = Util.getMillis();
        ServerShopBlockEntity shop = targetedShop(minecraft);
        if (shop != null) {
            BlockPos targetPos = shop.getBlockPos();
            if (!targetPos.equals(displayedPos)) {
                displayedPos = targetPos.immutable();
                shownSince = now;
            }
            fadingSince = -1L;
        } else if (displayedPos != null) {
            shop = cachedShop(minecraft, displayedPos);
            if (shop == null) {
                clear();
                return;
            }
            if (!chatOpen) {
                if (fadingSince < 0L) fadingSince = now;
                if (now - fadingSince >= FADE_OUT_MILLIS) {
                    clear();
                    return;
                }
            }
        } else {
            return;
        }

        float fadeIn = easeOut(Mth.clamp((now - shownSince) / (float) FADE_IN_MILLIS, 0.0F, 1.0F));
        float fadeOut = fadingSince < 0L
                ? 1.0F
                : 1.0F - easeOut(Mth.clamp((now - fadingSince) / (float) FADE_OUT_MILLIS, 0.0F, 1.0F));
        float opacity = fadeIn * fadeOut;
        if (opacity <= 0.015F) return;

        renderTooltip(event.getGuiGraphics(), minecraft.font, shop, opacity, fadeIn);
    }

    private static ServerShopBlockEntity targetedShop(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return null;
        return cachedShop(minecraft, hit.getBlockPos());
    }

    private static ServerShopBlockEntity cachedShop(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null
                || !(minecraft.level.getBlockEntity(pos) instanceof ServerShopBlockEntity shop)
                || !shop.configured()) {
            return null;
        }
        return shop;
    }

    private static void renderTooltip(
            GuiGraphics graphics,
            Font font,
            ServerShopBlockEntity shop,
            float opacity,
            float fadeIn
    ) {
        boolean buying = shop.mode() == ShopMode.SELL_TO_PLAYER;
        Component action = Component.translatable(
                buying
                        ? "overlay.numismatics_treasury.shop.buy"
                        : "overlay.numismatics_treasury.shop.sell"
        );
        Component price = Component.translatable(
                "overlay.numismatics_treasury.shop.price",
                MoneyDisplay.exact(shop.price())
        );
        String itemName = font.plainSubstrByWidth(shop.template().getHoverName().getString(), 180);

        int textXOffset = 22;
        int width = Math.max(
                textXOffset + Math.max(font.width(action), font.width(itemName)),
                20 + font.width(price)
        );
        int height = 37;
        int x = Mth.clamp(graphics.guiWidth() / 2 + 20, 7, graphics.guiWidth() - width - 7);
        int y = Mth.clamp(graphics.guiHeight() / 2 + 16, 7, graphics.guiHeight() - height - 7);
        float slide = (float) Math.pow(1.0F - fadeIn, 3.0D) * 8.0F;

        int modeColor = buying ? BUY_COLOR : SELL_COLOR;
        int borderBottom = buying ? BUY_BORDER_BOTTOM : SELL_BORDER_BOTTOM;

        graphics.pose().pushPose();
        graphics.pose().translate(slide, 0.0F, 0.0F);
        TooltipRenderUtil.renderTooltipBackground(
                graphics,
                x,
                y,
                width,
                height,
                400,
                withAlpha(BACKGROUND_TOP, opacity),
                withAlpha(BACKGROUND_BOTTOM, opacity),
                withAlpha(modeColor, opacity * 0.72F),
                withAlpha(borderBottom, opacity * 0.72F)
        );
        graphics.pose().translate(0.0F, 0.0F, 400.0F);

        graphics.setColor(1.0F, 1.0F, 1.0F, opacity);
        graphics.blit(buying ? BUY_ICON : SELL_ICON, x + 1, y, 0, 0, 16, 16, 16, 16);
        MoneyDisplay.renderIcon(graphics, x + 1, y + 27);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.drawString(font, action, x + textXOffset, y, withAlpha(modeColor, opacity), true);
        graphics.drawString(font, itemName, x + textXOffset, y + 12, withAlpha(0xFFFFFFFF, opacity), true);
        graphics.drawString(font, price, x + 20, y + 27, withAlpha(PRICE_COLOR, opacity), true);
        graphics.pose().popPose();
    }

    private static float easeOut(float progress) {
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse * inverse;
    }

    private static int withAlpha(int color, float opacity) {
        int sourceAlpha = color >>> 24;
        int alpha = Math.max(4, Math.round(sourceAlpha * Mth.clamp(opacity, 0.0F, 1.0F)));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private static void clear() {
        displayedPos = null;
        shownSince = 0L;
        fadingSince = -1L;
    }
}
