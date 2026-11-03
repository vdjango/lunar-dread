package lunar.dread;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** A real, shootable canopy hitbox; rendered together with the owner's cloth/wing mesh. */
public final class MoonCanopy extends Entity {
    private UUID owner;
    public MoonCanopy(EntityType<? extends MoonCanopy> type,Level level){super(type,level);setNoGravity(true);}
    public void owner(Mob mob){owner=mob.getUUID();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
    @Override protected void readAdditionalSaveData(CompoundTag t){if(t.hasUUID("owner"))owner=t.getUUID("owner");}
    @Override protected void addAdditionalSaveData(CompoundTag t){if(owner!=null)t.putUUID("owner",owner);}
    @Override public boolean isPickable(){return true;}
    @Override public boolean hurt(DamageSource source,float amount){
        if(!(level() instanceof ServerLevel l)||owner==null||!(l.getEntity(owner) instanceof Mob mob))return false;
        if(source.getEntity() instanceof LivingEntity&&!(source.getEntity() instanceof net.minecraft.world.entity.player.Player))return false;
        var d=mob.getPersistentData();d.putFloat("lunar_dread.wing_hp",Math.max(0,d.getFloat("lunar_dread.wing_hp")-Math.max(1,amount)));
        l.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.WOOL_BREAK,net.minecraft.sounds.SoundSource.HOSTILE,1,.6f);
        if(d.getFloat("lunar_dread.wing_hp")<=0){d.remove("lunar_dread.wing_until");discard();}return true;
    }
    @Override public void tick(){
        super.tick();if(level() instanceof ServerLevel l){
            var e=owner==null?null:l.getEntity(owner);
            if(!(e instanceof Mob mob)||!mob.isAlive()||mob.getPersistentData().getLong("lunar_dread.wing_until")<=l.getGameTime()){discard();return;}
            setPos(mob.getX(),mob.getY()+mob.getBbHeight()+.5,mob.getZ());
        }
    }
}
