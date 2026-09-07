package dev.yassou.numismaticstreasury.network.payload;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TreasuryActionPayload(
        String action,
        String json
) implements CustomPacketPayload {
    public static final Type<TreasuryActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    NumismaticsTreasury.MOD_ID,
                    "action"
            )
    );
    public static final StreamCodec<ByteBuf, TreasuryActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(32),
                    TreasuryActionPayload::action,
                    ByteBufCodecs.stringUtf8(65_536),
                    TreasuryActionPayload::json,
                    TreasuryActionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
