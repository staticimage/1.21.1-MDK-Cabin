package net.austin.cabinmod.network;

import io.netty.buffer.ByteBuf;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.diviner.DivinerClientHooks;
import net.austin.cabinmod.screen.custom.CraftingBlockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// The Cabin speaks to a player up in the heavens (client-side dialogue); everyone near the diviner hears his
// low hums. The speaker's client tells the server a line started (CabinSpeak), the server checks the player is
// really in that diviner's menu and passes it on to the players around it (CabinHum).
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModPayloads {
    private static final Map<UUID, Long> LAST_SPEAK = new HashMap<>();

    private ModPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CabinSpeak.TYPE, CabinSpeak.CODEC, ModPayloads::onSpeak);
        registrar.playToClient(CabinHum.TYPE, CabinHum.CODEC,
                (hum, context) -> DivinerClientHooks.onCabinHum(hum.pos(), hum.words(), hum.seed(), hum.listener()));
    }

    /** Client to server: the Cabin began a line of `words` words for this player at the diviner `pos`. */
    public record CabinSpeak(BlockPos pos, int words, int seed) implements CustomPacketPayload {
        public static final Type<CabinSpeak> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "cabin_speak"));
        public static final StreamCodec<ByteBuf, CabinSpeak> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CabinSpeak::pos, ByteBufCodecs.VAR_INT, CabinSpeak::words, ByteBufCodecs.INT, CabinSpeak::seed,
                CabinSpeak::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: hum `words` words at the diviner `pos`; `listener` is the player he is talking to. */
    public record CabinHum(BlockPos pos, int words, int seed, UUID listener) implements CustomPacketPayload {
        public static final Type<CabinHum> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "cabin_hum"));
        public static final StreamCodec<ByteBuf, CabinHum> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CabinHum::pos, ByteBufCodecs.VAR_INT, CabinHum::words, ByteBufCodecs.INT, CabinHum::seed,
                UUIDUtil.STREAM_CODEC, CabinHum::listener, CabinHum::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onSpeak(CabinSpeak speak, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof CraftingBlockMenu menu) || !menu.blockEntity.getBlockPos().equals(speak.pos())) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = LAST_SPEAK.get(player.getUUID());
        if (last != null && now - last < 20) return;
        LAST_SPEAK.put(player.getUUID(), now);
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(speak.pos()),
                new CabinHum(speak.pos(), Math.min(speak.words(), 40), speak.seed(), player.getUUID()));
    }
}
