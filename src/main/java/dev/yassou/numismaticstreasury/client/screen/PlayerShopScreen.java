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
    private final int lotSize;
    private final long stock;
    private final String ownerName;
    private final ItemStack configuredItem;
    private final String configuredItemName;

    private EditBox valueBox;
    private EditBox lotSizeBox;
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
        lotSize = Math.max(1, ClientScreenData.integer(data, "lotSize", 1));
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
        layout(370, 225);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        valueBox = numericBox(
                x,
                panelTop + 126,
                contentWidth,
                Component.translatable("screen.numismatics_treasury.shop.lots"),
                "1"
        );
        valueBox.setResponder(ignored -> updateButtons());
        addRenderableWidget(valueBox);
        primaryButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.player_shop.buy"),
                        ignored -> buy())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x, panelTop + 181, contentWidth - 78, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.close"),
                        ignored -> onClose())
                .bounds(x + contentWidth - 70, panelTop + 181, 70, 20)
                .build());
        updateButtons();
    }

    private void initManagement() {
        layout(400, 322);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int fieldWidth = (contentWidth - 8) / 2;
        lotSizeBox = numericBox(
                x,
                panelTop + 107,
                fieldWidth,
                Component.translatable("screen.numismatics_treasury.shop.items_per_lot"),
                Integer.toString(lotSize)
        );
        lotSizeBox.setResponder(ignored -> updateButtons());
        addRenderableWidget(lotSizeBox);
        valueBox = numericBox(
                x + fieldWidth + 8,
                panelTop + 107,
                fieldWidth,
                Component.translatable("screen.numismatics_treasury.shop.lot_price"),
                Integer.toString(price)
        );
        valueBox.setResponder(ignored -> updateButtons());
        addRenderableWidget(valueBox);
        primaryButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.save"),
                        ignored -> save())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x, panelTop + 138, contentWidth, 20)
                .build());
        depositButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.player_shop.deposit_stack"),
                        ignored -> deposit())
                .green()
                .bounds(x, panelTop + 169, (contentWidth - 8) / 2, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.player_shop.withdraw_stack"),
                        ignored -> withdraw())
                .bounds(x + (contentWidth + 8) / 2, panelTop + 169,
                        (contentWidth - 8) / 2, 20)
                .build()).active = stock > 0L;
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> onClose())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());
        inventoryPicker = new InventoryPicker(x, panelTop + 230, -1);
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

    private int configuredLotSize() {
        try {
            return lotSizeBox == null || lotSizeBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(lotSizeBox.getValue());
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

    private Component displayedPrice() {
        if (lotSize == 1) {
            return Component.translatable(
                    "screen.numismatics_treasury.player_shop.unit_price_value",
                    MoneyDisplay.exact(price)
            );
        }
        return Component.translatable(
                "screen.numismatics_treasury.player_shop.lot_price_value",
                MoneyDisplay.exact(lotSize),
                MoneyDisplay.exact(price)
        );
    }

    private void updateButtons() {
        if (primaryButton == null) return;
        int value = value();
        if (management) {
            primaryButton.active = !previewItem().isEmpty() && value > 0
                    && configuredLotSize() > 0 && configuredLotSize() <= 2_304;
            primaryButton.resetConfirmation(Component.translatable(
                    "screen.numismatics_treasury.save"));
            if (depositButton != null) depositButton.active = !selectedItem().isEmpty();
        } else {
            primaryButton.active = !configuredItem.isEmpty()
                    && value > 0
                    && value <= Math.max(1, 2_304 / lotSize)
                    && (long) value * lotSize <= stock;
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
        data.addProperty("lotSize", configuredLotSize());
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
        data.addProperty("lots", value());
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
                Component.translatable("screen.numismatics_treasury.shop.lots"),
                panelLeft + 14,
                panelTop + 114,
                TEXT,
                false
        );
        int lots = value();
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.numismatics_treasury.shop.trade_summary",
                        MoneyDisplay.exact((long) lotSize * lots),
                        MoneyDisplay.exact((long) price * lots)
                ),
                panelLeft + 14,
                panelTop + 157,
                lots > 0 ? ACCENT : MUTED,
                false
        );
    }

    private void renderManagement(GuiGraphics graphics, int mouseX, int mouseY) {
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
                panelTop + 217,
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
                panelTop + 234,
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
        graphics.drawString(
                font,
                font.plainSubstrByWidth(previewName().getString(), cardWidth - 44),
                x + 34,
                y + 8,
                TEXT,
                false
        );
        Component details = editable
                ? Component.translatable("screen.numismatics_treasury.player_shop.stock", stock)
                : lotSize == 1
                    ? Component.translatable(
                            "screen.numismatics_treasury.player_shop.seller_stock",
                            ownerName,
                            stock
                    )
                    : Component.translatable(
                            "screen.numismatics_treasury.player_shop.seller_lot_stock",
                            ownerName,
                            MoneyDisplay.exact(stock / lotSize),
                            MoneyDisplay.exact(stock)
                    );
        graphics.drawString(
                font,
                font.plainSubstrByWidth(details.getString(), cardWidth - 44),
                x + 34,
                y + 24,
                ACCENT,
                false
        );
        if (!editable) {
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(displayedPrice().getString(), cardWidth - 44),
                    x + 34,
                    y + 35,
                    MUTED,
                    false
            );
        }
    }
}
