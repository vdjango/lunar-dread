package lunar.dread;

import java.util.LinkedHashMap;
import com.tik.zbb.Constants;
import com.tik.zbb.config.ConfigManager;
import com.tik.zbb.ai.action.actions.breakk.BreakAction;
import com.tik.zbb.ai.action.actions.build.BuildAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Real ZBB bus, player edits, NBT persistence and bounded daylight recovery. */
public final class BlockRecoveryChecks {
    private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    public static void run(ServerPlayer player){
        var level=player.serverLevel();long time=level.getDayTime();
        var oldBlocks=new LinkedHashMap<BlockPos,BlockState>();
        var state=BlockRecovery.state(level);var oldChanges=new LinkedHashMap<>(state.changes);
        var oldOrder=new java.util.LinkedHashSet<>(state.order);
        var origin=new BlockPos(280,100,280);
        var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        try{
            zombie.moveTo(278.5,100,280.5);zombie.setNoAi(true);level.addFreshEntity(zombie);
            state.changes.clear();state.order.clear();state.setDirty();
            level.setDayTime(9*24000L+18000);MoonCycle.score(level,"#blood_active",1);
            for(int i=0;i<23;i++)oldBlocks.put(origin.offset(i,0,0),level.getBlockState(origin.offset(i,0,0)));
            var broken=origin;level.setBlockAndUpdate(broken,Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(broken,Blocks.AIR.defaultBlockState());
            Constants.EVENT_BUS.post(new BreakAction.OnAnyBlockBrokenEvent(level,broken,Blocks.STONE.defaultBlockState(),ConfigManager.getConfigSnapshot(),zombie));
            check(BlockRecovery.pending(level)==1,"ZBB broken event must record the original stone");
            var built=origin.east();level.setBlockAndUpdate(built,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(built,Blocks.COBBLESTONE.defaultBlockState());
            Constants.EVENT_BUS.post(new BuildAction.OnAnyBlockPlacedEvent(level,built,ConfigManager.getConfigSnapshot(),Blocks.COBBLESTONE.defaultBlockState(),Blocks.AIR.defaultBlockState(),null));
            check(BlockRecovery.pending(level)==2,"ZBB built event must record temporary support");
            var playerPlace=origin.east(2);level.setBlockAndUpdate(playerPlace,Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(playerPlace,Blocks.AIR.defaultBlockState());
            BlockRecovery.record(level,playerPlace,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(),level,playerPlace),Blocks.STONE.defaultBlockState(),player));
            level.setBlockAndUpdate(playerPlace,Blocks.COBBLESTONE.defaultBlockState());
            check(!state.changes.containsKey(playerPlace.asLong()),"Player placement must claim a mob-broken cell");
            var playerBreak=origin.east(3);level.setBlockAndUpdate(playerBreak,Blocks.COBBLESTONE.defaultBlockState());
            BlockRecovery.record(level,playerBreak,Blocks.AIR.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState());
            NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,playerBreak,level.getBlockState(playerBreak),player));
            level.setBlockAndUpdate(playerBreak,Blocks.AIR.defaultBlockState());
            check(!state.changes.containsKey(playerBreak.asLong()),"Player break must cancel mob-built block removal");
            var overwritten=origin.east(4);level.setBlockAndUpdate(overwritten,Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(overwritten,Blocks.AIR.defaultBlockState());
            BlockRecovery.record(level,overwritten,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(overwritten,Blocks.DIAMOND_BLOCK.defaultBlockState());
            var tower=origin.east(5);level.setBlockAndUpdate(tower,Blocks.COBBLESTONE.defaultBlockState());
            var saved=BlockRecovery.State.load(state.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
            check(saved.changes.size()==3&&saved.changes.get(broken.asLong()).original.is(Blocks.STONE)
                &&saved.changes.get(built.asLong()).expected.is(Blocks.COBBLESTONE),"Pending states must survive NBT save/load");
            level.setDayTime(10*24000L+11980);BlockRecovery.process(level);
            check(level.getBlockState(broken).isAir()&&level.getBlockState(built).is(Blocks.COBBLESTONE),"Active blood moon must prevent repair");
            MoonCycle.score(level,"#blood_active",0);
            level.setDayTime(10*24000L+1);BlockRecovery.process(level);
            check(level.getBlockState(broken).isAir()&&level.getBlockState(built).is(Blocks.COBBLESTONE),
                "Repair must not happen all at once at sunrise");
            long first=Math.min(state.changes.get(broken.asLong()).due,state.changes.get(built.asLong()).due);
            level.setDayTime(first);BlockRecovery.process(level);
            check(level.getBlockState(broken).is(Blocks.STONE)!=level.getBlockState(built).isAir(),
                "Different repair slots must be spread across the daylight");
            level.setDayTime(10*24000L+11980);BlockRecovery.process(level);
            for(int i=0;i<8;i++)BlockRecovery.process(level);
            check(level.getBlockState(broken).is(Blocks.STONE)&&level.getBlockState(built).isAir(),"Daylight must restore both ZBB edits");
            check(level.getBlockState(playerPlace).is(Blocks.COBBLESTONE)&&level.getBlockState(playerBreak).isAir()
                &&level.getBlockState(tower).is(Blocks.COBBLESTONE),"Player-built and claimed blocks must remain untouched");
            check(level.getBlockState(overwritten).is(Blocks.DIAMOND_BLOCK)&&BlockRecovery.pending(level)==0,
                "Unrelated later block change must never be overwritten");
            var remote=new BlockPos(10000,100,10000);
            check(!level.hasChunkAt(remote),"Remote test chunk must start unloaded");
            BlockRecovery.record(level,remote,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            level.setDayTime(11*24000L+11980);BlockRecovery.process(level);
            check(!level.hasChunkAt(remote)&&state.changes.containsKey(remote.asLong()),
                "Recovery must defer unloaded chunks without force-loading them");
            BlockRecovery.forget(level,remote);
            for(int i=6;i<23;i++){
                var p=origin.east(i);level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
                BlockRecovery.record(level,p,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            }
            level.setDayTime(12*24000L+11980);BlockRecovery.process(level);
            check(BlockRecovery.pending(level)>=15,"One server tick must never exceed two block changes");
            for(int i=0;i<9;i++)BlockRecovery.process(level);
            check(BlockRecovery.pending(level)==0,"Remaining work resumes across bounded ticks");
            check(state.order.isEmpty(),"Finished work must leave no scheduler entries");
            var empty=state.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            check(empty.getList("changes",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
                "Finished repairs must not remain in serialized save data");
            var stale=origin.east(6);level.setBlockAndUpdate(stale,Blocks.AIR.defaultBlockState());
            BlockRecovery.record(level,stale,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            BlockRecovery.record(level,remote,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            var snapshot=state.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            // Older queues lack an expiry field; loading must supply a finite lifetime.
            for(var item:snapshot.getList("changes",net.minecraft.nbt.Tag.TAG_COMPOUND))
                ((net.minecraft.nbt.CompoundTag)item).remove("expires");
            var migrated=BlockRecovery.State.load(snapshot,level.registryAccess());
            check(migrated.changes.values().stream().allMatch(c->c.expires==c.due+BlockRecovery.RETENTION_TICKS),
                "Older saves must gain finite expiry");
            long expire=state.changes.values().stream().mapToLong(c->c.expires).max().orElseThrow();
            level.setDayTime(expire/24000*24000+24000+18000);BlockRecovery.process(level);
            check(state.changes.isEmpty()&&state.order.isEmpty()&&!level.hasChunkAt(remote)
                &&level.getBlockState(stale).isAir(),"Expiry must forget stale repairs even at night without touching blocks or loading chunks");
            var compact=BlockRecovery.State.load(state.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
            check(compact.changes.isEmpty()&&compact.order.isEmpty(),"Expired entries must not reappear after saving and loading");
            System.out.println("BLOCK_RECOVERY_ZBB_PLAYER_SAVE_DAYLIGHT_BUDGET_OK");
        }finally{
            zombie.discard();
            MoonCycle.score(level,"#blood_active",0);
            state.changes.clear();state.order.clear();state.changes.putAll(oldChanges);state.order.addAll(oldOrder);state.setDirty();
            for(var entry:oldBlocks.entrySet())level.setBlockAndUpdate(entry.getKey(),entry.getValue());
            level.setDayTime(time);
        }
    }
}
