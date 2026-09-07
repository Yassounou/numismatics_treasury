package dev.yassou.numismaticstreasury.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** JSON server configuration, reloadable without restarting the game. */
public final class TreasuryConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get()
            .resolve("numismatics_treasury.json");
    private static volatile Values values = new Values();

    private TreasuryConfig() {
    }

    public static Values get() {
        return values;
    }

    public static synchronized ReloadResult load() {
        try {
            if (!Files.exists(PATH)) {
                Values defaults = validated(new Values());
                Files.createDirectories(PATH.getParent());
                Files.writeString(
                        PATH,
                        GSON.toJson(defaults),
                        StandardCharsets.UTF_8
                );
                values = defaults;
                return new ReloadResult(
                        true,
                        Component.translatable("message.numismatics_treasury.config.created")
                );
            }
            Values parsed = GSON.fromJson(
                    Files.readString(PATH, StandardCharsets.UTF_8),
                    Values.class
            );
            Values loaded = validated(parsed == null ? new Values() : parsed);
            Files.writeString(
                    PATH,
                    GSON.toJson(loaded),
                    StandardCharsets.UTF_8
            );
            values = loaded;
            return new ReloadResult(
                    true,
                    Component.translatable("message.numismatics_treasury.config.reloaded")
            );
        } catch (Exception exception) {
            NumismaticsTreasury.LOGGER.error(
                    "Could not load {}. Keeping the previous configuration.",
                    PATH,
                    exception
            );
            return new ReloadResult(
                    false,
                    Component.translatable(
                            "message.numismatics_treasury.config.invalid",
                            exception.getMessage()
                    )
            );
        }
    }

    public static Path path() {
        return PATH;
    }

    private static Values validated(Values config) throws IOException {
        if (config.modules == null) config.modules = new Modules();
        if (config.pay == null) config.pay = new Pay();
        if (config.auctionHouse == null) config.auctionHouse = new AuctionHouse();
        config.pay.minimum = Math.max(1, config.pay.minimum);
        config.pay.maximum = Math.max(config.pay.minimum, config.pay.maximum);
        config.auctionHouse.commissionPercent = Math.max(
                0.0D,
                Math.min(100.0D, config.auctionHouse.commissionPercent)
        );
        config.auctionHouse.maximumListingsPerPlayer = Math.max(
                1,
                config.auctionHouse.maximumListingsPerPlayer
        );
        config.auctionHouse.minimumPrice = Math.max(
                1,
                config.auctionHouse.minimumPrice
        );
        config.auctionHouse.maximumPrice = Math.max(
                config.auctionHouse.minimumPrice,
                config.auctionHouse.maximumPrice
        );
        List<Integer> durations = config.auctionHouse.allowedDurationsHours;
        if (durations == null || durations.isEmpty()) {
            durations = List.of(1, 6, 12, 24, 48);
        }
        config.auctionHouse.allowedDurationsHours = new ArrayList<>(
                durations.stream()
                        .filter(value -> value != null && value > 0 && value <= 24 * 365)
                        .distinct()
                        .sorted()
                        .toList()
        );
        if (config.auctionHouse.allowedDurationsHours.isEmpty()) {
            config.auctionHouse.allowedDurationsHours.add(24);
        }
        return config;
    }

    public static final class Values {
        public Modules modules = new Modules();
        public Pay pay = new Pay();
        public AuctionHouse auctionHouse = new AuctionHouse();
    }

    public static final class Modules {
        public boolean payCommand = true;
        public boolean bankTeller = true;
        public boolean portableTransferTerminal = true;
        public boolean serverShop = true;
        public boolean playerShop = true;
        public boolean auctionHouse = true;
    }

    public static final class Pay {
        public int minimum = 1;
        public int maximum = 2_000_000_000;
    }

    public static final class AuctionHouse {
        public double commissionPercent = 5.0D;
        public int maximumListingsPerPlayer = 20;
        public int minimumPrice = 1;
        public int maximumPrice = 2_000_000_000;
        public List<Integer> allowedDurationsHours = new ArrayList<>(
                List.of(1, 6, 12, 24, 48)
        );
    }

    public record ReloadResult(boolean successful, Component message) {
    }
}
