package lunar.dread;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Cached callers classify real cover, not an arbitrary absolute Y or any wooden roof. */
public final class BloodFogProfile {
    public record Range(float near,float far) {}
    public static float depth(Level level,BlockPos pos) {
        if(!level.hasChunkAt(pos)||level.canSeeSky(pos))return 0;
        int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
        int stone=0;
        for(int y=1;y<=16&&pos.getY()+y<level.getMaxBuildHeight();y++){
            var at=pos.above(y);var s=level.getBlockState(at);
            if(s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.DIRT)||s.is(BlockTags.SAND))stone++;
        }
        if(stone<3)return .08f;
        float buried=Math.clamp((surface-pos.getY()-3)/13f,0,1)*Math.clamp(stone/8f,0,1);
        int openings=0;
        for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){
            var at=pos.relative(d,6);
            if(level.hasChunkAt(at)&&level.canSeeSky(at))openings++;
        }
        return buried*(1-openings*.16f);
    }
    public static Range range(float growth,float depth){
        depth=Math.clamp(depth,0,1);growth=Math.clamp(growth,0,1);
        float[] knots={0,.08f,.5f,1},near={6-2*growth,6-2*growth,3-growth,1.5f-.5f*growth},far={32-8*growth,28-6*growth,18-4*growth,10-3*growth};
        for(int i=1;i<knots.length;i++)if(depth<=knots[i]){
            float t=(depth-knots[i-1])/(knots[i]-knots[i-1]);
            return new Range(near[i-1]+t*(near[i]-near[i-1]),far[i-1]+t*(far[i]-far[i-1]));
        }
        return new Range(near[3],far[3]);
    }
}
