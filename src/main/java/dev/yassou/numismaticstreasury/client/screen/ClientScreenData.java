package dev.yassou.numismaticstreasury.client.screen;

import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
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
        if (data.has("itemNbt") && Minecraft.getInstance().level != null) {
            try {
                ItemStack decoded = ItemStack.parseOptional(
                        Minecraft.getInstance().level.registryAccess(),
                        TagParser.parseTag(data.get("itemNbt").getAsString())
                );
                if (!decoded.isEmpty()) return decoded;
            } catch (CommandSyntaxException ignored) {
                // Retain the id/count fallback for older or malformed payloads.
            }
        }
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
