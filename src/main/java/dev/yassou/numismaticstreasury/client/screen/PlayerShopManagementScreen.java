package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import dev.yassou.numismaticstreasury.client.gui.TreasuryButton;
import dev.yassou.numismaticstreasury.client.gui.TreasuryFrame;
import dev.yassou.numismaticstreasury.menu.PlayerShopMenu;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class PlayerShopManagementScreen
        extends AbstractContainerScreen<PlayerShopMenu> {
    private static final int PANEL = 0xEE171717;
    private static final int PANEL_ALT = 0xEE242424;
    private static final int SEPARATOR = 0xFF4B4B4B;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int ACCENT = 0xFFE0B04B;

    private EditBox priceBox;
    private EditBox lotSizeBox;
    private TreasuryButton saveButton;
    private TreasuryButton withdrawButton;

    public PlayerShopManagementScreen(
            PlayerShopMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 228;
        titleLabelX = 10;
        titleLabelY = 10;
        inventoryLabelX = 8;
        inventoryLabelY = 136;
    }

    @Override
    protected void init() {
        super.init();
        lotSizeBox = new EditBox(
                font,
                leftPos + 8,
                topPos + 76,
                78,
                20,
                Component.translatable("screen.numismatics_treasury.shop.items_per_lot")
        );
        lotSizeBox.setMaxLength(4);
        lotSizeBox.setFilter(value -> value.isEmpty()
                || value.chars().allMatch(Character::isDigit));
        lotSizeBox.setValue(Integer.toString(menu.lotSize()));
        lotSizeBox.setResponder(ignored -> updateButtons());
        addRenderableWidget(lotSizeBox);

        priceBox = new EditBox(
                font,
                leftPos + 90,
                topPos + 76,
                78,
                20,
                Component.translatable("screen.numismatics_treasury.shop.lot_price")
        );
        priceBox.setMaxLength(10);
        priceBox.setFilter(value -> value.isEmpty()
                || value.chars().allMatch(Character::isDigit));
        priceBox.setValue(Integer.toString(menu.price()));
        priceBox.setResponder(ignored -> updateButtons());
        addRenderableWidget(priceBox);

        saveButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.save"),
                        ignored -> save())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(leftPos + 8, topPos + 103, 78, 20)
                .build());
        withdrawButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.player_shop.withdraw_inventory"),
                        ignored -> withdraw())
                .bounds(leftPos + 90, topPos + 103, 78, 20)
                .build());
        updateButtons();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private int price() {
        try {
            return priceBox == null || priceBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(priceBox.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int lotSize() {
        try {
            return lotSizeBox == null || lotSizeBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(lotSizeBox.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void updateButtons() {
        if (saveButton != null) {
            saveButton.active = price() > 0 && lotSize() > 0 && lotSize() <= 2_304
                    && (!menu.inputStack().isEmpty() || !menu.shopItem().isEmpty());
        }
        if (withdrawButton != null) {
            withdrawButton.active = menu.stock() > 0L && menu.inputStack().isEmpty();
        }
    }

    private void save() {
        JsonObject data = new JsonObject();
        data.addProperty("pos", menu.blockPos().asLong());
        data.addProperty("price", price());
        data.addProperty("lotSize", lotSize());
        TreasuryNetwork.sendAction("player_shop_menu_config", data);
    }

    private void withdraw() {
        JsonObject data = new JsonObject();
        data.addProperty("pos", menu.blockPos().asLong());
        TreasuryNetwork.sendAction("player_shop_menu_withdraw", data);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hasStoredShopItem()) {
            ItemStack shown = menu.shopItem().copyWithCount(1);
            int slotX = leftPos + 9;
            int slotY = topPos + 38;
            graphics.renderItem(shown, slotX, slotY);
            graphics.renderItemDecorations(
                    font,
                    shown,
                    slotX,
                    slotY,
                    Long.toString(menu.stock())
            );
            if (mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16) {
                graphics.renderTooltip(font, shown, mouseX, mouseY);
                return;
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private boolean hasStoredShopItem() {
        return menu.inputStack().isEmpty() && !menu.shopItem().isEmpty();
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        TreasuryFrame.renderAround(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(
                leftPos + 1,
                topPos + 29,
                leftPos + imageWidth - 1,
                topPos + 30,
                SEPARATOR
        );
        slotBackground(graphics, leftPos + 8, topPos + 37);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotBackground(
                        graphics,
                        leftPos + 7 + column * 18,
                        topPos + 146 + row * 18
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            slotBackground(graphics, leftPos + 7 + column * 18, topPos + 204);
        }
    }

    private static void slotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SEPARATOR);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xEE181818);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, ACCENT, false);
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.player_shop.input"),
                31,
                37,
                TEXT,
                false
        );
        ItemStack shown = menu.inputStack().isEmpty()
                ? menu.shopItem() : menu.inputStack();
        Component name = shown.isEmpty()
                ? Component.translatable("screen.numismatics_treasury.no_item")
                : shown.getHoverName();
        graphics.drawString(
                font,
                font.plainSubstrByWidth(name.getString(), imageWidth - 39),
                31,
                49,
                shown.isEmpty() ? MUTED : TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.shop.items_per_lot_short"),
                8,
                65,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.shop.lot_price_short"),
                90,
                65,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.player_shop.shift_click_help"),
                8,
                126,
                MUTED,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.inventory"),
                inventoryLabelX,
                inventoryLabelY,
                TEXT,
                false
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
