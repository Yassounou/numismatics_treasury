package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import dev.yassou.numismaticstreasury.client.gui.TreasuryButton;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class PlayerShopWithdrawScreen extends TreasuryScreen {
    private final long pos;
    private final long stock;
    private final int capacity;
    private final int maximum;
    private final String source;
    private final ItemStack item;
    private EditBox amountBox;
    private TreasuryButton withdrawButton;
    private boolean leaving;

    public PlayerShopWithdrawScreen(String json) {
        super(Component.translatable(
                "screen.numismatics_treasury.player_shop.withdraw_title"));
        JsonObject data = ClientScreenData.parse(json);
        pos = ClientScreenData.longValue(data, "pos", 0L);
        stock = Math.max(0L, ClientScreenData.longValue(data, "stock", 0L));
        capacity = Math.max(0, ClientScreenData.integer(data, "capacity", 0));
        maximum = Math.max(0, ClientScreenData.integer(data, "maximum", 0));
        source = ClientScreenData.string(data, "source", "screen");
        item = ClientScreenData.item(data);
    }

    @Override
    protected void init() {
        layout(350, 205);
        int x = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int maxWidth = 78;
        amountBox = new EditBox(
                font,
                x,
                panelTop + 106,
                contentWidth - maxWidth - 8,
                20,
                Component.translatable(
                        "screen.numismatics_treasury.player_shop.withdraw_amount")
        );
        amountBox.setMaxLength(10);
        amountBox.setFilter(value -> value.isEmpty()
                || value.chars().allMatch(Character::isDigit));
        int defaultAmount = Math.min(maximum,
                item.isEmpty() ? maximum : item.getMaxStackSize());
        amountBox.setValue(defaultAmount > 0 ? Integer.toString(defaultAmount) : "");
        amountBox.setHint(Component.translatable(
                "screen.numismatics_treasury.player_shop.withdraw_amount_hint"));
        amountBox.setResponder(ignored -> updateButton());
        addRenderableWidget(amountBox);
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.player_shop.withdraw_max"),
                        ignored -> selectMaximum())
                .bounds(x + contentWidth - maxWidth,
                        panelTop + 106, maxWidth, 20)
                .build()).active = maximum > 0;
        withdrawButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.player_shop.withdraw_confirm"),
                        ignored -> submit())
                .green()
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(x, panelTop + 157, contentWidth - 78, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.back"),
                        ignored -> leave())
                .bounds(x + contentWidth - 70, panelTop + 157, 70, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> leave())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());
        updateButton();
    }

    private void selectMaximum() {
        if (amountBox == null) return;
        amountBox.setValue(maximum > 0 ? Integer.toString(maximum) : "");
    }

    private int amount() {
        try {
            return amountBox == null || amountBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(amountBox.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void updateButton() {
        if (withdrawButton == null) return;
        int amount = amount();
        withdrawButton.active = amount > 0 && amount <= maximum;
        withdrawButton.resetConfirmation(Component.translatable(
                "screen.numismatics_treasury.player_shop.withdraw_confirm_amount",
                MoneyDisplay.exact(amount)
        ));
    }

    private void submit() {
        int amount = amount();
        if (amount <= 0 || amount > maximum) return;
        leaving = true;
        JsonObject data = requestData();
        data.addProperty("amount", amount);
        TreasuryNetwork.sendAction("player_shop_withdraw_amount", data);
        if (minecraft != null) minecraft.setScreen(null);
    }

    private void leave() {
        if (leaving) return;
        leaving = true;
        TreasuryNetwork.sendAction("player_shop_withdraw_back", requestData());
        if (minecraft != null) minecraft.setScreen(null);
    }

    private JsonObject requestData() {
        JsonObject data = new JsonObject();
        data.addProperty("pos", pos);
        data.addProperty("source", source);
        return data;
    }

    @Override
    public void onClose() {
        leave();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics);
        int x = panelLeft + 14;
        if (!item.isEmpty()) {
            graphics.renderItem(item.copyWithCount(1), x + 2, panelTop + 47);
            graphics.drawString(font, item.getHoverName(),
                    x + 26, panelTop + 47, TEXT, false);
        }
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.player_shop.withdraw_stock",
                        MoneyDisplay.exact(stock)),
                x + 26, panelTop + 60, ACCENT, false);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.player_shop.withdraw_capacity",
                        MoneyDisplay.exact(capacity)),
                x + 26, panelTop + 73, MUTED, false);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.player_shop.withdraw_amount"),
                x, panelTop + 92, TEXT, false);
        if (maximum <= 0) {
            graphics.drawString(font, Component.translatable(
                            "message.numismatics_treasury.inventory_no_space"),
                    x, panelTop + 132, ERROR, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!item.isEmpty()
                && mouseX >= x + 2 && mouseX < x + 18
                && mouseY >= panelTop + 47 && mouseY < panelTop + 63) {
            graphics.renderTooltip(font, item, mouseX, mouseY);
        }
    }
}
