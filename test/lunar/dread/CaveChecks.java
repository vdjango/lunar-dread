package lunar.dread;

import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Isolated real-world W2 cave placement and all-player distance check. */
public final class CaveChecks {
    public static void run(ServerPlayer player) {
        var level=player.serverLevel();
        long time=level.getDayTime();Vec3 oldPos=player.position();
        var prior=new LinkedHashMap<BlockPos,BlockState>();
        HorrorMob scout=null;
        try {
            level.setDayTime(5*24000L+1000);
            player.moveTo(260.5,25,260.5);
            for(int x=292;x<=296;x++)for(int z=258;z<=262;z++)for(int y=24;y<=35;y++){
                var block=new BlockPos(x,y,z);prior.put(block,level.getBlockState(block));
                level.setBlockAndUpdate(block,(y==24||y>=27?Blocks.STONE:Blocks.AIR).defaultBlockState());
            }
            var cave=HorrorDirector.caveSpot(level,294,25,260);
            if(cave==null||player.distanceToSqr(Vec3.atBottomCenterOf(cave))<24*24)
                throw new IllegalStateException("Distant covered cave floor was not found");
            scout=HorrorDirector.spawn(level,player,1,cave);
            if(scout==null||scout.kind()!=1||scout.getType()!=MoonContent.DIGGER.get())
                throw new IllegalStateException("W2 spine digger did not enter a legal distant cave");
            scout.discard();scout=null;
            player.moveTo(285.5,25,260.5);
            if(HorrorDirector.spawn(level,player,1,cave)!=null)
                throw new IllegalStateException("Digger spawned inside a player's 24-block protection radius");
            System.out.println("W2_CAVE_REAL_DIGGER_DISTANCE_OK");
        } finally {
            if(scout!=null)scout.discard();
            for(var entry:prior.entrySet())level.setBlockAndUpdate(entry.getKey(),entry.getValue());
            player.moveTo(oldPos.x,oldPos.y,oldPos.z);level.setDayTime(time);
        }
    }
}
