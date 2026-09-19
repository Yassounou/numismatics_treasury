package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.client.gui.TreasuryButton;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerShopAssociatesScreen extends TreasuryScreen {
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 6;

    private final long pos;
    private final String ownerName;
    private final int linkedTotal;
    private final int maxAssociates;
    private final List<AssociateView> associates;
    private final List<String> playerNames;
    private EditBox playerName;
    private EditBox share;
    private TreasuryButton applyButton;
    private TreasuryButton removeButton;
    private int listX;
    private int listY;
    private int listWidth;
    private int scrollRow;
    private int selectedIndex = -1;
    private boolean leaving;
    private List<String> completionMatches = List.of();
    private int completionIndex = -1;
    private boolean applyingCompletion;

    public PlayerShopAssociatesScreen(String json) {
        super(Component.translatable(
                "screen.numismatics_treasury.player_shop.associates_title"));
        JsonObject data = ClientScreenData.parse(json);
        pos = ClientScreenData.longValue(data, "pos", 0L);
        ownerName = ClientScreenData.string(data, "ownerName", "?");
        linkedTotal = ClientScreenData.integer(data, "linkedTotal", 0);
        maxAssociates = ClientScreenData.integer(data, "maxAssociates", 16);
        List<String> parsedNames = new ArrayList<>();
        JsonArray names = data.has("playerNames")
                ? data.getAsJsonArray("playerNames") : new JsonArray();
        for (JsonElement value : names) {
            if (value.isJsonPrimitive()) parsedNames.add(value.getAsString());
        }
        playerNames = List.copyOf(parsedNames);
        List<AssociateView> parsed = new ArrayList<>();
        JsonArray values = data.has("associates")
                ? data.getAsJsonArray("associates") : new JsonArray();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject associate = value.getAsJsonObject();
            parsed.add(new AssociateView(
                    ClientScreenData.string(associate, "uuid", ""),
                    ClientScreenData.string(associate, "name", "?"),
                    ClientScreenData.integer(associate, "percent", 0)
            ));
        }
        associates = List.copyOf(parsed);
    }

    @Override
    protected void init() {
        layout(390, 310);
        listX = panelLeft + 14;
        listY = panelTop + 55;
        listWidth = panelWidth - 28;

        playerName = new EditBox(
                font, panelLeft + 14, panelTop + 226, panelWidth - 104, 20,
                Component.translatable(
                        "screen.numismatics_treasury.player_shop.linked_player")
        );
        playerName.setMaxLength(64);
        playerName.setHint(Component.translatable(
                "screen.numismatics_treasury.player_shop.player_name_hint"));
        playerName.setResponder(value -> {
            if (!applyingCompletion) updateCompletionMatches(value);
            updateButtons();
        });
        addRenderableWidget(playerName);

        share = new EditBox(
                font, panelLeft + panelWidth - 82, panelTop + 226, 68, 20,
                Component.translatable(
                        "screen.numismatics_treasury.player_shop.linked_share")
        );
        share.setMaxLength(3);
        share.setFilter(value -> value.isEmpty()
                || value.chars().allMatch(Character::isDigit));
        share.setHint(Component.literal("%"));
        share.setResponder(ignored -> updateButtons());
        addRenderableWidget(share);

        applyButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.player_shop.apply_associate"),
                        ignored -> apply())
                .green()
                .bounds(panelLeft + 14, panelTop + 273, 150, 20)
                .build());
        removeButton = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.player_shop.remove_associate"),
                        ignored -> remove())
                .confirmation(Component.translatable("screen.numismatics_treasury.confirm"))
                .bounds(panelLeft + 170, panelTop + 273, 112, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.player_shop.back"),
                        ignored -> back())
                .bounds(panelLeft + 288, panelTop + 273, 88, 20)
                .build());
        updateButtons();
    }

    private int parsedShare() {
        try {
            return share == null || share.getValue().isBlank()
                    ? 0 : Integer.parseInt(share.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB
                && playerName != null
                && playerName.isFocused()
                && completePlayerName((modifiers & GLFW.GLFW_MOD_SHIFT) != 0)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean completePlayerName(boolean backwards) {
        if (completionMatches.isEmpty()) {
            updateCompletionMatches(playerName.getValue());
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
        playerName.setValue(completionMatches.get(completionIndex));
        playerName.setSuggestion(null);
        applyingCompletion = false;
        updateButtons();
        return true;
    }

    private void updateCompletionMatches(String value) {
        String prefix = value.toLowerCase(Locale.ROOT);
        completionMatches = playerNames.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
        completionIndex = -1;
        if (value.isEmpty() || completionMatches.isEmpty()) {
            playerName.setSuggestion(null);
            return;
        }
        String first = completionMatches.getFirst();
        playerName.setSuggestion(first.length() > value.length()
                ? first.substring(value.length()) : null);
    }

    private void updateButtons() {
        if (applyButton != null) {
            int value = parsedShare();
            applyButton.active = playerName != null
                    && !playerName.getValue().isBlank()
                    && value >= 1 && value <= 100;
        }
        if (removeButton != null) {
            removeButton.active = selectedIndex >= 0 && selectedIndex < associates.size();
        }
    }

    private void apply() {
        if (applyButton == null || !applyButton.active) return;
        JsonObject data = positionData();
        data.addProperty("linkedPlayer", playerName.getValue().strip());
        data.addProperty("linkedPercent", parsedShare());
        TreasuryNetwork.sendAction("player_shop_associate_save", data);
    }

    private void remove() {
        AssociateView selected = selected();
        if (selected == null) return;
        JsonObject data = positionData();
        data.addProperty("linkedUuid", selected.uuid());
        TreasuryNetwork.sendAction("player_shop_associate_remove", data);
    }

    private void back() {
        if (leaving) return;
        leaving = true;
        TreasuryNetwork.sendAction("player_shop_associate_back", positionData());
        if (minecraft != null) minecraft.setScreen(null);
    }

    private JsonObject positionData() {
        JsonObject data = new JsonObject();
        data.addProperty("pos", pos);
        return data;
    }

    private AssociateView selected() {
        return selectedIndex >= 0 && selectedIndex < associates.size()
                ? associates.get(selectedIndex) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || mouseX < listX || mouseX >= listX + listWidth
                || mouseY < listY || mouseY >= listY + VISIBLE_ROWS * ROW_HEIGHT) {
            return false;
        }
        int index = scrollRow + (int) ((mouseY - listY) / ROW_HEIGHT);
        if (index < 0 || index >= associates.size()) return false;
        selectedIndex = index;
        AssociateView selected = associates.get(index);
        playerName.setValue(selected.name());
        share.setValue(Integer.toString(selected.percent()));
        updateButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (mouseX >= listX && mouseX < listX + listWidth
                && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
            int max = Math.max(0, associates.size() - VISIBLE_ROWS);
            scrollRow = Math.max(0, Math.min(max,
                    scrollRow + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.player_shop.associates_summary",
                        linkedTotal, 100 - linkedTotal),
                panelLeft + 14, panelTop + 37, MUTED, false);
        renderAssociates(graphics);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.player_shop.linked_player"),
                panelLeft + 14, panelTop + 215, TEXT, false);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.player_shop.linked_share"),
                panelLeft + panelWidth - 82, panelTop + 215, TEXT, false);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.player_shop.associates_help",
                        maxAssociates, ownerName),
                panelLeft + 14, panelTop + 252, MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderAssociates(GuiGraphics graphics) {
        int height = VISIBLE_ROWS * ROW_HEIGHT;
        graphics.fill(listX, listY, listX + listWidth, listY + height, PANEL_ALT);
        if (associates.isEmpty()) {
            Component empty = Component.translatable(
                    "screen.numismatics_treasury.player_shop.no_associates");
            graphics.drawCenteredString(font, empty,
                    listX + listWidth / 2, listY + height / 2 - 4, MUTED);
            return;
        }
        int end = Math.min(associates.size(), scrollRow + VISIBLE_ROWS);
        for (int index = scrollRow; index < end; index++) {
            int rowY = listY + (index - scrollRow) * ROW_HEIGHT;
            if (index == selectedIndex) {
                graphics.fill(listX + 1, rowY + 1,
                        listX + listWidth - 9, rowY + ROW_HEIGHT - 1, 0xFF4A351C);
                outline(graphics, listX, rowY,
                        listWidth - 8, ROW_HEIGHT, ACCENT);
            } else if ((index & 1) != 0) {
                graphics.fill(listX + 1, rowY + 1,
                        listX + listWidth - 9, rowY + ROW_HEIGHT - 1, 0x33202020);
            }
            AssociateView associate = associates.get(index);
            graphics.drawString(font,
                    font.plainSubstrByWidth(associate.name(), listWidth - 74),
                    listX + 7, rowY + 8, TEXT, false);
            String percentage = associate.percent() + "%";
            graphics.drawString(font, percentage,
                    listX + listWidth - 17 - font.width(percentage),
                    rowY + 8, ACCENT, false);
        }
        if (associates.size() > VISIBLE_ROWS) {
            int trackX = listX + listWidth - 6;
            graphics.fill(trackX, listY + 2, trackX + 3, listY + height - 2, SEPARATOR);
            int thumbHeight = Math.max(16, (height - 4) * VISIBLE_ROWS / associates.size());
            int maxScroll = associates.size() - VISIBLE_ROWS;
            int travel = height - 4 - thumbHeight;
            int thumbY = listY + 2 + (maxScroll == 0 ? 0 : travel * scrollRow / maxScroll);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, ACCENT);
        }
    }

    @Override
    public void onClose() {
        back();
    }

    private record AssociateView(String uuid, String name, int percent) {
    }
}
