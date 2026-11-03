package lunar.dread;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;

/** A parachuted cargo body moves through actual block collision; no duplicate accessible inventory in flight. */
public final class SupplyCrate extends Entity {
    private UUID mission;
    public SupplyCrate(EntityType<? extends SupplyCrate> type,Level l){super(type,l);}
    void mission(UUID id){mission=id;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
    @Override protected void readAdditionalSaveData(CompoundTag t){if(t.hasUUID("mission"))mission=t.getUUID("mission");}
    @Override protected void addAdditionalSaveData(CompoundTag t){if(mission!=null)t.putUUID("mission",mission);}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel l))return;
        var d=SupplyDrops.state(l).drops.stream().filter(v->v.id.equals(mission)&&v.inAir&&getUUID().equals(v.crate)).findFirst().orElse(null);
        if(d==null){discard();return;}
        setDeltaMovement(0,Math.max(-.22,getDeltaMovement().y-.018),0);move(MoverType.SELF,getDeltaMovement());
        if(onGround()||getY()<=d.pos.getY()+.05){
            if(onGround()&&getY()>d.pos.getY()+1)d.pos=blockPosition();
            if(d.status==2&&!l.getBlockState(d.pos).isAir()){
                // A builder may obstruct the landing after launch. Retry safely instead of losing the cargo.
                net.minecraft.core.BlockPos alternate=null;
                for(var p:net.minecraft.core.BlockPos.betweenClosed(d.pos.offset(-2,0,-2),d.pos.offset(2,2,2)))
                    if(l.hasChunkAt(p)&&l.getWorldBorder().isWithinBounds(p)&&l.getBlockState(p).isAir()
                        &&l.getBlockState(p.above()).isAir()&&l.getBlockState(p.below()).isSolidRender(l,p.below())){alternate=p.immutable();break;}
                if(alternate==null){setDeltaMovement(0,0,0);return;}
                d.pos=alternate;
            }
            d.inAir=false;SupplyDrops.state(l).setDirty();
            if(d.status==2&&l.getBlockState(d.pos).isAir()){l.setBlockAndUpdate(d.pos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());SupplyDrops.fill(l,d.pos,0,true);}
            l.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.CHEST_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.9f,.7f);
            MoonCycle.sync(l);discard();
        }
    }
}
