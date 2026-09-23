package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.client.TreasuryClientPreferences;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import dev.yassou.numismaticstreasury.client.gui.TreasuryButton;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class HistoryStatsScreen extends TreasuryScreen {
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int ENTRY_HEIGHT = 31;
    private static final int SHOP_HEIGHT = 43;
    private static final ResourceLocation SETTINGS_ICON = ResourceLocation.fromNamespaceAndPath(
            NumismaticsTreasury.MOD_ID,
            "textures/gui/icons/history_settings.png"
    );

    private final String source;
    private final boolean portable;
    private final long returnPos;
    private final int balance;
    private final boolean operator;
    private final String selectedShop;
    private final TotalsView totals;
    private final List<EntryView> entries;
    private final List<ShopView> shops;
    private final ServerView server;
    private boolean notificationsEnabled;
    private Tab tab = Tab.OVERVIEW;
    private ShopSort shopSort = ShopSort.RECENT;
    private int scroll;
    private int beneficiaryScroll;
    private int selectedShopIndex;
    private boolean draggingScrollbar;
    private boolean draggingBeneficiaryScrollbar;
    private boolean settingsOpen;
    private boolean leaving;
    private ItemStack hoveredItem = ItemStack.EMPTY;
    private int currentMouseX;
    private int currentMouseY;

    public HistoryStatsScreen(String json) {
        super(Component.translatable("screen.numismatics_treasury.history.title"));
        JsonObject data = ClientScreenData.parse(json);
        source = ClientScreenData.string(data, "source", "command");
        portable = ClientScreenData.bool(data, "portable", false);
        returnPos = ClientScreenData.longValue(data, "returnPos", Long.MIN_VALUE);
        balance = ClientScreenData.integer(data, "balance", 0);
        operator = ClientScreenData.bool(data, "operator", false);
        notificationsEnabled = ClientScreenData.bool(
                data, "notificationsEnabled", true);
        selectedShop = ClientScreenData.string(data, "selectedShop", "");
        totals = TotalsView.parse(object(data, "totals"));

        List<EntryView> parsedEntries = new ArrayList<>();
        for (JsonElement value : array(data, "entries")) {
            if (value.isJsonObject()) parsedEntries.add(EntryView.parse(value.getAsJsonObject()));
        }
        entries = List.copyOf(parsedEntries);

        List<ShopView> parsedShops = new ArrayList<>();
        for (JsonElement value : array(data, "shops")) {
            if (value.isJsonObject()) parsedShops.add(ShopView.parse(value.getAsJsonObject()));
        }
        parsedShops.sort(Comparator
                .comparing((ShopView value) -> !value.key.equals(selectedShop))
                .thenComparing(Comparator.comparingLong(ShopView::lastSaleAt).reversed()));
        shops = parsedShops;
        for (int index = 0; index < shops.size(); index++) {
            if (shops.get(index).key.equals(selectedShop)) {
                selectedShopIndex = index;
                break;
            }
        }
        server = data.has("server") ? ServerView.parse(data.getAsJsonObject("server")) : null;
    }

    @Override
    protected void init() {
        layout(500, 320);
        int x = panelLeft + 10;
        int y = panelTop + 36;
        List<Tab> tabs = visibleTabs();
        int gap = 4;
        int width = (panelWidth - 20 - gap * (tabs.size() - 1)) / tabs.size();
        for (int index = 0; index < tabs.size(); index++) {
            Tab value = tabs.get(index);
            addRenderableWidget(TreasuryButton.builder(
                            Component.translatable(value.key),
                            ignored -> switchTab(value))
                    .icon(value.icon)
                    .iconScale(0.75F)
                    .iconYOffset(-1)
                    .iconWithText()
                    .selected(tab == value)
                    .bounds(x + index * (width + gap), y, width, 20)
                    .build());
        }
        addRenderableWidget(TreasuryButton.builder(
                        Component.translatable(
                                "screen.numismatics_treasury.history.settings.title"),
                        ignored -> toggleSettings())
                .icon(SETTINGS_ICON)
                .iconScale(0.75F)
                .iconYOffset(-1)
                .selected(settingsOpen)
                .bounds(panelLeft + panelWidth - 49, panelTop + 5, 20, 18)
                .build());
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> leave())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());
        if (settingsOpen) {
            addRenderableWidget(TreasuryButton.builder(
                            balanceSettingLabel(), ignored -> toggleBalance())
                    .selected(TreasuryClientPreferences.showHistoryBalance())
                    .bounds(panelLeft + panelWidth - 179,
                            panelTop + 91, 157, 20)
                    .build());
            addRenderableWidget(TreasuryButton.builder(
                            notificationSettingLabel(), ignored -> toggleNotifications())
                    .selected(notificationsEnabled)
                    .bounds(panelLeft + panelWidth - 179,
                            panelTop + 117, 157, 20)
                    .build());
        }
        if (tab == Tab.SHOPS && !shops.isEmpty()) {
            addRenderableWidget(TreasuryButton.builder(
                            sortLabel(), ignored -> cycleShopSort())
                    .bounds(panelLeft + 10, panelTop + 65, 190, 20)
                    .build());
        }
    }

    private List<Tab> visibleTabs() {
        List<Tab> values = new ArrayList<>(List.of(Tab.OVERVIEW, Tab.HISTORY, Tab.SHOPS));
        if (operator && server != null) values.add(Tab.SERVER);
        return values;
    }

    private void switchTab(Tab value) {
        tab = value;
        scroll = 0;
        beneficiaryScroll = 0;
        draggingScrollbar = false;
        draggingBeneficiaryScrollbar = false;
        rebuildScreen();
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    private void toggleSettings() {
        settingsOpen = !settingsOpen;
        rebuildScreen();
    }

    private void toggleBalance() {
        TreasuryClientPreferences.setShowHistoryBalance(
                !TreasuryClientPreferences.showHistoryBalance());
        rebuildScreen();
    }

    private void toggleNotifications() {
        notificationsEnabled = !notificationsEnabled;
        JsonObject data = new JsonObject();
        data.addProperty("enabled", notificationsEnabled);
        TreasuryNetwork.sendAction("history_notifications", data);
        rebuildScreen();
    }

    private void cycleShopSort() {
        String selectedKey = shops.isEmpty()
                ? "" : shops.get(Math.min(selectedShopIndex, shops.size() - 1)).key;
        shopSort = shopSort.next();
        shops.sort(shopComparator());
        selectedShopIndex = 0;
        for (int index = 0; index < shops.size(); index++) {
            if (shops.get(index).key.equals(selectedKey)) {
                selectedShopIndex = index;
                break;
            }
        }
        scroll = Math.max(0, Math.min(selectedShopIndex, maximumScroll()));
        beneficiaryScroll = 0;
        rebuildScreen();
    }

    private Comparator<ShopView> shopComparator() {
        return switch (shopSort) {
            case RECENT -> Comparator.comparingLong(ShopView::lastSaleAt).reversed();
            case SALES -> Comparator.comparingLong(ShopView::sales).reversed();
            case ITEMS -> Comparator.comparingLong(ShopView::itemsSold).reversed();
            case REVENUE -> Comparator.comparingLong(ShopView::grossRevenue).reversed();
            case BEST_SALE -> Comparator.comparingLong(ShopView::bestSale).reversed();
        };
    }

    private Component sortLabel() {
        return Component.translatable(
                "screen.numismatics_treasury.history.sort",
                Component.translatable(shopSort.key)
        );
    }

    private Component balanceSettingLabel() {
        return Component.translatable(
                "screen.numismatics_treasury.history.settings.show_balance",
                Component.translatable(TreasuryClientPreferences.showHistoryBalance()
                        ? "screen.numismatics_treasury.history.settings.on"
                        : "screen.numismatics_treasury.history.settings.off")
        );
    }

    private Component notificationSettingLabel() {
        return Component.translatable(
                "screen.numismatics_treasury.history.settings.show_notifications",
                Component.translatable(notificationsEnabled
                        ? "screen.numismatics_treasury.history.settings.on"
                        : "screen.numismatics_treasury.history.settings.off")
        );
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        ScrollbarLayout beneficiary = beneficiaryScrollbarLayout();
        if (beneficiary != null
                && mouseX >= panelLeft + 210
                && mouseX < panelLeft + panelWidth - 10
                && mouseY >= beneficiary.y
                && mouseY < beneficiary.y + beneficiary.height) {
            int maximum = maximumBeneficiaryScroll();
            beneficiaryScroll = Math.max(0, Math.min(maximum,
                    beneficiaryScroll + (scrollY < 0 ? 1 : -1)));
            return maximum > 0 || super.mouseScrolled(
                    mouseX, mouseY, scrollX, scrollY);
        }
        if (mouseX < panelLeft + 10 || mouseX >= panelLeft + panelWidth - 10
                || mouseY < panelTop + 64 || mouseY >= panelTop + panelHeight - 10) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int maximum = maximumScroll();
        scroll = Math.max(0, Math.min(maximum, scroll + (scrollY < 0 ? 1 : -1)));
        return maximum > 0 || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        ScrollbarLayout beneficiary = beneficiaryScrollbarLayout();
        if (beneficiary != null && beneficiary.contains(mouseX, mouseY)) {
            draggingBeneficiaryScrollbar = maximumBeneficiaryScroll() > 0;
            updateBeneficiaryScrollFromMouse(mouseY, beneficiary);
            return true;
        }
        ScrollbarLayout scrollbar = scrollbarLayout();
        if (scrollbar != null && scrollbar.contains(mouseX, mouseY)) {
            draggingScrollbar = maximumScroll() > 0;
            updateScrollFromMouse(mouseY, scrollbar);
            return true;
        }
        if (tab != Tab.SHOPS) return false;
        int x = panelLeft + 10;
        int y = panelTop + 90;
        int listWidth = 190;
        if (mouseX < x || mouseX >= x + listWidth
                || mouseY < y || mouseY >= y + SHOP_HEIGHT * shopRows()) {
            return false;
        }
        int index = scroll + (int) ((mouseY - y) / SHOP_HEIGHT);
        if (index < 0 || index >= shops.size()) return false;
        selectedShopIndex = index;
        beneficiaryScroll = 0;
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (draggingBeneficiaryScrollbar && button == 0) {
            ScrollbarLayout scrollbar = beneficiaryScrollbarLayout();
            if (scrollbar != null) {
                updateBeneficiaryScrollFromMouse(mouseY, scrollbar);
            }
            return true;
        }
        if (draggingScrollbar && button == 0) {
            ScrollbarLayout scrollbar = scrollbarLayout();
            if (scrollbar != null) updateScrollFromMouse(mouseY, scrollbar);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = draggingScrollbar || draggingBeneficiaryScrollbar;
        if (button == 0) {
            draggingScrollbar = false;
            draggingBeneficiaryScrollbar = false;
        }
        return wasDragging || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredItem = ItemStack.EMPTY;
        currentMouseX = mouseX;
        currentMouseY = mouseY;
        renderPanel(graphics);
        if (TreasuryClientPreferences.showHistoryBalance()) {
            MoneyDisplay.renderBadge(
                    graphics,
                    font,
                    panelLeft + panelWidth - MoneyDisplay.badgeWidth(font, balance) - 58,
                    panelTop + 6,
                    balance
            );
        }
        scroll = Math.max(0, Math.min(scroll, maximumScroll()));
        switch (tab) {
            case OVERVIEW -> renderOverview(graphics);
            case HISTORY -> renderHistory(graphics);
            case SHOPS -> renderShops(graphics);
            case SERVER -> renderServer(graphics);
        }
        if (settingsOpen) renderSettings(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hoveredItem.isEmpty()) {
            graphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }

    private void renderSettings(GuiGraphics graphics) {
        int x = panelLeft + panelWidth - 187;
        int y = panelTop + 62;
        int width = 173;
        int height = 84;
        MoneyDisplay.fillRounded(graphics, x, y, width, height, 0xFC171717);
        MoneyDisplay.outline(graphics, x, y, width, height, ACCENT);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.history.settings.title"),
                x + 10, y + 10, ACCENT, false);
    }

    private void renderOverview(GuiGraphics graphics) {
        int x = panelLeft + 10;
        int y = panelTop + 64;
        int gap = 6;
        int width = (panelWidth - 20 - gap * 3) / 4;
        card(graphics, x, y, width,
                "screen.numismatics_treasury.history.earned", totals.earned, SUCCESS);
        card(graphics, x + (width + gap), y, width,
                "screen.numismatics_treasury.history.spent", totals.spent, ERROR);
        card(graphics, x + (width + gap) * 2, y, width,
                "screen.numismatics_treasury.history.sent", totals.sent, TEXT);
        card(graphics, x + (width + gap) * 3, y, width,
                "screen.numismatics_treasury.history.received", totals.received, ACCENT);

        graphics.drawString(font,
                Component.translatable("screen.numismatics_treasury.history.recent"),
                x, panelTop + 121, ACCENT, false);
        if (entries.isEmpty()) {
            empty(graphics, panelTop + 180,
                    "screen.numismatics_treasury.history.empty");
            return;
        }
        int end = Math.min(entries.size(), scroll + overviewRows());
        for (int index = scroll; index < end; index++) {
            renderEntry(graphics, entries.get(index),
                    x, panelTop + 137 + (index - scroll) * ENTRY_HEIGHT);
        }
        scrollbar(graphics);
    }

    private void renderHistory(GuiGraphics graphics) {
        int x = panelLeft + 10;
        if (entries.isEmpty()) {
            empty(graphics, panelTop + 174,
                    "screen.numismatics_treasury.history.empty");
            return;
        }
        int end = Math.min(entries.size(), scroll + historyRows());
        for (int index = scroll; index < end; index++) {
            renderEntry(graphics, entries.get(index),
                    x, panelTop + 66 + (index - scroll) * ENTRY_HEIGHT);
        }
        scrollbar(graphics);
    }

    private void renderEntry(GuiGraphics graphics, EntryView entry, int x, int y) {
        int width = panelWidth - 20;
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT - 2, PANEL_ALT);
        ItemStack icon = entry.item.isEmpty()
                ? item(entry.itemId) : entry.item.copy();
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 6, y + 6);
            offerTooltip(icon, x + 6, y + 6);
        }
        int textX = x + (icon.isEmpty() ? 8 : 28);
        Component label = Component.translatable(
                "screen.numismatics_treasury.history.entry."
                        + entry.type.toLowerCase(Locale.ROOT));
        graphics.drawString(font, label, textX, y + 5, TEXT, false);
        String detail = entryDetail(entry);
        graphics.drawString(font,
                font.plainSubstrByWidth(detail, width - 150),
                textX, y + 17, MUTED, false);
        String date = DATE.format(Instant.ofEpochMilli(entry.timestamp));
        graphics.drawString(font, date,
                x + width - 8 - font.width(date), y + 5, MUTED, false);
        if (entry.amount != 0L) {
            String amount = signed(entry.amount);
            graphics.drawString(font, amount,
                    x + width - 8 - font.width(amount), y + 17,
                    entry.amount > 0 ? SUCCESS : ERROR, false);
        }
    }

    private String entryDetail(EntryView entry) {
        List<String> parts = new ArrayList<>();
        if (!entry.itemName.isBlank()) {
            parts.add((entry.quantity > 0 ? "×" + entry.quantity + " " : "")
                    + entry.itemName);
        }
        if (!entry.counterparty.isBlank()) parts.add(entry.counterparty);
        if (!entry.detail.isBlank()) parts.add(entry.detail);
        return String.join(" · ", parts);
    }

    private void renderShops(GuiGraphics graphics) {
        int x = panelLeft + 10;
        if (shops.isEmpty()) {
            empty(graphics, panelTop + 174,
                    "screen.numismatics_treasury.history.no_shops");
            return;
        }
        int listWidth = 190;
        int end = Math.min(shops.size(), scroll + shopRows());
        for (int index = scroll; index < end; index++) {
            ShopView shop = shops.get(index);
            int y = panelTop + 90 + (index - scroll) * SHOP_HEIGHT;
            graphics.fill(x, y, x + listWidth, y + SHOP_HEIGHT - 3, PANEL_ALT);
            if (index == selectedShopIndex) {
                outline(graphics, x, y, listWidth, SHOP_HEIGHT - 3, ACCENT);
            }
            ItemStack icon = shop.item.isEmpty()
                    ? item(shop.itemId) : shop.item.copy();
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + 7, y + 8);
                offerTooltip(icon, x + 7, y + 8);
            }
            String title = shop.itemName.isBlank()
                    ? Component.translatable("screen.numismatics_treasury.no_item").getString()
                    : shop.itemName;
            graphics.drawString(font,
                    font.plainSubstrByWidth(title, listWidth - 38),
                    x + 30, y + 6, TEXT, false);
            BlockPos pos = BlockPos.of(shop.pos);
            String location = shortDimension(shop.dimension) + " · "
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            graphics.drawString(font,
                    font.plainSubstrByWidth(location, listWidth - 38),
                    x + 30, y + 18, MUTED, false);
            graphics.drawString(font,
                    Component.translatable(
                            "screen.numismatics_treasury.history.sales_short", shop.sales),
                    x + 30, y + 30, ACCENT, false);
        }
        scrollbar(graphics);
        renderShopDetails(graphics, shops.get(Math.min(selectedShopIndex, shops.size() - 1)));
    }

    private void renderShopDetails(GuiGraphics graphics, ShopView shop) {
        int x = panelLeft + 210;
        int y = panelTop + 90;
        int width = panelWidth - 220;
        boolean compact = compactShopDetails();
        int totalsOffset = compact ? 32 : 42;
        int bestSaleOffset = compact ? 45 : 57;
        int separatorOffset = compact ? 58 : 75;
        int periodOffset = compact ? 65 : 84;
        int periodStep = compact ? 14 : 17;
        int beneficiaryTitleOffset = compact ? 111 : 143;
        int beneficiaryStartOffset = beneficiaryStartOffset();
        graphics.fill(x, y, x + width, panelTop + panelHeight - 10, PANEL_ALT);
        ItemStack icon = shop.item.isEmpty()
                ? item(shop.itemId) : shop.item.copy();
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 9, y + 8);
            offerTooltip(icon, x + 9, y + 8);
        }
        graphics.drawString(font,
                font.plainSubstrByWidth(shop.itemName, width - 44),
                x + 32, y + 7, TEXT, false);
        graphics.drawString(font, shop.ownerName, x + 32, y + 19, MUTED, false);

        String totalsText = Component.translatable(
                "screen.numismatics_treasury.history.shop_totals",
                shop.sales, shop.itemsSold, number(shop.grossRevenue)).getString();
        graphics.drawString(font,
                font.plainSubstrByWidth(totalsText, width - 18),
                x + 9, y + totalsOffset, ACCENT, false);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.history.best_sale",
                        number(shop.bestSale)),
                x + 9, y + bestSaleOffset, TEXT, false);

        graphics.fill(x + 8, y + separatorOffset,
                x + width - 8, y + separatorOffset + 1, SEPARATOR);
        periodLine(graphics, x + 9, y + periodOffset, "24 h", shop.day);
        periodLine(graphics, x + 9, y + periodOffset + periodStep, "7 d", shop.week);
        periodLine(graphics, x + 9, y + periodOffset + periodStep * 2, "30 d", shop.month);

        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.history.beneficiaries"),
                x + 9, y + beneficiaryTitleOffset, ACCENT, false);
        if (shop.beneficiaries.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.numismatics_treasury.history.no_revenue"),
                    x + 9, y + beneficiaryStartOffset, MUTED, false);
            return;
        }
        int maximum = maximumBeneficiaryScroll();
        beneficiaryScroll = Math.max(0, Math.min(beneficiaryScroll, maximum));
        int end = Math.min(
                shop.beneficiaries.size(),
                beneficiaryScroll + beneficiaryRows()
        );
        for (int index = beneficiaryScroll; index < end; index++) {
            BeneficiaryView beneficiary = shop.beneficiaries.get(index);
            int row = index - beneficiaryScroll;
            String amount = number(beneficiary.revenue) + " spurs";
            graphics.drawString(font,
                    font.plainSubstrByWidth(beneficiary.name, width - 100),
                    x + 9, y + beneficiaryStartOffset + row * 14, TEXT, false);
            graphics.drawString(font, amount,
                    x + width - 9 - font.width(amount),
                    y + beneficiaryStartOffset + row * 14, SUCCESS, false);
        }
        beneficiaryScrollbar(graphics);
    }

    private void periodLine(
            GuiGraphics graphics,
            int x,
            int y,
            String label,
            PeriodView period
    ) {
        String value = Component.translatable(
                "screen.numismatics_treasury.history.period_line",
                label,
                period.sales,
                period.items,
                number(period.revenue)).getString();
        graphics.drawString(font, value, x, y, MUTED, false);
    }

    private void renderServer(GuiGraphics graphics) {
        if (server == null) return;
        boolean compact = panelHeight < 290;
        int x = panelLeft + 18;
        int y = panelTop + (compact ? 66 : 74);
        int width = (panelWidth - 42) / 2;
        int height = compact ? 38 : 48;
        int step = compact ? 44 : 56;
        serverCard(graphics, x, y, width, height,
                "screen.numismatics_treasury.history.server.volume", server.totalVolume);
        serverCard(graphics, x + width + 6, y, width, height,
                "screen.numismatics_treasury.history.server.transactions", server.transactions);
        serverCard(graphics, x, y + step, width, height,
                "screen.numismatics_treasury.history.server.transfers", server.transferVolume);
        serverCard(graphics, x + width + 6, y + step, width, height,
                "screen.numismatics_treasury.history.server.player_shops", server.playerShopVolume);
        serverCard(graphics, x, y + step * 2, width, height,
                "screen.numismatics_treasury.history.server.server_shops", server.serverShopVolume);
        serverCard(graphics, x + width + 6, y + step * 2, width, height,
                "screen.numismatics_treasury.history.server.auctions", server.auctionVolume);
        serverCard(graphics, x, y + step * 3, panelWidth - 36,
                compact ? 36 : 38,
                "screen.numismatics_treasury.history.server.commissions",
                server.commissionCollected);
    }

    private void card(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String key,
            long value,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 45, PANEL_ALT);
        graphics.drawString(font, Component.translatable(key), x + 7, y + 7, MUTED, false);
        graphics.drawString(font, number(value), x + 7, y + 24, color, false);
    }

    private void serverCard(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            String key,
            long value
    ) {
        graphics.fill(x, y, x + width, y + height, PANEL_ALT);
        graphics.drawString(font, Component.translatable(key), x + 8, y + 8, MUTED, false);
        graphics.drawString(font, number(value), x + 8, y + 25, ACCENT, false);
    }

    private void empty(GuiGraphics graphics, int y, String key) {
        graphics.drawCenteredString(font, Component.translatable(key),
                panelLeft + panelWidth / 2, y, MUTED);
    }

    private int overviewRows() {
        return Math.max(1, Math.min(5,
                (panelHeight - 157) / ENTRY_HEIGHT));
    }

    private int historyRows() {
        return Math.max(1, Math.min(7,
                (panelHeight - 76) / ENTRY_HEIGHT));
    }

    private int shopRows() {
        return Math.max(1, Math.min(5,
                (panelHeight - 100) / SHOP_HEIGHT));
    }

    private boolean compactShopDetails() {
        return panelHeight < 290;
    }

    private int beneficiaryStartOffset() {
        return compactShopDetails() ? 126 : 160;
    }

    private int beneficiaryRows() {
        int available = panelHeight - 10
                - (90 + beneficiaryStartOffset());
        return Math.max(1, Math.min(4, available / 14));
    }

    private int maximumScroll() {
        return switch (tab) {
            case OVERVIEW -> Math.max(0, entries.size() - overviewRows());
            case HISTORY -> Math.max(0, entries.size() - historyRows());
            case SHOPS -> Math.max(0, shops.size() - shopRows());
            case SERVER -> 0;
        };
    }

    private ScrollbarLayout scrollbarLayout() {
        return switch (tab) {
            case OVERVIEW -> entries.isEmpty() ? null : new ScrollbarLayout(
                    panelLeft + panelWidth - 7,
                    panelTop + 137,
                    overviewRows() * ENTRY_HEIGHT,
                    entries.size(),
                    overviewRows()
            );
            case HISTORY -> entries.isEmpty() ? null : new ScrollbarLayout(
                    panelLeft + panelWidth - 7,
                    panelTop + 66,
                    historyRows() * ENTRY_HEIGHT,
                    entries.size(),
                    historyRows()
            );
            case SHOPS -> shops.isEmpty() ? null : new ScrollbarLayout(
                    panelLeft + 203,
                    panelTop + 90,
                    shopRows() * SHOP_HEIGHT,
                    shops.size(),
                    shopRows()
            );
            case SERVER -> null;
        };
    }

    private void scrollbar(GuiGraphics graphics) {
        ScrollbarLayout layout = scrollbarLayout();
        if (layout == null) return;
        graphics.fill(
                layout.x,
                layout.y,
                layout.x + 3,
                layout.y + layout.height,
                SEPARATOR
        );
        int thumb = layout.thumbHeight();
        int travel = layout.height - thumb;
        int position = maximumScroll() == 0
                ? 0 : travel * scroll / maximumScroll();
        graphics.fill(
                layout.x,
                layout.y + position,
                layout.x + 3,
                layout.y + position + thumb,
                ACCENT
        );
    }

    private void updateScrollFromMouse(double mouseY, ScrollbarLayout layout) {
        int maximum = maximumScroll();
        if (maximum == 0) {
            scroll = 0;
            return;
        }
        int thumb = layout.thumbHeight();
        int travel = Math.max(1, layout.height - thumb);
        double relative = (mouseY - layout.y - thumb / 2.0D) / travel;
        scroll = Math.max(0, Math.min(maximum,
                (int) Math.round(relative * maximum)));
    }

    private int maximumBeneficiaryScroll() {
        if (tab != Tab.SHOPS || shops.isEmpty()) return 0;
        ShopView shop = shops.get(Math.min(selectedShopIndex, shops.size() - 1));
        return Math.max(0, shop.beneficiaries.size() - beneficiaryRows());
    }

    private ScrollbarLayout beneficiaryScrollbarLayout() {
        if (tab != Tab.SHOPS || shops.isEmpty()) return null;
        ShopView shop = shops.get(Math.min(selectedShopIndex, shops.size() - 1));
        if (shop.beneficiaries.isEmpty()) return null;
        return new ScrollbarLayout(
                panelLeft + panelWidth - 17,
                panelTop + 90 + beneficiaryStartOffset(),
                beneficiaryRows() * 14,
                shop.beneficiaries.size(),
                beneficiaryRows()
        );
    }

    private void beneficiaryScrollbar(GuiGraphics graphics) {
        ScrollbarLayout layout = beneficiaryScrollbarLayout();
        if (layout == null || maximumBeneficiaryScroll() == 0) return;
        graphics.fill(
                layout.x,
                layout.y,
                layout.x + 3,
                layout.y + layout.height,
                SEPARATOR
        );
        int thumb = layout.thumbHeight();
        int travel = layout.height - thumb;
        int position = travel * beneficiaryScroll / maximumBeneficiaryScroll();
        graphics.fill(
                layout.x,
                layout.y + position,
                layout.x + 3,
                layout.y + position + thumb,
                ACCENT
        );
    }

    private void updateBeneficiaryScrollFromMouse(
            double mouseY,
            ScrollbarLayout layout
    ) {
        int maximum = maximumBeneficiaryScroll();
        if (maximum == 0) {
            beneficiaryScroll = 0;
            return;
        }
        int thumb = layout.thumbHeight();
        int travel = Math.max(1, layout.height - thumb);
        double relative = (mouseY - layout.y - thumb / 2.0D) / travel;
        beneficiaryScroll = Math.max(0, Math.min(maximum,
                (int) Math.round(relative * maximum)));
    }

    private void offerTooltip(ItemStack stack, int x, int y) {
        if (currentMouseX >= x && currentMouseX < x + 16
                && currentMouseY >= y && currentMouseY < y + 16) {
            hoveredItem = stack;
        }
    }

    private record ScrollbarLayout(
            int x,
            int y,
            int height,
            int total,
            int visible
    ) {
        private int thumbHeight() {
            return total <= visible
                    ? height
                    : Math.max(14, height * visible / total);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x - 3 && mouseX < x + 6
                    && mouseY >= y && mouseY < y + height;
        }
    }

    private void leave() {
        if (leaving) return;
        leaving = true;
        JsonObject data = new JsonObject();
        data.addProperty("source", source);
        data.addProperty("portable", portable);
        if (returnPos != Long.MIN_VALUE) data.addProperty("returnPos", returnPos);
        TreasuryNetwork.sendAction("history_back", data);
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        leave();
    }

    private static ItemStack item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String signed(long value) {
        return (value > 0 ? "+" : "−") + number(Math.abs(value)) + " spurs";
    }

    private static String number(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value).replace(',', ' ');
    }

    private static String shortDimension(String dimension) {
        int separator = dimension.indexOf(':');
        return separator >= 0 ? dimension.substring(separator + 1) : dimension;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonArray()
                ? parent.getAsJsonArray(key) : new JsonArray();
    }

    private enum Tab {
        OVERVIEW(
                "screen.numismatics_treasury.history.tab.overview",
                "history_overview"),
        HISTORY(
                "screen.numismatics_treasury.history.tab.history",
                "history_transactions"),
        SHOPS(
                "screen.numismatics_treasury.history.tab.shops",
                "history_player_shops"),
        SERVER(
                "screen.numismatics_treasury.history.tab.server",
                "history_server");

        private final String key;
        private final ResourceLocation icon;

        Tab(String key, String iconName) {
            this.key = key;
            icon = ResourceLocation.fromNamespaceAndPath(
                    NumismaticsTreasury.MOD_ID,
                    "textures/gui/icons/" + iconName + ".png"
            );
        }
    }

    private enum ShopSort {
        RECENT("screen.numismatics_treasury.history.sort.recent"),
        SALES("screen.numismatics_treasury.history.sort.sales"),
        ITEMS("screen.numismatics_treasury.history.sort.items"),
        REVENUE("screen.numismatics_treasury.history.sort.revenue"),
        BEST_SALE("screen.numismatics_treasury.history.sort.best_sale");

        private final String key;

        ShopSort(String key) {
            this.key = key;
        }

        private ShopSort next() {
            ShopSort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record TotalsView(
            long earned,
            long spent,
            long sent,
            long received,
            long transactions
    ) {
        private static TotalsView parse(JsonObject data) {
            return new TotalsView(
                    longValue(data, "earned"),
                    longValue(data, "spent"),
                    longValue(data, "sent"),
                    longValue(data, "received"),
                    longValue(data, "transactions")
            );
        }
    }

    private record EntryView(
            long timestamp,
            String type,
            long amount,
            int quantity,
            String itemId,
            String itemName,
            ItemStack item,
            String counterparty,
            String detail
    ) {
        private static EntryView parse(JsonObject data) {
            return new EntryView(
                    longValue(data, "timestamp"),
                    ClientScreenData.string(data, "type", ""),
                    longValue(data, "amount"),
                    ClientScreenData.integer(data, "quantity", 0),
                    ClientScreenData.string(data, "itemId", "minecraft:air"),
                    ClientScreenData.string(data, "itemName", ""),
                    ClientScreenData.item(data),
                    ClientScreenData.string(data, "counterparty", ""),
                    ClientScreenData.string(data, "detail", "")
            );
        }
    }

    private record PeriodView(long sales, long items, long revenue) {
        private static PeriodView parse(JsonObject data) {
            return new PeriodView(
                    longValue(data, "sales"),
                    longValue(data, "items"),
                    longValue(data, "revenue")
            );
        }
    }

    private record ShopView(
            String key,
            String ownerName,
            String dimension,
            long pos,
            String itemId,
            String itemName,
            ItemStack item,
            long sales,
            long itemsSold,
            long grossRevenue,
            long bestSale,
            long lastSaleAt,
            PeriodView day,
            PeriodView week,
            PeriodView month,
            List<BeneficiaryView> beneficiaries
    ) {
        private static ShopView parse(JsonObject data) {
            List<BeneficiaryView> beneficiaries = new ArrayList<>();
            for (JsonElement value : array(data, "beneficiaries")) {
                if (value.isJsonObject()) {
                    beneficiaries.add(BeneficiaryView.parse(value.getAsJsonObject()));
                }
            }
            beneficiaries.sort(Comparator.comparingLong(BeneficiaryView::revenue).reversed());
            return new ShopView(
                    ClientScreenData.string(data, "key", ""),
                    ClientScreenData.string(data, "ownerName", "?"),
                    ClientScreenData.string(data, "dimension", ""),
                    longValue(data, "pos"),
                    ClientScreenData.string(data, "itemId", "minecraft:air"),
                    ClientScreenData.string(data, "itemName", ""),
                    ClientScreenData.item(data),
                    longValue(data, "sales"),
                    longValue(data, "itemsSold"),
                    longValue(data, "grossRevenue"),
                    longValue(data, "bestSale"),
                    longValue(data, "lastSaleAt"),
                    PeriodView.parse(object(data, "day")),
                    PeriodView.parse(object(data, "week")),
                    PeriodView.parse(object(data, "month")),
                    List.copyOf(beneficiaries)
            );
        }
    }

    private record BeneficiaryView(String name, long revenue) {
        private static BeneficiaryView parse(JsonObject data) {
            return new BeneficiaryView(
                    ClientScreenData.string(data, "name", "?"),
                    longValue(data, "revenue")
            );
        }
    }

    private record ServerView(
            long transactions,
            long totalVolume,
            long transferVolume,
            long serverShopVolume,
            long playerShopVolume,
            long auctionVolume,
            long commissionCollected
    ) {
        private static ServerView parse(JsonObject data) {
            return new ServerView(
                    longValue(data, "transactions"),
                    longValue(data, "totalVolume"),
                    longValue(data, "transferVolume"),
                    longValue(data, "serverShopVolume"),
                    longValue(data, "playerShopVolume"),
                    longValue(data, "auctionVolume"),
                    longValue(data, "commissionCollected")
            );
        }
    }

    private static long longValue(JsonObject data, String key) {
        return data.has(key) ? data.get(key).getAsLong() : 0L;
    }
}
