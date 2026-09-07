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
    private final String configuredItemName;
    private String mode;
    private final int initialPrice;
    private EditBox valueBox;
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
        configuredItemName = ClientScreenData.string(
                data,
                "itemName",
                Component.translatable("screen.numismatics_treasury.no_item").getString()
        );
        mode = ClientScreenData.string(data, "mode", "SELL_TO_PLAYER");
        initialPrice = ClientScreenData.integer(data, "price", 0);
    }

    @Override
    protected void init() {
        if (admin) initAdmin();
        else initCustomer();
    }

    private void initCustomer() {
        layout(350, 190);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        boolean playerSells = mode.equals("BUY_FROM_PLAYER");
        int quantityWidth = playerSells ? contentWidth - 96 : contentWidth;
        valueBox = numericBox(
                x,
                panelTop + 105,
                quantityWidth,
                Component.translatable("screen.numismatics_treasury.shop.quantity"),
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
                    .confirmation(Component.translatable(
                            "screen.numismatics_treasury.shop.confirm_sell_all"))
                    .bounds(x + quantityWidth + 8, panelTop + 105, 88, 20)
                    .build());
        }

        actionButton = addRenderableWidget(TreasuryButton.builder(
                        tradeLabel(), ignored -> submit())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x, panelTop + 140, contentWidth - 78, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.close"),
                        ignored -> onClose())
                .bounds(x + contentWidth - 70, panelTop + 140, 70, 20)
                .build());
        updateAction();
    }

    private void initAdmin() {
        layout(370, 278);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        valueBox = numericBox(
                x,
                panelTop + 107,
                112,
                Component.translatable("screen.numismatics_treasury.shop.unit_price"),
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
                .bounds(x + 120, panelTop + 107, 108, 20)
                .build());
        actionButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.save"),
                        ignored -> submit())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x + 236, panelTop + 107, contentWidth - 236, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> onClose())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());

        inventoryPicker = new InventoryPicker(
                x,
                panelTop + 174,
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

    private int value() {
        try {
            return valueBox == null || valueBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(valueBox.getValue());
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
        if (admin && inventoryPicker != null && minecraft != null && minecraft.player != null
                && !inventoryPicker.selectedStack(minecraft.player).isEmpty()) {
            return selected.getHoverName();
        }
        return Component.literal(configuredItemName);
    }

    private void updateAction() {
        if (actionButton == null) return;
        int value = value();
        actionButton.active = !previewStack().isEmpty() && value > 0
                && (admin || value <= 2_304);
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
            data.addProperty("inventorySlot", inventoryPicker == null
                    ? -1 : inventoryPicker.selectedSlot());
            TreasuryNetwork.sendAction("shop_config", data);
        } else {
            data.addProperty("quantity", value());
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
            if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
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
                Component.translatable("screen.numismatics_treasury.shop.quantity"),
                panelLeft + 14,
                panelTop + 94,
                TEXT,
                false
        );
    }

    private void renderAdmin(GuiGraphics graphics, int mouseX, int mouseY) {
        renderItemCard(graphics, panelTop + 40, true);
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.shop.unit_price"),
                panelLeft + 14,
                panelTop + 95,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.inventory"),
                panelLeft + 14,
                panelTop + 160,
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
                panelTop + 178,
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
        graphics.drawString(font, previewName(), cardX + 34, cardY + 10, TEXT, false);
        Component subtitle = editable
                ? Component.translatable(
                        "screen.numismatics_treasury.shop.selected_item_help")
                : Component.translatable(
                        mode.equals("SELL_TO_PLAYER")
                                ? "screen.numismatics_treasury.shop.server_sells"
                                : "screen.numismatics_treasury.shop.server_buys",
                        initialPrice
                );
        graphics.drawString(
                font,
                subtitle,
                cardX + 34,
                cardY + 27,
                editable ? MUTED : ACCENT,
                false
        );
    }
}
