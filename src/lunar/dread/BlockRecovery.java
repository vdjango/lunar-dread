package lunar.dread;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Restores only recorded mob edits, spread across the next daylight. Never scans terrain. */
public final class BlockRecovery {
    static final int MAX_PENDING=16384, VISITS_PER_TICK=8, CHANGES_PER_TICK=2;
    static final long RETENTION_TICKS=3*24000L;
    private static final SavedData.Factory<State> FACTORY=new SavedData.Factory<>(State::new,State::load,null);

    static final class Change {
        final long pos;
        final BlockState original;
        BlockState expected;
        long due,expires;
        Change(long pos,BlockState original,BlockState expected,long due,long expires){
            this.pos=pos;this.original=original;this.expected=expected;this.due=due;this.expires=expires;
        }
    }
    static final class State extends SavedData {
        final LinkedHashMap<Long,Change> changes=new LinkedHashMap<>();
        final LinkedHashSet<Long> order=new LinkedHashSet<>();
        static State load(CompoundTag tag,HolderLookup.Provider registries){
            var state=new State();var blocks=registries.lookupOrThrow(Registries.BLOCK);
            ListTag list=tag.getList("changes",Tag.TAG_COMPOUND);
            for(int i=0;i<list.size()&&state.changes.size()<MAX_PENDING;i++){
                CompoundTag item=list.getCompound(i);long pos=item.getLong("pos");
                BlockState original=NbtUtils.readBlockState(blocks,item.getCompound("original"));
                BlockState expected=NbtUtils.readBlockState(blocks,item.getCompound("expected"));
                if(original.equals(expected))continue;
                long due=item.getLong("due");
                long expires=item.contains("expires")?Math.min(item.getLong("expires"),due+RETENTION_TICKS):due+RETENTION_TICKS;
                state.changes.put(pos,new Change(pos,original,expected,due,expires));
                state.order.add(pos);
            }
            return state;
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
            var list=new ListTag();
            for(var change:changes.values()){
                var item=new CompoundTag();item.putLong("pos",change.pos);
                item.put("original",NbtUtils.writeBlockState(change.original));
                item.put("expected",NbtUtils.writeBlockState(change.expected));
                item.putLong("due",change.due);item.putLong("expires",change.expires);list.add(item);
            }
            tag.put("changes",list);return tag;
        }
    }
    static State state(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY,"lunar_dread_block_recovery");}
    static int pending(ServerLevel level){return state(level).changes.size();}

    static void record(ServerLevel level,BlockPos pos,BlockState oldState,BlockState newState){
        if(level.dimension()!=Level.OVERWORLD||oldState.equals(newState)||oldState.hasBlockEntity()
            ||newState.hasBlockEntity()||!level.getWorldBorder().isWithinBounds(pos))return;
        var state=state(level);long key=pos.asLong();var previous=state.changes.get(key);
        // If another actor has changed this cell, it no longer belongs to the mob's repair job.
        if(previous!=null&&!previous.expected.equals(oldState)){forget(level,pos);previous=null;}
        if(previous==null&&state.changes.size()>=MAX_PENDING)return;
        long day=level.getDayTime()/24000;
        long spread=Math.floorMod(key*0x9E3779B97F4A7C15L,11800);
        long due=(day+1)*24000+20+spread;
        if(previous==null){state.changes.put(key,new Change(key,oldState,newState,due,due+RETENTION_TICKS));state.order.add(key);}
        else if(previous.original.equals(newState)){state.changes.remove(key);state.order.remove(key);}
        else {previous.expected=newState;previous.due=Math.max(previous.due,due);}
        state.setDirty();
    }
    static void forget(ServerLevel level,BlockPos pos){
        if(level.dimension()!=Level.OVERWORLD)return;
        var state=state(level);long key=pos.asLong();
        if(state.changes.remove(key)!=null){state.order.remove(key);state.setDirty();}
    }
    @SubscribeEvent public void playerBreak(BlockEvent.BreakEvent event){
        if(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer &&event.getLevel() instanceof ServerLevel level)
            forget(level,event.getPos());
    }
    @SubscribeEvent public void playerPlace(BlockEvent.EntityPlaceEvent event){
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer &&event.getLevel() instanceof ServerLevel level)
            forget(level,event.getPos());
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event){
        var level=event.getServer().overworld();
        process(level);
    }
    static void process(ServerLevel level){
        var state=state(level);if(state.order.isEmpty())return;
        int visits=Math.min(VISITS_PER_TICK,state.order.size()),changed=0;long now=level.getDayTime();
        boolean repair=now%24000<12000&&LunarDread.score(level,"#blood_active")!=1;
        for(int i=0;i<visits&&changed<CHANGES_PER_TICK;i++){
            long key=state.order.removeFirst();var change=state.changes.get(key);
            if(change==null)continue;
            // Expiry also runs at night, and never loads a chunk or changes its blocks.
            if(now>=change.expires){state.changes.remove(key);state.setDirty();continue;}
            if(!repair||change.due>now){state.order.addLast(key);continue;}
            var pos=BlockPos.of(key);
            if(!level.hasChunkAt(pos)){state.order.addLast(key);continue;}
            if(!level.getBlockState(pos).equals(change.expected)){
                state.changes.remove(key);state.setDirty();continue;
            }
            if(!change.original.canSurvive(level,pos)
                ||!change.original.getCollisionShape(level,pos).isEmpty()
                &&!level.getEntitiesOfClass(LivingEntity.class,new AABB(pos),e->!e.isSpectator()).isEmpty()){
                state.order.addLast(key);continue;
            }
            if(level.setBlockAndUpdate(pos,change.original)){
                state.changes.remove(key);state.setDirty();changed++;
            }else state.order.addLast(key);
        }
    }
}
