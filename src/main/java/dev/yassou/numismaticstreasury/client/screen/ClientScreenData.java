package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonObject;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ClientScreenData {
    private ClientScreenData() {
    }

    static JsonObject parse(String json) {
        JsonObject value = TreasuryNetwork.GSON.fromJson(json, JsonObject.class);
        return value == null ? new JsonObject() : value;
    }

    static ItemStack item(JsonObject data) {
        ResourceLocation id = ResourceLocation.tryParse(string(data, "itemId", "minecraft:air"));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        int count = integer(data, "count", 0);
        return item == Items.AIR || count <= 0
                ? ItemStack.EMPTY
                : new ItemStack(item, count);
    }

    static int integer(JsonObject data, String key, int fallback) {
        return data.has(key) ? data.get(key).getAsInt() : fallback;
    }

    static long longValue(JsonObject data, String key, long fallback) {
        return data.has(key) ? data.get(key).getAsLong() : fallback;
    }

    static double decimal(JsonObject data, String key, double fallback) {
        return data.has(key) ? data.get(key).getAsDouble() : fallback;
    }

    static boolean bool(JsonObject data, String key, boolean fallback) {
        return data.has(key) ? data.get(key).getAsBoolean() : fallback;
    }

    static String string(JsonObject data, String key, String fallback) {
        return data.has(key) ? data.get(key).getAsString() : fallback;
    }
}
