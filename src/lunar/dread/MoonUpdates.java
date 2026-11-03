package lunar.dread;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded host snapshots. Client inputs contain no position, damage or chosen outcome. */
public final class MoonUpdates {
    public static Consumer<World> worldReceiver = v -> {};
    public static Consumer<Personal> personalReceiver = v -> {};
    public static java.util.function.IntConsumer navigationReceiver=v->{};
    public record Navigation(int mode) implements CustomPacketPayload {
        public static final Type<Navigation> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("lunar_dread","navigation"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Navigation> CODEC=StreamCodec.of((b,v)->b.writeVarInt(v.mode),b->new Navigation(bounded(b,0,1)));
        @Override public Type<Navigation> type(){return TYPE;}
    }
    public record Beacon(BlockPos pos, int status, int tier) {}
    public record Wing(int entity, int style) {}
    public record Tumble(int entity, long start, int duration) {}
    public record World(int kind, int form, int overlay, long tick, long awakenedAt, long nightAt,
                        List<Beacon> beacons, List<Wing> wings, List<Tumble> tumbling, BlockPos shell, int shellAge) implements CustomPacketPayload {
        public static final Type<World> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lunar_dread", "moon_world"));
        public static final StreamCodec<RegistryFriendlyByteBuf, World> CODEC = StreamCodec.of((b,v) -> {
            b.writeVarInt(v.kind); b.writeVarInt(v.form); b.writeVarInt(v.overlay); b.writeLong(v.tick);
            b.writeLong(v.awakenedAt); b.writeLong(v.nightAt); b.writeVarInt(v.beacons.size());
            for (var d:v.beacons) { b.writeBlockPos(d.pos); b.writeVarInt(d.status); b.writeVarInt(d.tier); }
            b.writeVarInt(v.wings.size()); for (var w:v.wings) { b.writeVarInt(w.entity); b.writeVarInt(w.style); }
            b.writeVarInt(v.tumbling.size());for(var t:v.tumbling){b.writeVarInt(t.entity);b.writeLong(t.start);b.writeVarInt(t.duration);}
            b.writeBoolean(v.shell!=null); if (v.shell!=null) b.writeBlockPos(v.shell); b.writeVarInt(v.shellAge);
        }, b -> {
            int kind=bounded(b,0,MoonCycle.IDS.length-1),form=bounded(b,0,1),overlay=bounded(b,0,6);
            long tick=b.readLong(),awake=b.readLong(),night=b.readLong();
            int count=bounded(b,0,4); var drops=new ArrayList<Beacon>();
            for(int i=0;i<count;i++) drops.add(new Beacon(b.readBlockPos(),bounded(b,0,3),bounded(b,0,1)));
            count=bounded(b,0,6); var wings=new ArrayList<Wing>();
            for(int i=0;i<count;i++) wings.add(new Wing(bounded(b,0,Integer.MAX_VALUE),bounded(b,0,3)));
            count=bounded(b,0,32);var tumbling=new ArrayList<Tumble>();
            for(int i=0;i<count;i++)tumbling.add(new Tumble(bounded(b,0,Integer.MAX_VALUE),b.readLong(),bounded(b,0,2000)));
            BlockPos shell=b.readBoolean()?b.readBlockPos():null;
            return new World(kind,form,overlay,tick,awake,night,List.copyOf(drops),List.copyOf(wings),List.copyOf(tumbling),shell,bounded(b,0,100000));
        });
        @Override public Type<World> type() { return TYPE; }
    }
    public record Personal(long tick, int blackAge, int blackLength, int gravityAge, int gravityLength, Vec3 force, boolean falling, int threat) implements CustomPacketPayload {
        public static final Type<Personal> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lunar_dread", "moon_personal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Personal> CODEC = StreamCodec.of((b,v) -> {
            b.writeLong(v.tick); b.writeVarInt(v.blackAge); b.writeVarInt(v.blackLength);
            b.writeVarInt(v.gravityAge); b.writeVarInt(v.gravityLength);
            b.writeDouble(v.force.x); b.writeDouble(v.force.y); b.writeDouble(v.force.z); b.writeBoolean(v.falling);b.writeVarInt(v.threat);
        }, b -> {
            long tick=b.readLong(); int age=bounded(b,-1,2000),length=bounded(b,0,2000),gAge=bounded(b,-1,2000),gLength=bounded(b,0,2000);
            Vec3 force=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
            if(!Double.isFinite(force.lengthSqr()) || force.lengthSqr()>.02) throw new IllegalArgumentException("Invalid gravity force");
            return new Personal(tick,age,length,gAge,gLength,force,b.readBoolean(),bounded(b,0,2));
        });
        @Override public Type<Personal> type() { return TYPE; }
    }
    public record Menu(boolean open) implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lunar_dread", "moon_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Menu> CODEC=StreamCodec.of((b,v)->b.writeBoolean(v.open),b->new Menu(b.readBoolean()));
        @Override public Type<Menu> type(){return TYPE;}
    }
    private static int bounded(RegistryFriendlyByteBuf b,int min,int max){int v=b.readVarInt();if(v<min||v>max)throw new IllegalArgumentException("Invalid moon snapshot");return v;}
    public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("4");
        r.playToClient(World.TYPE,World.CODEC,(v,c)->c.enqueueWork(()->worldReceiver.accept(v)));
        r.playToClient(Personal.TYPE,Personal.CODEC,(v,c)->c.enqueueWork(()->personalReceiver.accept(v)));
        r.playToClient(Navigation.TYPE,Navigation.CODEC,(v,c)->c.enqueueWork(()->navigationReceiver.accept(v.mode)));
        r.playToServer(Menu.TYPE,Menu.CODEC,(v,c)->c.enqueueWork(()->{
            if(c.player() instanceof net.minecraft.server.level.ServerPlayer p) MoonIncidents.menu(p,v.open);
        }));
    }
}
