package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import dev.yassou.numismaticstreasury.client.gui.TreasuryButton;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BankTellerScreen extends TreasuryScreen {
    private final int balance;
    private final int minimum;
    private final int maximum;
    private final long pos;
    private final boolean portable;
    private final List<String> recipientNames;
    private EditBox recipient;
    private EditBox amount;
    private TreasuryButton send;
    private List<String> completionMatches = List.of();
    private int completionIndex = -1;
    private boolean applyingCompletion;

    public BankTellerScreen(String json) {
        super(Component.translatable("screen.numismatics_treasury.transfer_teller.title"));
        JsonObject data = ClientScreenData.parse(json);
        balance = ClientScreenData.integer(data, "balance", 0);
        pos = ClientScreenData.longValue(data, "pos", Long.MIN_VALUE);
        portable = ClientScreenData.bool(data, "portable", false);
        minimum = ClientScreenData.integer(data, "minimum", 1);
        maximum = ClientScreenData.integer(data, "maximum", Integer.MAX_VALUE);
        List<String> parsedNames = new ArrayList<>();
        JsonArray recipients = data.has("recipients")
                ? data.getAsJsonArray("recipients") : new JsonArray();
        for (JsonElement value : recipients) {
            if (value.isJsonPrimitive()) parsedNames.add(value.getAsString());
        }
        recipientNames = List.copyOf(parsedNames);
    }

    @Override
    protected void init() {
        layout(330, 196);
        int x = panelLeft + 14;
        int width = panelWidth - 28;
        recipient = new EditBox(
                font, x, panelTop + 58, width, 20,
                Component.translatable("screen.numismatics_treasury.transfer_teller.recipient")
        );
        recipient.setHint(Component.translatable(
                "screen.numismatics_treasury.transfer_teller.recipient_hint"));
        recipient.setMaxLength(16);
        recipient.setResponder(value -> {
            if (!applyingCompletion) updateCompletionMatches(value);
            updateSendState();
        });
        addRenderableWidget(recipient);

        amount = new EditBox(
                font, x, panelTop + 112, width, 20,
                Component.translatable("screen.numismatics_treasury.transfer_teller.amount")
        );
        amount.setHint(Component.translatable(
                "screen.numismatics_treasury.transfer_teller.amount_hint"));
        amount.setMaxLength(10);
        amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        amount.setResponder(value -> updateSendState());
        addRenderableWidget(amount);

        send = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.transfer_teller.send"),
                        ignored -> submit()
                ).green()
                .confirmation(Component.translatable(
                        "screen.numismatics_treasury.transfer_teller.confirm"))
                .bounds(x, panelTop + 155, 156, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.history.short"),
                        ignored -> openHistory()
                ).bounds(x + 160, panelTop + 155, 82, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.close"),
                        ignored -> onClose()
                ).bounds(x + 246, panelTop + 155, 56, 20)
                .build());
        updateSendState();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB
                && recipient != null
                && recipient.isFocused()
                && completeRecipient((modifiers & GLFW.GLFW_MOD_SHIFT) != 0)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean completeRecipient(boolean backwards) {
        if (completionMatches.isEmpty()) {
            updateCompletionMatches(recipient.getValue());
        }
        if (completionMatches.isEmpty()) return false;
        if (completionIndex < 0) {
            completionIndex = backwards ? completionMatches.size() - 1 : 0;
        } else {
            completionIndex = Math.floorMod(
                    completionIndex + (backwards ? -1 : 1),
                    completionMatches.size()
            );
        }
        applyingCompletion = true;
        recipient.setValue(completionMatches.get(completionIndex));
        recipient.setSuggestion(null);
        applyingCompletion = false;
        return true;
    }

    private void updateCompletionMatches(String value) {
        String prefix = value.toLowerCase(Locale.ROOT);
        completionMatches = recipientNames.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
        completionIndex = -1;
        if (value.isEmpty() || completionMatches.isEmpty()) {
            recipient.setSuggestion(null);
            return;
        }
        String first = completionMatches.getFirst();
        recipient.setSuggestion(first.length() > value.length()
                ? first.substring(value.length()) : null);
    }

    private void updateSendState() {
        if (send == null) return;
        int value = parsedAmount();
        send.resetConfirmation(Component.translatable(
                "screen.numismatics_treasury.transfer_teller.send"));
        send.active = recipient != null && !recipient.getValue().isBlank()
                && value >= minimum && value <= maximum && value <= balance;
    }

    private int parsedAmount() {
        try {
            return amount == null || amount.getValue().isBlank()
                    ? 0 : Integer.parseInt(amount.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void submit() {
        if (send == null || !send.active) return;
        JsonObject data = new JsonObject();
        data.addProperty("target", recipient.getValue().strip());
        data.addProperty("amount", parsedAmount());
        data.addProperty("portable", portable);
        if (!portable) data.addProperty("pos", pos);
        TreasuryNetwork.sendAction("pay", data);
    }

    private void openHistory() {
        JsonObject data = new JsonObject();
        data.addProperty("source", "bank");
        data.addProperty("portable", portable);
        if (!portable) data.addProperty("pos", pos);
        TreasuryNetwork.sendAction("history_open", data);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics);
        MoneyDisplay.renderBadge(
                graphics,
                font,
                panelLeft + panelWidth - MoneyDisplay.badgeWidth(font, balance) - 10,
                panelTop + 6,
                balance
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.transfer_teller.recipient"),
                panelLeft + 14,
                panelTop + 45,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.numismatics_treasury.transfer_teller.recipient_tab_help"
                ),
                panelLeft + 14,
                panelTop + 81,
                MUTED,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.transfer_teller.amount_short"),
                panelLeft + 14,
                panelTop + 99,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.transfer_teller.help"),
                panelLeft + 14,
                panelTop + 138,
                MUTED,
                false
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
