package dev.yassou.numismaticstreasury.server.auction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public record TreasuryNotification(String key, List<String> arguments) {
    public TreasuryNotification {
        arguments = List.copyOf(arguments);
    }

    public Component component() {
        return Component.translatable(key, arguments.toArray());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        ListTag values = new ListTag();
        for (String argument : arguments) values.add(StringTag.valueOf(argument));
        tag.put("arguments", values);
        return tag;
    }

    public static TreasuryNotification load(CompoundTag tag) {
        List<String> arguments = new ArrayList<>();
        for (Tag value : tag.getList("arguments", Tag.TAG_STRING)) {
            arguments.add(value.getAsString());
        }
        return new TreasuryNotification(tag.getString("key"), arguments);
    }
}
