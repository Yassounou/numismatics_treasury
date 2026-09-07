package dev.yassou.numismaticstreasury.network;

import dev.yassou.numismaticstreasury.NumismaticsTreasury;
import dev.yassou.numismaticstreasury.network.payload.OpenTreasuryScreenPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.function.Consumer;

/** Keeps all client-only Minecraft classes out of the dedicated-server classpath. */
public final class ClientPayloadBridge {
    private static volatile Consumer<OpenTreasuryScreenPayload> sink = payload ->
            NumismaticsTreasury.LOGGER.error(
                    "Client payload received before the client bootstrap: {}",
                    payload.screen()
            );

    private ClientPayloadBridge() {
    }

    public static void install(Consumer<OpenTreasuryScreenPayload> clientSink) {
        sink = Objects.requireNonNull(clientSink);
    }

    public static void handle(OpenTreasuryScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> sink.accept(payload));
    }
}
