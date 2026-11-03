package lunar.dread;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One real temporary landing surface. Ownership survives chunk unload and process restart. */
public final class NearMoon {
    public static final class State extends SavedData {
        BlockPos center;long start,end;int samples,day=-1;final List<Long> owned=new ArrayList<>();
        static State load(CompoundTag t,HolderLookup.Provider r){var s=new State();if(t.contains("center"))s.center=BlockPos.of(t.getLong("center"));s.start=t.getLong("start");s.end=t.getLong("end");s.samples=t.getInt("samples");s.day=t.getInt("day");for(long p:t.getLongArray("owned"))s.owned.add(p);return s;}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){if(center!=null)t.putLong("center",center.asLong());t.putLong("start",start);t.putLong("end",end);t.putInt("samples",samples);t.putInt("day",day);t.putLongArray("owned",owned.stream().mapToLong(Long::longValue).toArray());return t;}
    }
    static State state(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new,State::load,null),"lunar_dread_near_moon");}
    public static BlockPos center(ServerLevel l){return state(l).center;}
    public static int age(ServerLevel l){var s=state(l);return s.center==null?0:Math.clamp((int)(l.getGameTime()-s.start),0,100000);}
    static boolean create(ServerLevel l,ServerPlayer p,int lift){
        var s=state(l);if(s.center!=null||s.day==SurvivalRules.day(l))return false;
        var center=p.blockPosition().offset(16,lift-12,0);if(center.getY()+8>=l.getMaxBuildHeight())return false;
        var positions=new ArrayList<BlockPos>();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            int r=x*x+z*z;if(r>144)continue;
            int depth=r<64?5:r<112?4:1;for(int y=0;y<depth;y++)positions.add(center.offset(x,-y,z));
            if(r>112&&r<144&&x>0)for(int y=1;y<=4;y++)positions.add(center.offset(x,y,z));
        }
        for(var pos:positions)if(!l.hasChunkAt(pos)||!l.getBlockState(pos).isAir()||!l.getWorldBorder().isWithinBounds(pos))return false;
        s.center=center;s.start=l.getGameTime();s.end=s.start+1600;s.samples=0;s.day=SurvivalRules.day(l);
        // Outer-to-inner removal preserves a readable, retreating edge.
        positions.sort(Comparator.<BlockPos>comparingDouble(v->-v.distSqr(center)));
        for(var pos:positions){l.setBlockAndUpdate(pos,pos.equals(center)?MoonContent.CORE.get().defaultBlockState():MoonContent.SHELL.get().defaultBlockState());s.owned.add(pos.asLong());}
        s.setDirty();return true;
    }
    public static void finish(ServerLevel l){var s=state(l);if(s.center!=null){s.end=Math.min(s.end,l.getGameTime()+200);s.setDirty();}}
    @SubscribeEvent public void broken(BlockEvent.BreakEvent e){
        if(!(e.getLevel() instanceof ServerLevel l)||!(e.getPlayer() instanceof ServerPlayer p))return;
        if(!e.getState().is(MoonContent.SHELL.get())&&!e.getState().is(MoonContent.CORE.get()))return;
        var s=state(l);if(!s.owned.contains(e.getPos().asLong()))return;
        if(s.samples<8&&!p.isCreative()){
            s.samples++;var stack=new ItemStack(MoonContent.relic(7),1);
            if(!p.getInventory().add(stack))p.drop(stack,false);
        }
        if(e.getState().is(MoonContent.CORE.get())){finish(l);MoonGravity.clear(l);}
        s.setDirty();
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        var l=e.getServer().overworld();var s=state(l);if(s.center==null)return;long now=l.getGameTime();
        if(!MoonCycle.night(l)&&s.end>now+200)finish(l);
        if(now>=s.end-200&&now<s.end&&now%20==0){
            l.sendParticles(ParticleTypes.ASH,s.center.getX(),s.center.getY()+1,s.center.getZ(),32,11,.5,11,.015);
            if(now%40==0)l.playSound(null,s.center,SoundEvents.DEEPSLATE_BREAK,SoundSource.AMBIENT,1,.5f);
        }
        if(now<s.end)return;
        var it=s.owned.iterator();int changed=0;
        while(it.hasNext()&&changed<8){var pos=BlockPos.of(it.next());if(!l.hasChunkAt(pos))continue;
            var block=l.getBlockState(pos);if(block.is(MoonContent.SHELL.get())||block.is(MoonContent.CORE.get()))l.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            it.remove();changed++;
        }
        if(s.owned.isEmpty())s.center=null;if(changed>0||s.center==null)s.setDirty();
    }
}
