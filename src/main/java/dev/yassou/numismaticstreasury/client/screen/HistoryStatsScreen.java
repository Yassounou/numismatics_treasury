package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    private int selectedShopIndex;
    private boolean leaving;

    public HistoryStatsScreen(String json) {
        super(Component.translatable("screen.numismatics_treasury.history.title"));
        JsonObject data = ClientScreenData.parse(json);
        source = ClientScreenData.string(data, "source", "command");
        portable = ClientScreenData.bool(data, "portable", false);
        returnPos = ClientScreenData.longValue(data, "returnPos", Long.MIN_VALUE);
        balance = ClientScreenData.integer(data, "balance", 0);
        operator = ClientScreenData.bool(data, "operator", false);
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
        shops = List.copyOf(parsedShops);
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
                    .selected(tab == value)
                    .bounds(x + index * (width + gap), y, width, 20)
                    .build());
        }
        addRenderableWidget(TreasuryButton.builder(
                        Component.literal("×"), ignored -> leave())
                .bounds(panelLeft + panelWidth - 25, panelTop + 5, 20, 18)
                .build());
    }

    private List<Tab> visibleTabs() {
        List<Tab> values = new ArrayList<>(List.of(Tab.OVERVIEW, Tab.HISTORY, Tab.SHOPS));
        if (operator && server != null) values.add(Tab.SERVER);
        return values;
    }

    private void switchTab(Tab value) {
        tab = value;
        scroll = 0;
        rebuildScreen();
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (mouseX < panelLeft + 10 || mouseX >= panelLeft + panelWidth - 10
                || mouseY < panelTop + 64 || mouseY >= panelTop + panelHeight - 10) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int maximum = switch (tab) {
            case OVERVIEW -> Math.max(0, entries.size() - 5);
            case HISTORY -> Math.max(0, entries.size() - 7);
            case SHOPS -> Math.max(0, shops.size() - 5);
            case SERVER -> 0;
        };
        scroll = Math.max(0, Math.min(maximum, scroll + (scrollY < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (tab != Tab.SHOPS || button != 0) return false;
        int x = panelLeft + 10;
        int y = panelTop + 66;
        int listWidth = 190;
        if (mouseX < x || mouseX >= x + listWidth
                || mouseY < y || mouseY >= y + SHOP_HEIGHT * 5) {
            return false;
        }
        int index = scroll + (int) ((mouseY - y) / SHOP_HEIGHT);
        if (index < 0 || index >= shops.size()) return false;
        selectedShopIndex = index;
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics);
        MoneyDisplay.renderBadge(
                graphics,
                font,
                panelLeft + panelWidth - MoneyDisplay.badgeWidth(font, balance) - 34,
                panelTop + 6,
                balance
        );
        switch (tab) {
            case OVERVIEW -> renderOverview(graphics);
            case HISTORY -> renderHistory(graphics);
            case SHOPS -> renderShops(graphics);
            case SERVER -> renderServer(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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
        int end = Math.min(entries.size(), scroll + 5);
        for (int index = scroll; index < end; index++) {
            renderEntry(graphics, entries.get(index),
                    x, panelTop + 137 + (index - scroll) * ENTRY_HEIGHT);
        }
    }

    private void renderHistory(GuiGraphics graphics) {
        int x = panelLeft + 10;
        if (entries.isEmpty()) {
            empty(graphics, panelTop + 174,
                    "screen.numismatics_treasury.history.empty");
            return;
        }
        int end = Math.min(entries.size(), scroll + 7);
        for (int index = scroll; index < end; index++) {
            renderEntry(graphics, entries.get(index),
                    x, panelTop + 66 + (index - scroll) * ENTRY_HEIGHT);
        }
        scrollbar(graphics, entries.size(), 7, panelTop + 66, 7 * ENTRY_HEIGHT);
    }

    private void renderEntry(GuiGraphics graphics, EntryView entry, int x, int y) {
        int width = panelWidth - 20;
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT - 2, PANEL_ALT);
        ItemStack icon = item(entry.itemId);
        if (!icon.isEmpty()) graphics.renderItem(icon, x + 6, y + 6);
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
        int end = Math.min(shops.size(), scroll + 5);
        for (int index = scroll; index < end; index++) {
            ShopView shop = shops.get(index);
            int y = panelTop + 66 + (index - scroll) * SHOP_HEIGHT;
            graphics.fill(x, y, x + listWidth, y + SHOP_HEIGHT - 3, PANEL_ALT);
            if (index == selectedShopIndex) {
                outline(graphics, x, y, listWidth, SHOP_HEIGHT - 3, ACCENT);
            }
            ItemStack icon = item(shop.itemId);
            if (!icon.isEmpty()) graphics.renderItem(icon, x + 7, y + 8);
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
        scrollbar(graphics, shops.size(), 5, panelTop + 66, 5 * SHOP_HEIGHT);
        renderShopDetails(graphics, shops.get(Math.min(selectedShopIndex, shops.size() - 1)));
    }

    private void renderShopDetails(GuiGraphics graphics, ShopView shop) {
        int x = panelLeft + 210;
        int y = panelTop + 66;
        int width = panelWidth - 220;
        graphics.fill(x, y, x + width, panelTop + panelHeight - 10, PANEL_ALT);
        ItemStack icon = item(shop.itemId);
        if (!icon.isEmpty()) graphics.renderItem(icon, x + 9, y + 8);
        graphics.drawString(font,
                font.plainSubstrByWidth(shop.itemName, width - 44),
                x + 32, y + 7, TEXT, false);
        graphics.drawString(font, shop.ownerName, x + 32, y + 19, MUTED, false);

        String totalsText = Component.translatable(
                "screen.numismatics_treasury.history.shop_totals",
                shop.sales, shop.itemsSold, number(shop.grossRevenue)).getString();
        graphics.drawString(font,
                font.plainSubstrByWidth(totalsText, width - 18),
                x + 9, y + 42, ACCENT, false);
        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.history.best_sale",
                        number(shop.bestSale)),
                x + 9, y + 57, TEXT, false);

        graphics.fill(x + 8, y + 75, x + width - 8, y + 76, SEPARATOR);
        periodLine(graphics, x + 9, y + 84, "24 h", shop.day);
        periodLine(graphics, x + 9, y + 101, "7 d", shop.week);
        periodLine(graphics, x + 9, y + 118, "30 d", shop.month);

        graphics.drawString(font, Component.translatable(
                        "screen.numismatics_treasury.history.beneficiaries"),
                x + 9, y + 143, ACCENT, false);
        if (shop.beneficiaries.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.numismatics_treasury.history.no_revenue"),
                    x + 9, y + 160, MUTED, false);
            return;
        }
        int count = Math.min(4, shop.beneficiaries.size());
        for (int index = 0; index < count; index++) {
            BeneficiaryView beneficiary = shop.beneficiaries.get(index);
            String amount = number(beneficiary.revenue) + " spurs";
            graphics.drawString(font,
                    font.plainSubstrByWidth(beneficiary.name, width - 100),
                    x + 9, y + 160 + index * 14, TEXT, false);
            graphics.drawString(font, amount,
                    x + width - 9 - font.width(amount),
                    y + 160 + index * 14, SUCCESS, false);
        }
        if (shop.beneficiaries.size() > count) {
            graphics.drawString(font, "+" + (shop.beneficiaries.size() - count),
                    x + 9, y + 160 + count * 14, MUTED, false);
        }
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
        int x = panelLeft + 18;
        int y = panelTop + 74;
        int width = (panelWidth - 42) / 2;
        int height = 48;
        serverCard(graphics, x, y, width, height,
                "screen.numismatics_treasury.history.server.volume", server.totalVolume);
        serverCard(graphics, x + width + 6, y, width, height,
                "screen.numismatics_treasury.history.server.transactions", server.transactions);
        serverCard(graphics, x, y + 56, width, height,
                "screen.numismatics_treasury.history.server.transfers", server.transferVolume);
        serverCard(graphics, x + width + 6, y + 56, width, height,
                "screen.numismatics_treasury.history.server.player_shops", server.playerShopVolume);
        serverCard(graphics, x, y + 112, width, height,
                "screen.numismatics_treasury.history.server.server_shops", server.serverShopVolume);
        serverCard(graphics, x + width + 6, y + 112, width, height,
                "screen.numismatics_treasury.history.server.auctions", server.auctionVolume);
        serverCard(graphics, x, y + 168, panelWidth - 36, 38,
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

    private void scrollbar(GuiGraphics graphics, int total, int visible, int y, int height) {
        if (total <= visible) return;
        int x = panelLeft + panelWidth - 8;
        graphics.fill(x, y, x + 3, y + height, SEPARATOR);
        int thumb = Math.max(14, height * visible / total);
        int travel = height - thumb;
        int position = travel * scroll / Math.max(1, total - visible);
        graphics.fill(x, y + position, x + 3, y + position + thumb, ACCENT);
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
        OVERVIEW("screen.numismatics_treasury.history.tab.overview"),
        HISTORY("screen.numismatics_treasury.history.tab.history"),
        SHOPS("screen.numismatics_treasury.history.tab.shops"),
        SERVER("screen.numismatics_treasury.history.tab.server");

        private final String key;

        Tab(String key) {
            this.key = key;
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
