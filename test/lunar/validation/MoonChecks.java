package lunar.validation;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import lunar.dread.LivingMoon;
import lunar.dread.MoonNetwork;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class MoonChecks {
    static Field field(String name) throws Exception { var f = LivingMoon.class.getDeclaredField(name); f.setAccessible(true); return f; }
    static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
    static void score(ServerPlayer p, String name, int value) {
        var board=p.serverLevel().getScoreboard();
        board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name),board.getObjective("lunar_dread.sys")).set(value);
    }
    public static void run(ServerPlayer p) {
        var level = p.serverLevel(); long oldTime = level.getDayTime();
        try {
            level.setDayTime(114000); score(p,"#blood_active",1); score(p,"#blood_count",1); score(p,"#finished",0); score(p,"#moon_attacks",1);
            var cycle=lunar.dread.MoonCycle.state(level);cycle.kind=6;cycle.form=1;cycle.forced=true;cycle.day=5;cycle.awakenedAt=level.getGameTime()-1000;
            p.removeAllEffects(); p.setHealth(20); p.invulnerableTime = 0; p.moveTo(260,100,260);
            var begin = LivingMoon.class.getDeclaredMethod("begin", net.minecraft.server.level.ServerLevel.class, List.class); begin.setAccessible(true);
            var snapshot = LivingMoon.class.getDeclaredMethod("snapshot", net.minecraft.server.level.ServerLevel.class); snapshot.setAccessible(true);
            var moon = new LivingMoon(); begin.invoke(moon, level, List.of(p));
            check(((MoonNetwork.State)snapshot.invoke(moon,level)).phase()==1, "Attack must begin with telegraph");
            p.moveTo(262,100,260); moon.tick(new ServerTickEvent.Post(() -> true, p.server));
            check(((MoonNetwork.State)snapshot.invoke(moon,level)).spots().getFirst().aim().x==262, "Tracking must follow movement");
            field("age").setInt(moon,48); moon.tick(new ServerTickEvent.Post(() -> true, p.server));
            p.moveTo(270,100,260);
            field("age").setInt(moon,80); moon.tick(new ServerTickEvent.Post(() -> true, p.server));
            check(p.getHealth()==20, "Leaving a locked circle must dodge the beam");
            check(((MoonNetwork.State)snapshot.invoke(moon,level)).spots().getFirst().aim().x==262, "Locked aim must not follow player");
            moon = new LivingMoon(); p.moveTo(260,100,260); p.invulnerableTime=0;
            BlockPos roof = new BlockPos(260,103,260); var previous = level.getBlockState(roof);
            level.setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());
            begin.invoke(moon,level,List.of(p)); field("age").setInt(moon,80); moon.tick(new ServerTickEvent.Post(() -> true, p.server));
            check(p.getHealth()==20, "Solid roof must block moon damage");
            check(((MoonNetwork.State)snapshot.invoke(moon,level)).spots().getFirst().impact().y>=103, "Beam must stop at roof");
            level.setBlockAndUpdate(roof,previous);
            moon = new LivingMoon(); p.removeAllEffects(); p.invulnerableTime=0;
            begin.invoke(moon,level,List.of(p));
            check(p.getHealth()==20, "Telegraph must not damage player");
            field("age").setInt(moon,80); moon.tick(new ServerTickEvent.Post(() -> true, p.server));
            check(p.getHealth()<20 && p.getHealth()>0, "Uncovered locked target must take real bounded damage");
            check(!p.getActiveEffects().isEmpty(), "Moon hit must apply recoverable debuffs");
            var sent=(MoonNetwork.State)snapshot.invoke(moon,level);
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try { MoonNetwork.State.CODEC.encode(buf,sent); check(MoonNetwork.State.CODEC.decode(buf).equals(sent), "Network snapshot roundtrip"); }
            finally { buf.release(); }
            level.setDayTime(120000); moon.tick(new ServerTickEvent.Post(() -> true, p.server));
            var reset=(MoonNetwork.State)snapshot.invoke(moon,level);
            check(!reset.active() && reset.phase()==0 && reset.spots().isEmpty(), "Dawn must remove attacks and active state");
            level.setDayTime(114000); moon = new LivingMoon(); p.setHealth(20); p.invulnerableTime=0;
            begin.invoke(moon,level,List.of(p)); score(p,"#blood_active",0); moon.tick(new ServerTickEvent.Post(() -> true, p.server));
            check(((MoonNetwork.State)snapshot.invoke(moon,level)).spots().isEmpty(), "Early event end must cancel pending beam");
        } catch (Exception e) { throw new IllegalStateException("Living moon regression",e); }
        finally { level.setDayTime(oldTime); score(p,"#blood_active",0); score(p,"#moon_attacks",0); p.removeAllEffects(); p.setHealth(20); }
    }
}
