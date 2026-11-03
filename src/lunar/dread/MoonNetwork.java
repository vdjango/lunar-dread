package lunar.dread;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One bounded, server-owned snapshot for all clients; never trusts client combat data. */
public final class MoonNetwork {
    public static Consumer<State> receiver = state -> {};
    public static Consumer<Dread> dreadReceiver = state -> {};
    public record Gaze(boolean looking) implements CustomPacketPayload {
        public static final Type<Gaze> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lunar_dread", "moon_gaze"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Gaze> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBoolean(value.looking), buf -> new Gaze(buf.readBoolean()));
        @Override public Type<Gaze> type() { return TYPE; }
    }
    public record Dread(int gaze, int mark) implements CustomPacketPayload {
        public static final Type<Dread> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lunar_dread", "moon_dread"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Dread> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.gaze); buf.writeVarInt(value.mark); }, buf -> {
                int gaze = buf.readVarInt(), mark = buf.readVarInt();
                if (gaze < 0 || gaze > 80 || mark < 0 || mark > 600) throw new IllegalArgumentException("Invalid moon attention");
                return new Dread(gaze, mark);
            });
        @Override public Type<Dread> type() { return TYPE; }
    }
    public record Spot(Vec3 aim, Vec3 impact) {}
    public record State(boolean active, int phase, long tick, int bloodCount, List<Spot> spots) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lunar_dread", "living_moon"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBoolean(value.active); buf.writeVarInt(value.phase); buf.writeLong(value.tick); buf.writeVarInt(value.bloodCount);
            buf.writeVarInt(value.spots.size());
            for (Spot s : value.spots) { write(buf, s.aim); write(buf, s.impact); }
        }, buf -> {
            boolean active = buf.readBoolean(); int phase = buf.readVarInt(); long tick = buf.readLong(); int count = buf.readVarInt();
            int size = buf.readVarInt();
            if (size < 0 || size > 3 || phase < 0 || phase > 3) throw new IllegalArgumentException("Invalid moon snapshot");
            List<Spot> spots = new ArrayList<>();
            for (int i = 0; i < size; i++) spots.add(new Spot(read(buf), read(buf)));
            return new State(active, phase, tick, count, List.copyOf(spots));
        });
        private static void write(RegistryFriendlyByteBuf buf, Vec3 v) { buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); }
        private static Vec3 read(RegistryFriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
        @Override public Type<State> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(State.TYPE, State.CODEC,
            (payload, context) -> context.enqueueWork(() -> receiver.accept(payload)));
        registrar.playToClient(Dread.TYPE, Dread.CODEC,
            (payload, context) -> context.enqueueWork(() -> dreadReceiver.accept(payload)));
        registrar.playToServer(Gaze.TYPE, Gaze.CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) MoonDread.report(player, payload.looking());
            }));
    }
}
