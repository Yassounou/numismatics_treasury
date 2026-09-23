package dev.yassou.numismaticstreasury.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local-only visual preferences. These values are never synchronized by a server. */
public final class TreasuryClientPreferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get()
            .resolve("numismatics_treasury-client.json");
    private static boolean loaded;
    private static Values values = new Values();

    private TreasuryClientPreferences() {
    }

    public static synchronized boolean showHistoryBalance() {
        loadIfNeeded();
        return values.showHistoryBalance;
    }

    public static synchronized void setShowHistoryBalance(boolean visible) {
        loadIfNeeded();
        values.showHistoryBalance = visible;
        save();
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(PATH)) {
                Values parsed = GSON.fromJson(
                        Files.readString(PATH, StandardCharsets.UTF_8),
                        Values.class
                );
                if (parsed != null) values = parsed;
            }
            save();
        } catch (Exception exception) {
            NumismaticsTreasury.LOGGER.warn(
                    "Could not load client preferences from {}", PATH, exception);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(values), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            NumismaticsTreasury.LOGGER.warn(
                    "Could not save client preferences to {}", PATH, exception);
        }
    }

    private static final class Values {
        private boolean showHistoryBalance = true;
    }
}
