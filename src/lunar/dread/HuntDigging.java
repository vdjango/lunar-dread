package lunar.dread;

import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Real, bounded excavation. Containers and third-party block entities are never excavated. */
public final class HuntDigging {
    private static final class Budget {long second=-1;int examined,broken;}
    private static final WeakHashMap<ServerLevel,Budget> budgets=new WeakHashMap<>();
    public static boolean breakable(ServerLevel l,BlockPos p){
        if(!l.hasChunkAt(p)||!l.getWorldBorder().isWithinBounds(p)||l.getBlockEntity(p)!=null)return false;
        var s=l.getBlockState(p);float h=s.getDestroySpeed(l,p);
        return !s.isAir()&&!s.getCollisionShape(l,p).isEmpty()&&h>=0&&h<=8
            &&!s.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE)&&!s.hasBlockEntity();
    }
    static boolean permitted(ServerLevel l,Mob m,BlockPos p){
        return l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)&&breakable(l,p)
            &&net.neoforged.neoforge.event.EventHooks.canEntityGrief(l,m)
            &&l.getBlockState(p).canEntityDestroy(l,p,m);
    }
    public static void tick(Mob m,ServerPlayer target,boolean build){
        if(!(m.level() instanceof ServerLevel l)||m.tickCount%5!=0||target==null)return;
        var b=budgets.computeIfAbsent(l,k->new Budget());long second=l.getGameTime()/20;
        if(b.second!=second){b.second=second;b.examined=b.broken=0;}
        var d=m.getPersistentData();BlockPos mining=d.contains("lunar_dread.dig_pos")?BlockPos.of(d.getLong("lunar_dread.dig_pos")):null;
        if(mining!=null){
            if(m.distanceToSqr(Vec3.atCenterOf(mining))>9||!permitted(l,m,mining)){
                l.destroyBlockProgress(m.getId(),mining,-1);d.remove("lunar_dread.dig_pos");return;
            }
            int progress=(int)(l.getGameTime()-d.getLong("lunar_dread.dig_start"));
            int duration=m instanceof net.minecraft.world.entity.animal.goat.Goat?20:30;
            l.destroyBlockProgress(m.getId(),mining,Math.min(9,progress*10/duration));
            if(progress>=duration&&b.broken<4){
                b.broken++;l.destroyBlockProgress(m.getId(),mining,-1);
                var old=l.getBlockState(mining);
                if(net.neoforged.neoforge.event.EventHooks.onEntityDestroyBlock(m,mining,old)
                    &&l.destroyBlock(mining,true,m))BlockRecovery.record(l,mining,old,l.getBlockState(mining));
                d.remove("lunar_dread.dig_pos");
            }return;
        }
        Vec3 delta=target.position().subtract(m.position());
        Vec3 dir=delta.multiply(1,0,1).normalize();
        BlockPos front=BlockPos.containing(m.position().add(dir.scale(.95)));
        boolean gap=build&&l.hasChunkAt(front.below())&&l.getBlockState(front.below()).canBeReplaced();
        if(!gap&&!m.horizontalCollision&&!m.getNavigation().isDone()&&target.getY()<=m.getY()+2)return;
        if(b.examined>=8)return;b.examined++;
        BlockPos[] candidates=delta.y< -1.5?new BlockPos[]{front.below(),front,front.above()}:new BlockPos[]{front,front.above(),front.above(2)};
        // Doors are considered first, but still receive a visible windup.
        for(var p:candidates)if(l.getBlockState(p).getBlock() instanceof DoorBlock&&permitted(l,m,p)){start(l,m,p);return;}
        for(var p:candidates)if(permitted(l,m,p)){start(l,m,p);return;}
        if(!build||b.broken>=4||!l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||!net.neoforged.neoforge.event.EventHooks.canEntityGrief(l,m))return;
        BlockPos support=front.below();
        if(target.getY()>m.getY()+1.5&&m.onGround()){
            // A jump followed by a real support block, never a teleport up a tower.
            m.setDeltaMovement(m.getDeltaMovement().add(0,.48,0));support=m.blockPosition();
        }
        if(l.hasChunkAt(support)&&l.getWorldBorder().isWithinBounds(support)&&l.getBlockEntity(support)==null
            &&l.getBlockState(support).canBeReplaced()&&l.getFluidState(support).isEmpty()
            &&l.getEntities(null,new net.minecraft.world.phys.AABB(support)).isEmpty()){
            var old=l.getBlockState(support);
            if(l.setBlockAndUpdate(support,net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState())){
                b.broken++;BlockRecovery.record(l,support,old,l.getBlockState(support));
            }
        }
    }
    private static void start(ServerLevel l,Mob m,BlockPos p){
        m.getPersistentData().putLong("lunar_dread.dig_pos",p.asLong());m.getPersistentData().putLong("lunar_dread.dig_start",l.getGameTime());
        l.playSound(null,p,MoonContent.SCRAPE.get(),SoundSource.HOSTILE,.8f,.7f);
    }
}
