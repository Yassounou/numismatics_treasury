package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.client.gui.InventoryPicker;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import dev.yassou.numismaticstreasury.client.gui.TreasuryButton;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ServerShopScreen extends TreasuryScreen {
    private final boolean admin;
    private final long pos;
    private final int balance;
    private final ItemStack configuredItem;
    private String mode;
    private final int initialPrice;
    private final int initialLotSize;
    private EditBox valueBox;
    private EditBox lotSizeBox;
    private TreasuryButton actionButton;
    private InventoryPicker inventoryPicker;
    private int selectedInventorySlot = -1;

    public ServerShopScreen(String json, boolean admin) {
        super(Component.translatable(admin
                ? "screen.numismatics_treasury.shop.admin_title"
                : "screen.numismatics_treasury.shop.title"));
        JsonObject data = ClientScreenData.parse(json);
        this.admin = admin;
        pos = ClientScreenData.longValue(data, "pos", 0L);
        balance = ClientScreenData.integer(data, "balance", 0);
        configuredItem = ClientScreenData.item(data);
        mode = ClientScreenData.string(data, "mode", "SELL_TO_PLAYER");
        initialPrice = ClientScreenData.integer(data, "price", 0);
        initialLotSize = Math.max(1, ClientScreenData.integer(data, "lotSize", 1));
    }

    @Override
    protected void init() {
        if (admin) initAdmin();
        else initCustomer();
    }

    private void initCustomer() {
        layout(370, 218);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        boolean playerSells = mode.equals("BUY_FROM_PLAYER");
        int quantityWidth = playerSells ? contentWidth - 96 : contentWidth;
        valueBox = numericBox(
                x,
                panelTop + 107,
                quantityWidth,
                Component.translatable("screen.numismatics_treasury.shop.lots"),
                "1"
        );
        valueBox.setResponder(value -> updateAction());
        addRenderableWidget(valueBox);

        if (playerSells) {
            addRenderableWidget(TreasuryButton.builder(
                            Component.translatable(
                                    "screen.numismatics_treasury.shop.sell_all"),
                            ignored -> sellAll())
                    .green()
                    .confirmation(Component.translatable(initialLotSize == 1
                            ? "screen.numismatics_treasury.shop.confirm_sell_all"
                            : "screen.numismatics_treasury.shop.confirm_sell_all_lots"))
                    .bounds(x + quantityWidth + 8, panelTop + 107, 88, 20)
                    .build());
        }

        actionButton = addRenderableWidget(TreasuryButton.builder(
                        tradeLabel(), ignored -> submit())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x, panelTop + 174, contentWidth - 78, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.close"),
                        ignored -> onClose())
                .bounds(x + contentWidth - 70, panelTop + 174, 70, 20)
                .build());
        updateAction();
    }

    private void initAdmin() {
        layout(390, 316);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int fieldWidth = (contentWidth - 8) / 2;
        lotSizeBox = numericBox(
                x,
                panelTop + 107,
                fieldWidth,
                Component.translatable("screen.numismatics_treasury.shop.items_per_lot"),
                Integer.toString(initialLotSize)
        );
        lotSizeBox.setResponder(value -> updateAction());
        addRenderableWidget(lotSizeBox);
        valueBox = numericBox(
                x + fieldWidth + 8,
                panelTop + 107,
                fieldWidth,
                Component.translatable("screen.numismatics_treasury.shop.lot_price"),
                Integer.toString(initialPrice)
        );
        valueBox.setResponder(value -> updateAction());
        addRenderableWidget(valueBox);

        addRenderableWidget(TreasuryButton.builder(
                        modeLabel(),
                        button -> {
                            mode = mode.equals("SELL_TO_PLAYER")
                                    ? "BUY_FROM_PLAYER" : "SELL_TO_PLAYER";
                            button.setMessage(modeLabel());
                        })
                .bounds(x, panelTop + 138, fieldWidth, 20)
                .build());
        actionButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.save"),
                        ignored -> submit())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x + fieldWidth + 8, panelTop + 138, fieldWidth, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> onClose())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());

        inventoryPicker = new InventoryPicker(
                x,
                panelTop + 216,
                selectedInventorySlot
        );
        updateAction();
    }

    private EditBox numericBox(int x, int y, int width, Component label, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, label);
        box.setMaxLength(10);
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        box.setValue(value);
        return box;
    }

    private Component modeLabel() {
        return Component.translatable(mode.equals("SELL_TO_PLAYER")
                ? "screen.numismatics_treasury.shop.mode.sell"
                : "screen.numismatics_treasury.shop.mode.buy");
    }

    private Component tradeLabel() {
        return Component.translatable(mode.equals("SELL_TO_PLAYER")
                ? "screen.numismatics_treasury.shop.buy"
                : "screen.numismatics_treasury.shop.sell");
    }

    private Component offerLabel() {
        boolean serverSells = mode.equals("SELL_TO_PLAYER");
        if (initialLotSize == 1) {
            return Component.translatable(
                    serverSells
                            ? "screen.numismatics_treasury.shop.server_sells"
                            : "screen.numismatics_treasury.shop.server_buys",
                    MoneyDisplay.exact(initialPrice)
            );
        }
        return Component.translatable(
                serverSells
                        ? "screen.numismatics_treasury.shop.server_sells_lot"
                        : "screen.numismatics_treasury.shop.server_buys_lot",
                MoneyDisplay.exact(initialLotSize),
                MoneyDisplay.exact(initialPrice)
        );
    }

    private int value() {
        try {
            return valueBox == null || valueBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(valueBox.getValue());
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

    private ItemStack previewStack() {
        if (admin && inventoryPicker != null && minecraft != null && minecraft.player != null) {
            ItemStack selected = inventoryPicker.selectedStack(minecraft.player);
            if (!selected.isEmpty()) return selected;
        }
        return configuredItem;
    }

    private Component previewName() {
        ItemStack selected = previewStack();
        if (selected.isEmpty()) {
            return Component.translatable("screen.numismatics_treasury.no_item");
        }
        return selected.getHoverName();
    }

    private void updateAction() {
        if (actionButton == null) return;
        int value = value();
        actionButton.active = !previewStack().isEmpty() && value > 0
                && (admin ? lotSize() > 0 && lotSize() <= 2_304
                : value <= Math.max(1, 2_304 / initialLotSize));
        if (!admin && value > 0) {
            long total = (long) initialPrice * value;
            actionButton.resetConfirmation(Component.translatable(
                    mode.equals("SELL_TO_PLAYER")
                            ? "screen.numismatics_treasury.shop.buy_total"
                            : "screen.numismatics_treasury.shop.sell_total",
                    MoneyDisplay.exact(total)
            ));
        } else {
            actionButton.resetConfirmation(admin
                    ? Component.translatable("screen.numismatics_treasury.save")
                    : tradeLabel());
        }
    }

    private void submit() {
        if (actionButton == null || !actionButton.active) return;
        JsonObject data = new JsonObject();
        data.addProperty("pos", pos);
        if (admin) {
            data.addProperty("mode", mode);
            data.addProperty("price", value());
            data.addProperty("lotSize", lotSize());
            data.addProperty("inventorySlot", inventoryPicker == null
                    ? -1 : inventoryPicker.selectedSlot());
            TreasuryNetwork.sendAction("shop_config", data);
        } else {
            data.addProperty("lots", value());
            TreasuryNetwork.sendAction("shop_trade", data);
        }
    }

    private void sellAll() {
        JsonObject data = new JsonObject();
        data.addProperty("pos", pos);
        TreasuryNetwork.sendAction("shop_sell_all", data);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!admin || inventoryPicker == null || minecraft == null || minecraft.player == null) {
            return false;
        }
        if (mouseX >= panelLeft + 14 && mouseX < panelLeft + panelWidth - 14
                && mouseY >= panelTop + 40 && mouseY < panelTop + 88 && button == 0) {
            inventoryPicker.clearSelection();
            selectedInventorySlot = -1;
            updateAction();
            return true;
        }
        if (inventoryPicker.mouseClicked(minecraft.player, mouseX, mouseY, button)) {
            selectedInventorySlot = inventoryPicker.selectedSlot();
            updateAction();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics);
        if (admin) renderAdmin(graphics, mouseX, mouseY);
        else renderCustomer(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (admin && inventoryPicker != null && minecraft != null && minecraft.player != null) {
            ItemStack hovered = inventoryPicker.hoveredStack(minecraft.player, mouseX, mouseY);
            if (!hovered.isEmpty()) {
                graphics.renderTooltip(font, hovered, mouseX, mouseY);
                return;
            }
        }
        ItemStack shown = previewStack();
        int cardX = panelLeft + 14;
        int cardY = panelTop + 40;
        if (!shown.isEmpty()
                && mouseX >= cardX && mouseX < cardX + panelWidth - 28
                && mouseY >= cardY && mouseY < cardY + 48) {
            graphics.renderTooltip(font, shown, mouseX, mouseY);
        }
    }

    private void renderCustomer(GuiGraphics graphics) {
        MoneyDisplay.renderBadge(
                graphics,
                font,
                panelLeft + panelWidth - MoneyDisplay.badgeWidth(font, balance) - 10,
                panelTop + 6,
                balance
        );
        renderItemCard(graphics, panelTop + 40, false);
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.shop.lots"),
                panelLeft + 14,
                panelTop + 95,
                TEXT,
                false
        );
        int lots = value();
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.numismatics_treasury.shop.trade_summary",
                        MoneyDisplay.exact((long) initialLotSize * lots),
                        MoneyDisplay.exact((long) initialPrice * lots)
                ),
                panelLeft + 14,
                panelTop + 138,
                lots > 0 ? ACCENT : MUTED,
                false
        );
    }

    private void renderAdmin(GuiGraphics graphics, int mouseX, int mouseY) {
        renderItemCard(graphics, panelTop + 40, true);
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.shop.items_per_lot"),
                panelLeft + 14,
                panelTop + 95,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.shop.lot_price"),
                panelLeft + 14 + (panelWidth - 36) / 2 + 8,
                panelTop + 95,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.inventory"),
                panelLeft + 14,
                panelTop + 202,
                TEXT,
                false
        );
        if (minecraft != null && minecraft.player != null && inventoryPicker != null) {
            inventoryPicker.render(graphics, font, minecraft.player, mouseX, mouseY);
        }
        graphics.drawWordWrap(
                font,
                Component.translatable("screen.numismatics_treasury.shop.pick_help"),
                panelLeft + 184,
                panelTop + 220,
                panelWidth - 198,
                MUTED
        );
    }

    private void renderItemCard(GuiGraphics graphics, int cardY, boolean editable) {
        int cardX = panelLeft + 14;
        int cardWidth = panelWidth - 28;
        ItemStack shown = previewStack();
        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + 48, PANEL_ALT);
        outline(graphics, cardX, cardY, cardWidth, 48, shown.isEmpty() ? ERROR : SEPARATOR);
        if (!shown.isEmpty()) graphics.renderItem(shown, cardX + 10, cardY + 10);
        graphics.drawString(
                font,
                font.plainSubstrByWidth(previewName().getString(), cardWidth - 44),
                cardX + 34,
                cardY + 10,
                TEXT,
                false
        );
        Component subtitle = editable
                ? Component.translatable(
                        "screen.numismatics_treasury.shop.selected_item_help")
                : offerLabel();
        graphics.drawString(
                font,
                font.plainSubstrByWidth(subtitle.getString(), cardWidth - 44),
                cardX + 34,
                cardY + 27,
                editable ? MUTED : ACCENT,
                false
        );
    }
}
