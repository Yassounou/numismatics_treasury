package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import dev.yassou.numismaticstreasury.client.gui.TreasuryButton;
import dev.yassou.numismaticstreasury.client.gui.InventoryPicker;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuctionHouseScreen extends TreasuryScreen {
    private static final int LIST_ROW_HEIGHT = 38;

    private String json;
    private int balance;
    private int claimCount;
    private double commission;
    private boolean operator;
    private List<Integer> durations = List.of(1, 6, 12, 24, 48);
    private List<ListingView> listings = List.of();
    private Tab tab = Tab.MARKET;
    private Filter filter = Filter.ALL;
    private String query = "";
    private String selectedId = "";
    private int scrollRow;
    private EditBox searchBox;
    private EditBox amountBox;
    private TreasuryButton primaryAction;
    private String createType = "FIXED";
    private int durationIndex;
    private String bidInput = "";
    private String sellPriceInput = "";
    private long terminalPos = Long.MIN_VALUE;
    private InventoryPicker inventoryPicker;
    private int selectedInventorySlot = -1;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;

    public AuctionHouseScreen(String json) {
        super(Component.translatable("screen.numismatics_treasury.auction.title"));
        read(json);
    }

    public void refresh(String refreshedJson) {
        captureInputs();
        read(refreshedJson);
        rebuild();
    }

    private void read(String refreshedJson) {
        json = refreshedJson;
        JsonObject data = ClientScreenData.parse(refreshedJson);
        balance = ClientScreenData.integer(data, "balance", 0);
        terminalPos = ClientScreenData.longValue(data, "pos", terminalPos);
        claimCount = ClientScreenData.integer(data, "claimCount", 0);
        commission = ClientScreenData.decimal(data, "commissionPercent", 0.0D);
        operator = ClientScreenData.bool(data, "operator", false);
        Tab requestedTab = null;
        if (data.has("openTab")) {
            try {
                requestedTab = Tab.valueOf(data.get("openTab").getAsString());
                tab = requestedTab;
                scrollRow = 0;
                if (requestedTab == Tab.MINE) {
                    selectedInventorySlot = -1;
                    sellPriceInput = "";
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore an unknown server hint and keep the currently open tab.
            }
        }
        List<Integer> parsedDurations = new ArrayList<>();
        JsonArray durationValues = data.has("durations")
                ? data.getAsJsonArray("durations") : new JsonArray();
        for (JsonElement value : durationValues) parsedDurations.add(value.getAsInt());
        if (!parsedDurations.isEmpty()) durations = List.copyOf(parsedDurations);
        durationIndex = Math.min(durationIndex, durations.size() - 1);

        List<ListingView> parsedListings = new ArrayList<>();
        JsonArray values = data.has("listings")
                ? data.getAsJsonArray("listings") : new JsonArray();
        for (JsonElement value : values) {
            if (value.isJsonObject()) parsedListings.add(ListingView.from(value.getAsJsonObject()));
        }
        listings = List.copyOf(parsedListings);
        if (requestedTab == Tab.MINE) {
            selectedId = listings.stream()
                    .filter(ListingView::mine)
                    .map(ListingView::id)
                    .findFirst()
                    .orElse("");
        } else if (selectedId.isBlank()
                || listings.stream().noneMatch(v -> v.id.equals(selectedId))) {
            selectedId = listings.isEmpty() ? "" : listings.getFirst().id;
        }
    }

    @Override
    protected void init() {
        layout(490, 292);
        int contentX = panelLeft + 8;
        int tabY = panelTop + 36;
        int gap = 4;
        int tabWidth = (panelWidth - 16 - gap * 3) / 4;
        for (int index = 0; index < Tab.values().length; index++) {
            Tab value = Tab.values()[index];
            addRenderableWidget(TreasuryButton.builder(
                            Component.translatable(value.key),
                            ignored -> switchTab(value)
                    ).selected(tab == value)
                    .bounds(contentX + index * (tabWidth + gap), tabY, tabWidth, 20)
                    .build());
        }
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> onClose())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());

        switch (tab) {
            case MARKET, MINE -> buildBrowser();
            case SELL -> buildSeller();
            case CLAIMS -> buildClaims();
        }
    }

    private void buildBrowser() {
        int contentX = panelLeft + 8;
        int top = panelTop + 63;
        searchBox = new EditBox(
                font,
                contentX,
                top,
                174,
                20,
                Component.translatable("screen.numismatics_treasury.auction.search")
        );
        searchBox.setHint(Component.translatable(
                "screen.numismatics_treasury.auction.search_hint"));
        searchBox.setMaxLength(60);
        searchBox.setValue(query);
        searchBox.setResponder(value -> query = value);
        addRenderableWidget(searchBox);
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(filter.key),
                        button -> {
                            filter = filter.next();
                            button.setMessage(Component.translatable(filter.key));
                            scrollRow = 0;
                        }
                ).bounds(contentX + 180, top, 86, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.auction.refresh"),
                        ignored -> TreasuryNetwork.sendAction("auction_refresh", terminalData())
                ).bounds(contentX + 272, top, panelWidth - 288, 20)
                .build());

        listX = contentX;
        listY = top + 27;
        listWidth = 270;
        listHeight = panelHeight - 100;
        buildSelectedActions();
    }

    private void buildSelectedActions() {
        ListingView selected = selected();
        if (selected == null) return;
        int x = listX + listWidth + 8;
        int width = panelLeft + panelWidth - 8 - x;
        int bottom = panelTop + panelHeight - 27;
        if (selected.mine || operator) {
            addRenderableWidget(TreasuryButton.builder(
                            Component.translatable("screen.numismatics_treasury.auction.cancel"),
                            ignored -> listingAction("auction_cancel", selected, 0)
                    ).confirmation(Component.translatable(
                            "screen.numismatics_treasury.auction.confirm_cancel"))
                    .bounds(x, bottom, width, 20)
                    .build());
            bottom -= 25;
        }
        if (selected.mine) return;
        if (selected.type.equals("FIXED")) {
            primaryAction = addRenderableWidget(TreasuryButton.builder(
                            Component.translatable(
                                    "screen.numismatics_treasury.auction.buy",
                                    selected.price),
                            ignored -> listingAction("auction_buy", selected, 0)
                    ).green()
                    .confirmation(Component.translatable(
                            "screen.numismatics_treasury.auction.confirm_buy"))
                    .bounds(x, bottom, width, 20)
                    .build());
            primaryAction.active = balance >= selected.price;
        } else {
            amountBox = new EditBox(
                    font,
                    x,
                    bottom - 25,
                    width,
                    20,
                    Component.translatable("screen.numismatics_treasury.auction.bid_amount")
            );
            amountBox.setHint(Component.translatable(
                    "screen.numismatics_treasury.auction.minimum",
                    selected.minimumBid));
            amountBox.setMaxLength(10);
            amountBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            amountBox.setValue(bidInput);
            amountBox.setResponder(value -> {
                bidInput = value;
                updateBidState();
            });
            addRenderableWidget(amountBox);
            primaryAction = addRenderableWidget(TreasuryButton.builder(
                            Component.translatable("screen.numismatics_treasury.auction.bid"),
                            ignored -> listingAction("auction_bid", selected, parsedAmount())
                    ).green()
                    .confirmation(Component.translatable(
                            "screen.numismatics_treasury.auction.confirm_bid"))
                    .bounds(x, bottom, width, 20)
                    .build());
            updateBidState();
        }
    }

    private void buildSeller() {
        int x = panelLeft + 12;
        int rightX = x + InventoryPicker.WIDTH + 14;
        int rightWidth = panelLeft + panelWidth - 12 - rightX;
        inventoryPicker = new InventoryPicker(
                x,
                panelTop + 82,
                selectedInventorySlot
        );
        amountBox = new EditBox(
                font,
                rightX,
                panelTop + 139,
                rightWidth,
                20,
                Component.translatable("screen.numismatics_treasury.auction.price")
        );
        amountBox.setHint(Component.translatable(
                "screen.numismatics_treasury.auction.price_hint"));
        amountBox.setMaxLength(10);
        amountBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        amountBox.setValue(sellPriceInput);
        amountBox.setResponder(value -> {
            sellPriceInput = value;
            updateCreateState();
        });
        addRenderableWidget(amountBox);
        addRenderableWidget(TreasuryButton.builder(
                        typeLabel(),
                        button -> {
                            createType = createType.equals("FIXED") ? "AUCTION" : "FIXED";
                            button.setMessage(typeLabel());
                        }
                ).bounds(rightX, panelTop + 168, 105, 20)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        durationLabel(),
                        button -> {
                            durationIndex = (durationIndex + 1) % durations.size();
                            button.setMessage(durationLabel());
                        }
                ).bounds(rightX + 111, panelTop + 168, 87, 20)
                .build());
        primaryAction = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable("screen.numismatics_treasury.auction.publish"),
                        ignored -> createListing()
                ).green()
                .confirmation(Component.translatable(
                        "screen.numismatics_treasury.auction.confirm_publish"))
                .bounds(rightX + 204, panelTop + 168, rightWidth - 204, 20)
                .build());
        updateCreateState();
    }

    private void buildClaims() {
        int x = panelLeft + 60;
        int y = panelTop + panelHeight - 70;
        primaryAction = addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.auction.claim_all",
                                claimCount),
                        ignored -> TreasuryNetwork.sendAction("auction_claim", terminalData())
                ).green()
                .bounds(x, y, panelWidth - 120, 20)
                .build());
        primaryAction.active = claimCount > 0;
    }

    private void switchTab(Tab value) {
        captureInputs();
        tab = value;
        scrollRow = 0;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        searchBox = null;
        amountBox = null;
        primaryAction = null;
        inventoryPicker = null;
        init();
    }

    private void captureInputs() {
        if (searchBox != null) query = searchBox.getValue();
        if (amountBox != null) {
            if (tab == Tab.SELL) sellPriceInput = amountBox.getValue();
            else bidInput = amountBox.getValue();
        }
    }

    private void updateBidState() {
        if (primaryAction == null) return;
        ListingView selected = selected();
        int amount = parsedAmount();
        primaryAction.resetConfirmation(Component.translatable(
                "screen.numismatics_treasury.auction.bid"));
        primaryAction.active = selected != null
                && amount >= selected.minimumBid
                && amount <= balance;
    }

    private void updateCreateState() {
        if (primaryAction == null) return;
        primaryAction.resetConfirmation(Component.translatable(
                "screen.numismatics_treasury.auction.publish"));
        primaryAction.active = !selectedSellStack().isEmpty() && parsedAmount() > 0;
    }

    private int parsedAmount() {
        try {
            return amountBox == null || amountBox.getValue().isBlank()
                    ? 0 : Integer.parseInt(amountBox.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void createListing() {
        if (primaryAction == null || !primaryAction.active) return;
        JsonObject data = new JsonObject();
        data.addProperty("type", createType);
        data.addProperty("price", parsedAmount());
        data.addProperty("durationHours", durations.get(durationIndex));
        data.addProperty("pos", terminalPos);
        data.addProperty("inventorySlot", selectedInventorySlot);
        TreasuryNetwork.sendAction("auction_create", data);
    }

    private void listingAction(String action, ListingView listing, int amount) {
        JsonObject data = new JsonObject();
        data.addProperty("id", listing.id);
        data.addProperty("revision", listing.revision);
        data.addProperty("pos", terminalPos);
        if (amount > 0) data.addProperty("amount", amount);
        TreasuryNetwork.sendAction(action, data);
    }

    private JsonObject terminalData() {
        JsonObject data = new JsonObject();
        data.addProperty("pos", terminalPos);
        return data;
    }

    private Component typeLabel() {
        return Component.translatable(createType.equals("FIXED")
                ? "screen.numismatics_treasury.auction.type_fixed"
                : "screen.numismatics_treasury.auction.type_auction");
    }

    private Component durationLabel() {
        return Component.translatable(
                "screen.numismatics_treasury.auction.duration",
                durations.get(durationIndex)
        );
    }

    private List<ListingView> filtered() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return listings.stream()
                .filter(value -> tab != Tab.MINE || value.mine)
                .filter(value -> filter.accepts(value.type))
                .filter(value -> needle.isEmpty()
                        || value.itemName.toLowerCase(Locale.ROOT).contains(needle)
                        || value.sellerName.toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private ListingView selected() {
        return filtered().stream()
                .filter(value -> value.id.equals(selectedId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (tab == Tab.SELL && inventoryPicker != null
                && minecraft != null && minecraft.player != null
                && inventoryPicker.mouseClicked(minecraft.player, mouseX, mouseY, button)) {
            selectedInventorySlot = inventoryPicker.selectedSlot();
            updateCreateState();
            return true;
        }
        if ((tab != Tab.MARKET && tab != Tab.MINE) || button != 0) return false;
        if (mouseX < listX || mouseX >= listX + listWidth
                || mouseY < listY || mouseY >= listY + listHeight) return false;
        int index = scrollRow + (int) ((mouseY - listY) / LIST_ROW_HEIGHT);
        List<ListingView> visible = filtered();
        if (index >= 0 && index < visible.size()) {
            selectedId = visible.get(index).id;
            bidInput = "";
            rebuild();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if ((tab == Tab.MARKET || tab == Tab.MINE)
                && mouseX >= listX && mouseX < listX + listWidth
                && mouseY >= listY && mouseY < listY + listHeight) {
            int visibleRows = Math.max(1, listHeight / LIST_ROW_HEIGHT);
            int max = Math.max(0, filtered().size() - visibleRows);
            scrollRow = Math.max(0, Math.min(max, scrollRow + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics);
        MoneyDisplay.renderBadge(
                graphics,
                font,
                panelLeft + panelWidth - MoneyDisplay.badgeWidth(font, balance) - 31,
                panelTop + 6,
                balance
        );
        switch (tab) {
            case MARKET, MINE -> renderBrowser(graphics, mouseX, mouseY);
            case SELL -> renderSeller(graphics, mouseX, mouseY);
            case CLAIMS -> renderClaims(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tab == Tab.SELL && inventoryPicker != null
                && minecraft != null && minecraft.player != null) {
            ItemStack hovered = inventoryPicker.hoveredStack(minecraft.player, mouseX, mouseY);
            if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void renderBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ListingView> visible = filtered();
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0x88101010);
        outline(graphics, listX, listY, listWidth, listHeight, SEPARATOR);
        graphics.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listY + listHeight - 1);
        int rows = Math.min(Math.max(0, visible.size() - scrollRow),
                Math.max(0, (listHeight + LIST_ROW_HEIGHT - 1) / LIST_ROW_HEIGHT));
        for (int offset = 0; offset < rows; offset++) {
            ListingView listing = visible.get(scrollRow + offset);
            int rowX = listX + 4;
            int rowY = listY + 4 + offset * LIST_ROW_HEIGHT;
            int rowWidth = listWidth - 8;
            boolean selected = listing.id.equals(selectedId);
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth
                    && mouseY >= rowY && mouseY < rowY + LIST_ROW_HEIGHT - 3;
            graphics.fill(rowX, rowY, rowX + rowWidth, rowY + LIST_ROW_HEIGHT - 3,
                    selected ? 0xFF4A3920 : hovered ? 0xFF393939 : 0xFF292929);
            outline(graphics, rowX, rowY, rowWidth, LIST_ROW_HEIGHT - 3,
                    selected ? ACCENT : hovered ? 0xFF777777 : SEPARATOR);
            graphics.renderItem(listing.item, rowX + 5, rowY + 9);
            graphics.drawString(font, font.plainSubstrByWidth(listing.itemName, rowWidth - 100),
                    rowX + 27, rowY + 6, TEXT, false);
            int shownPrice = listing.type.equals("AUCTION") && listing.currentBid > 0
                    ? listing.currentBid : listing.price;
            MoneyDisplay.renderIcon(graphics, rowX + 27, rowY + 21);
            graphics.drawString(font, Integer.toString(shownPrice), rowX + 44, rowY + 21, ACCENT, false);
            Component time = remaining(listing.expiresAt);
            graphics.drawString(font, time, rowX + rowWidth - font.width(time) - 5,
                    rowY + 21, MUTED, false);
        }
        graphics.disableScissor();
        if (visible.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                    tab == Tab.MINE
                            ? "screen.numismatics_treasury.auction.empty_mine"
                            : "screen.numismatics_treasury.auction.empty_market"),
                    listX + listWidth / 2, listY + listHeight / 2, MUTED);
        }

        int detailX = listX + listWidth + 8;
        int detailWidth = panelLeft + panelWidth - 8 - detailX;
        graphics.fill(detailX, listY, detailX + detailWidth, listY + listHeight, PANEL_ALT);
        outline(graphics, detailX, listY, detailWidth, listHeight, SEPARATOR);
        ListingView selected = selected();
        if (selected == null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.numismatics_treasury.auction.select_listing"),
                    detailX + detailWidth / 2, listY + 55, MUTED);
            return;
        }
        graphics.renderItem(selected.item, detailX + 9, listY + 12);
        graphics.drawString(font, font.plainSubstrByWidth(selected.itemName, detailWidth - 38),
                detailX + 32, listY + 11, TEXT, false);
        graphics.drawString(font, "×" + selected.item.getCount(), detailX + 32, listY + 24, MUTED, false);
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.numismatics_treasury.auction.seller",
                        selected.sellerName),
                detailX + 9,
                listY + 47,
                MUTED,
                false
        );
        graphics.drawString(font, Component.translatable(selected.type.equals("FIXED")
                        ? "screen.numismatics_treasury.auction.fixed"
                        : "screen.numismatics_treasury.auction.auction"),
                detailX + 9, listY + 62, ACCENT, false);
        int price = selected.type.equals("AUCTION") && selected.currentBid > 0
                ? selected.currentBid : selected.price;
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.auction.shown_price", price),
                detailX + 9,
                listY + 79,
                TEXT,
                false
        );
        if (selected.type.equals("AUCTION")) {
            graphics.drawString(font, Component.translatable(
                            "screen.numismatics_treasury.auction.minimum",
                            selected.minimumBid),
                    detailX + 9, listY + 94, TEXT, false);
            Component leader = selected.leadingBidder.isBlank()
                    ? Component.translatable("screen.numismatics_treasury.auction.no_bid")
                    : Component.translatable(
                            "screen.numismatics_treasury.auction.leading_bidder",
                            selected.leadingBidder);
            graphics.drawString(font, font.plainSubstrByWidth(leader.getString(), detailWidth - 18),
                    detailX + 9, listY + 109, MUTED, false);
        }
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.auction.expires_in",
                        remaining(selected.expiresAt)),
                detailX + 9, listY + 126, MUTED, false);
    }

    private void renderSeller(GuiGraphics graphics, int mouseX, int mouseY) {
        int inventoryX = panelLeft + 12;
        int rightX = inventoryX + InventoryPicker.WIDTH + 14;
        int rightWidth = panelLeft + panelWidth - 12 - rightX;
        ItemStack selected = selectedSellStack();
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.inventory"),
                inventoryX,
                panelTop + 68,
                TEXT,
                false
        );
        if (inventoryPicker != null && minecraft != null && minecraft.player != null) {
            inventoryPicker.render(graphics, font, minecraft.player, mouseX, mouseY);
        }
        int cardY = panelTop + 68;
        graphics.fill(rightX, cardY, rightX + rightWidth, cardY + 51, PANEL_ALT);
        outline(graphics, rightX, cardY, rightWidth, 51, SEPARATOR);
        if (!selected.isEmpty()) graphics.renderItem(selected, rightX + 10, cardY + 10);
        graphics.drawString(
                font,
                selected.isEmpty()
                        ? Component.translatable("screen.numismatics_treasury.no_item_selected")
                        : selected.getHoverName(),
                rightX + 34,
                cardY + 10,
                selected.isEmpty() ? MUTED : TEXT,
                false
        );
        graphics.drawString(
                font,
                selected.isEmpty()
                        ? Component.translatable("screen.numismatics_treasury.auction.pick_item")
                        : Component.translatable(
                                "screen.numismatics_treasury.auction.full_stack",
                                selected.getCount()),
                rightX + 34,
                cardY + 27,
                MUTED,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.numismatics_treasury.auction.price_help"),
                rightX,
                panelTop + 127,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.numismatics_treasury.auction.commission",
                        commission),
                rightX,
                panelTop + 198,
                MUTED,
                false
        );
    }

    private ItemStack selectedSellStack() {
        if (inventoryPicker == null || minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        return inventoryPicker.selectedStack(minecraft.player);
    }

    private void renderClaims(GuiGraphics graphics) {
        int centerX = panelLeft + panelWidth / 2;
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.numismatics_treasury.auction.claims_title"),
                centerX,
                panelTop + 88,
                ACCENT
        );
        graphics.drawCenteredString(font, Component.translatable(
                        claimCount == 0
                                ? "screen.numismatics_treasury.auction.claims_empty"
                                : "screen.numismatics_treasury.auction.claims_count",
                        claimCount),
                centerX, panelTop + 116, claimCount == 0 ? MUTED : TEXT);
        graphics.drawCenteredString(font,
                Component.translatable("screen.numismatics_treasury.auction.claims_help"),
                centerX, panelTop + 134, MUTED);
    }

    private static Component remaining(long expiresAt) {
        long seconds = Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1_000L);
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        if (hours > 0) {
            return Component.translatable(
                    "screen.numismatics_treasury.time.hours_minutes",
                    hours,
                    minutes
            );
        }
        return Component.translatable(
                "screen.numismatics_treasury.time.minutes_seconds",
                minutes,
                seconds % 60L
        );
    }

    private enum Tab {
        MARKET("screen.numismatics_treasury.auction.tab.market"),
        SELL("screen.numismatics_treasury.auction.tab.sell"),
        MINE("screen.numismatics_treasury.auction.tab.mine"),
        CLAIMS("screen.numismatics_treasury.auction.tab.claims");

        private final String key;
        Tab(String key) { this.key = key; }
    }

    private enum Filter {
        ALL("screen.numismatics_treasury.auction.filter.all"),
        FIXED("screen.numismatics_treasury.auction.filter.fixed"),
        AUCTION("screen.numismatics_treasury.auction.filter.auction");
        private final String key;
        Filter(String key) { this.key = key; }
        private Filter next() { return values()[(ordinal() + 1) % values().length]; }
        private boolean accepts(String type) { return this == ALL || name().equals(type); }
    }

    private record ListingView(
            String id,
            String sellerName,
            String type,
            int price,
            int currentBid,
            int minimumBid,
            String leadingBidder,
            long expiresAt,
            long revision,
            boolean mine,
            ItemStack item,
            String itemName
    ) {
        private static ListingView from(JsonObject data) {
            return new ListingView(
                    ClientScreenData.string(data, "id", ""),
                    ClientScreenData.string(data, "sellerName", "?"),
                    ClientScreenData.string(data, "type", "FIXED"),
                    ClientScreenData.integer(data, "price", 0),
                    ClientScreenData.integer(data, "currentBid", 0),
                    ClientScreenData.integer(data, "minimumBid", 0),
                    ClientScreenData.string(data, "leadingBidderName", ""),
                    ClientScreenData.longValue(data, "expiresAt", 0L),
                    ClientScreenData.longValue(data, "revision", 0L),
                    ClientScreenData.bool(data, "mine", false),
                    ClientScreenData.item(data),
                    ClientScreenData.string(
                            data,
                            "itemName",
                            Component.translatable(
                                    "screen.numismatics_treasury.no_item").getString()
                    )
            );
        }
    }
}
