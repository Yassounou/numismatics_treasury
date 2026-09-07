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

public final class PlayerShopScreen extends TreasuryScreen {
    private final boolean management;
    private final long pos;
    private final int balance;
    private final int price;
    private final long stock;
    private final String ownerName;
    private final ItemStack configuredItem;
    private final String configuredItemName;

    private EditBox valueBox;
    private TreasuryButton primaryButton;
    private TreasuryButton depositButton;
    private InventoryPicker inventoryPicker;

    public PlayerShopScreen(String json, boolean management) {
        super(Component.translatable(management
                ? "screen.numismatics_treasury.player_shop.manage_title"
                : "screen.numismatics_treasury.player_shop.title"));
        JsonObject data = ClientScreenData.parse(json);
        this.management = management;
        pos = ClientScreenData.longValue(data, "pos", 0L);
        balance = ClientScreenData.integer(data, "balance", 0);
        price = ClientScreenData.integer(data, "price", 0);
        stock = ClientScreenData.longValue(data, "stock", 0L);
        ownerName = ClientScreenData.string(data, "ownerName", "?");
        configuredItem = ClientScreenData.item(data);
        configuredItemName = ClientScreenData.string(
                data,
                "itemName",
                Component.translatable("screen.numismatics_treasury.no_item").getString()
        );
    }

    @Override
    protected void init() {
        if (management) initManagement();
        else initCustomer();
    }

    private void initCustomer() {
        layout(350, 205);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        valueBox = numericBox(
                x,
                panelTop + 126,
                contentWidth,
                Component.translatable("screen.numismatics_treasury.shop.quantity"),
                "1"
        );
        valueBox.setResponder(ignored -> updateButtons());
        addRenderableWidget(valueBox);
        primaryButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.player_shop.buy"),
                        ignored -> buy())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x, panelTop + 162, contentWidth - 78, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.close"),
                        ignored -> onClose())
                .bounds(x + contentWidth - 70, panelTop + 162, 70, 20)
                .build());
        updateButtons();
    }

    private void initManagement() {
        layout(380, 286);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        valueBox = numericBox(
                x,
                panelTop + 107,
                112,
                Component.translatable("screen.numismatics_treasury.shop.unit_price"),
                Integer.toString(price)
        );
        valueBox.setResponder(ignored -> updateButtons());
        addRenderableWidget(valueBox);
        primaryButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.save"),
                        ignored -> save())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x + 120, panelTop + 107, contentWidth - 120, 20)
                .build());
        depositButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.player_shop.deposit_stack"),
                        ignored -> deposit())
                .green()
                .bounds(x, panelTop + 138, (contentWidth - 8) / 2, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.player_shop.withdraw_stack"),
                        ignored -> withdraw())
                .bounds(x + (contentWidth + 8) / 2, panelTop + 138,
                        (contentWidth - 8) / 2, 20)
                .build()).active = stock > 0L;
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> onClose())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());
        inventoryPicker = new InventoryPicker(x, panelTop + 194, -1);
        updateButtons();
    }

    private EditBox numericBox(int x, int y, int width, Component label, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, label);
        box.setMaxLength(10);
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        box.setValue(value);
        return box;
    }

    private int value() {
        try {
            return valueBox == null || valueBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(valueBox.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private ItemStack selectedItem() {
        if (inventoryPicker == null || minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        return inventoryPicker.selectedStack(minecraft.player);
    }

    private ItemStack previewItem() {
        ItemStack selected = selectedItem();
        return selected.isEmpty() ? configuredItem : selected;
    }

    private Component previewName() {
        ItemStack selected = selectedItem();
        if (!selected.isEmpty()) return selected.getHoverName();
        return configuredItem.isEmpty()
                ? Component.translatable("screen.numismatics_treasury.no_item")
                : Component.literal(configuredItemName);
    }

    private void updateButtons() {
        if (primaryButton == null) return;
        int value = value();
        if (management) {
            primaryButton.active = !previewItem().isEmpty() && value > 0;
            primaryButton.resetConfirmation(Component.translatable(
                    "screen.numismatics_treasury.save"));
            if (depositButton != null) depositButton.active = !selectedItem().isEmpty();
        } else {
            primaryButton.active = !configuredItem.isEmpty()
                    && value > 0 && value <= 2_304 && value <= stock;
            long total = (long) price * value;
            primaryButton.resetConfirmation(Component.translatable(
                    "screen.numismatics_treasury.player_shop.buy_total",
                    MoneyDisplay.exact(total)
            ));
        }
    }

    private void save() {
        JsonObject data = positionData();
        data.addProperty("price", value());
        data.addProperty("inventorySlot",
                inventoryPicker == null ? -1 : inventoryPicker.selectedSlot());
        TreasuryNetwork.sendAction("player_shop_config", data);
    }

    private void deposit() {
        if (inventoryPicker == null || inventoryPicker.selectedSlot() < 0) return;
        JsonObject data = positionData();
        data.addProperty("inventorySlot", inventoryPicker.selectedSlot());
        TreasuryNetwork.sendAction("player_shop_deposit", data);
    }

    private void withdraw() {
        TreasuryNetwork.sendAction("player_shop_withdraw", positionData());
    }

    private void buy() {
        JsonObject data = positionData();
        data.addProperty("quantity", value());
        TreasuryNetwork.sendAction("player_shop_trade", data);
    }

    private JsonObject positionData() {
        JsonObject data = new JsonObject();
        data.addProperty("pos", pos);
        return data;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!management || inventoryPicker == null
                || minecraft == null || minecraft.player == null) {
            return false;
        }
        if (inventoryPicker.mouseClicked(minecraft.player, mouseX, mouseY, button)) {
            updateButtons();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics);
        if (management) renderManagement(graphics, mouseX, mouseY);
        else renderCustomer(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (management && inventoryPicker != null
                && minecraft != null && minecraft.player != null) {
            ItemStack hovered = inventoryPicker.hoveredStack(
                    minecraft.player, mouseX, mouseY);
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
                panelTop + 114,
                TEXT,
                false
        );
    }

    private void renderManagement(GuiGraphics graphics, int mouseX, int mouseY) {
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
                panelTop + 181,
                TEXT,
                false
        );
        if (inventoryPicker != null && minecraft != null && minecraft.player != null) {
            inventoryPicker.render(graphics, font, minecraft.player, mouseX, mouseY);
        }
        graphics.drawWordWrap(
                font,
                Component.translatable("screen.numismatics_treasury.player_shop.manage_help"),
                panelLeft + 184,
                panelTop + 198,
                panelWidth - 198,
                MUTED
        );
    }

    private void renderItemCard(GuiGraphics graphics, int y, boolean editable) {
        int x = panelLeft + 14;
        int cardWidth = panelWidth - 28;
        ItemStack shown = previewItem();
        graphics.fill(x, y, x + cardWidth, y + 48, PANEL_ALT);
        outline(graphics, x, y, cardWidth, 48, shown.isEmpty() ? ERROR : SEPARATOR);
        if (!shown.isEmpty()) graphics.renderItem(shown, x + 10, y + 10);
        graphics.drawString(font, previewName(), x + 34, y + 8, TEXT, false);
        Component details = editable
                ? Component.translatable("screen.numismatics_treasury.player_shop.stock", stock)
                : Component.translatable(
                        "screen.numismatics_treasury.player_shop.seller_stock",
                        ownerName,
                        stock
                );
        graphics.drawString(font, details, x + 34, y + 24, ACCENT, false);
        if (!editable) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "screen.numismatics_treasury.player_shop.unit_price_value",
                            MoneyDisplay.exact(price)
                    ),
                    x + 34,
                    y + 35,
                    MUTED,
                    false
            );
        }
    }
}
