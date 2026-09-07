package dev.yassou.numismaticstreasury.network.payload;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenTreasuryScreenPayload(
        String screen,
        String json
) implements CustomPacketPayload {
    public static final Type<OpenTreasuryScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    NumismaticsTreasury.MOD_ID,
                    "open_screen"
            )
    );
    public static final StreamCodec<ByteBuf, OpenTreasuryScreenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(32),
                    OpenTreasuryScreenPayload::screen,
                    ByteBufCodecs.stringUtf8(2_097_152),
                    OpenTreasuryScreenPayload::json,
                    OpenTreasuryScreenPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
