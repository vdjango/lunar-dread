package lunar.dread;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/** Shared locomotion, five distinct bodies and interactions; no boss overlay. */
public final class HorrorMob extends Monster {
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(HorrorMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROPE=SynchedEntityData.defineId(HorrorMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> LIFT=SynchedEntityData.defineId(HorrorMob.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> MIRROR=SynchedEntityData.defineId(HorrorMob.class,EntityDataSerializers.OPTIONAL_UUID);
    private final int kind;
    private int windup,carryTicks,stagger;
    private Vec3 carryStart=Vec3.ZERO;
    private float escapeDamage;
    private long lastEscape=-100;
    public HorrorMob(EntityType<? extends HorrorMob> type,net.minecraft.world.level.Level level,int kind){super(type,level);this.kind=kind;xpReward=12;}
    public int kind(){return kind;}
    public int phase(){return entityData.get(PHASE);}
    public int ropeTarget(){return entityData.get(ROPE);}
    public java.util.UUID mirror(){return entityData.get(MIRROR).orElse(null);}
    public void mirror(java.util.UUID id){entityData.set(MIRROR,java.util.Optional.of(id));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(PHASE,0);b.define(ROPE,-1);b.define(LIFT,0L);b.define(MIRROR,java.util.Optional.empty());}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new MeleeAttackGoal(this,1.25,true));targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(this,net.minecraft.world.entity.player.Player.class,true));}
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,50).add(Attributes.MOVEMENT_SPEED,.38).add(Attributes.ATTACK_DAMAGE,6).add(Attributes.FOLLOW_RANGE,96).add(Attributes.KNOCKBACK_RESISTANCE,.45);}
    @Override public boolean canAttack(LivingEntity e){return !isVehicle()&&e instanceof net.minecraft.world.entity.player.Player&&super.canAttack(e);}
    @Override public boolean doHurtTarget(Entity e){return phase()==0&&super.doHurtTarget(e);}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel l))return;
        if(stagger>0){stagger--;getNavigation().stop();return;}
        var passenger=getFirstPassenger();
        if((kind==17||kind==18)&&getTarget() instanceof ServerPlayer target&&tickCount%20==0&&!MoonGravity.busy(target)&&!MoonIncidents.sensoryBusy(target)){
            if(kind==17&&!onGround())setDeltaMovement(getDeltaMovement().multiply(1,.7,1).add(0,.025,0));
            if(distanceToSqr(target)<12*12&&hasLineOfSight(target)&&level().getGameTime()%120==0){var t=new MoonGravity.Tide();t.center=position();t.start=level().getGameTime();t.duration=100;t.height=4;MoonGravity.attach(target,t,l);}
        }
        if(passenger instanceof ServerPlayer victim){
            carryTicks++;entityData.set(PHASE,2);
            if(!LunarDread.player(victim)||carryTicks>=140||position().distanceToSqr(carryStart)>1024||!AnimalCombat.enabled(l)){
                AbductionController.release(victim,false);return;
            }
            // Lift first; do not drag a standing player through a wall during the transition.
            if(carryTicks<=12){getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);return;}
            Vec3 shoulder=getPassengerRidingPosition(victim);
            if(!l.noCollision(victim,victim.getBoundingBox().move(shoulder.subtract(victim.position())))){
                AbductionController.release(victim,false);return;
            }
            if(carryTicks%10==3){
                Vec3 away=position().subtract(carryStart).multiply(1,0,1).normalize();
                if(away.lengthSqr()<.1)away=new Vec3(Math.cos(getYRot()*Math.PI/180),0,Math.sin(getYRot()*Math.PI/180));
                Vec3 next=position().add(away.scale(8));
                getNavigation().moveTo(next.x,next.y,next.z,carryTicks<60?1.63:1.37);
            }
            return;
        }
        if(phase()==2){entityData.set(PHASE,0);stagger=16;}
        if(!(getTarget() instanceof ServerPlayer p)||!LunarDread.player(p))return;
        if(!AnimalCombat.enabled(l)){setTarget(null);windup=0;entityData.set(PHASE,0);return;}
        if(kind==1||kind==2||kind==3)HuntDigging.tick(this,p,kind==2);
        if(kind==4){
            // Visible membrane-assisted jumps, with ordinary collision and gravity.
            if(tickCount%30==0&&p.getY()>getY()+2&&distanceToSqr(p)<32*32){
                Vec3 v=p.position().subtract(position()).normalize();setDeltaMovement(v.scale(.5).add(0,.55,0));
            }
            if(tickCount%80==0&&p.isFallFlying()&&hasLineOfSight(p)&&distanceToSqr(p)<18*18&&!MoonIncidents.busy(p)){
                getPersistentData().putLong("lunar_dread.rope_until",l.getGameTime()+30);getPersistentData().putUUID("lunar_dread.rope_player",p.getUUID());
                p.getPersistentData().putLong("lunar_dread.rope_lock",l.getGameTime()+30);p.getPersistentData().putInt("lunar_dread.rope_owner",getId());
                l.playSound(null,blockPosition(),MoonContent.RIFT.get(),net.minecraft.sounds.SoundSource.HOSTILE,.7f,.9f);
            }
            if(getPersistentData().getLong("lunar_dread.rope_until")>l.getGameTime()&&hasLineOfSight(p)&&p.getPersistentData().getLong("lunar_dread.rope_lock")>l.getGameTime()&&p.getPersistentData().getInt("lunar_dread.rope_owner")==getId()){
                entityData.set(ROPE,p.getId());
                p.setDeltaMovement(p.getDeltaMovement().add(position().subtract(p.position()).normalize().scale(.055)));p.hurtMarked=true;
            }else {entityData.set(ROPE,-1);if(p.getPersistentData().getInt("lunar_dread.rope_owner")==getId())p.getPersistentData().remove("lunar_dread.rope_lock");}
        }
        boolean grabber=kind==0||kind==3;
        if(!grabber)return;
        if(windup>0){
            getNavigation().stop();getLookControl().setLookAt(p,45,45);windup--;
            if(distanceToSqr(p)>3.2*3.2||!hasLineOfSight(p)||p.isBlocking()){windup=0;entityData.set(PHASE,0);stagger=16;return;}
            if(windup==0){entityData.set(PHASE,0);AbductionController.capture(this,p);}return;
        }
        if(distanceToSqr(p)<2.2*2.2&&hasLineOfSight(p)&&!MoonIncidents.busy(p)&&!LivingMoon.targeted(p)&&AbductionController.available(l)){
            windup=24;entityData.set(PHASE,1);l.playSound(null,blockPosition(),MoonContent.GRAB.get(),net.minecraft.sounds.SoundSource.HOSTILE,.9f,.65f);
        }
    }
    void captured(ServerPlayer p){carryTicks=0;carryStart=position();escapeDamage=0;lastEscape=-100;entityData.set(PHASE,2);entityData.set(LIFT,level().getGameTime());setTarget(null);}
    void released(){entityData.set(PHASE,0);windup=0;stagger=16;getPersistentData().remove("lunar_dread.rope_until");}
    @Override protected boolean canAddPassenger(Entity e){return getPassengers().isEmpty()&&e instanceof ServerPlayer;}
    @Override public Vec3 getPassengerRidingPosition(Entity passenger){
        double lift=Math.clamp((level().getGameTime()-entityData.get(LIFT))/12.0,0,1);
        double a=Math.toRadians(yBodyRot);return position().add(Math.cos(a)*.55,1.45*lift+Math.sin(tickCount*.65)*.055,Math.sin(a)*.55);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        float before=getHealth();boolean result=super.hurt(source,amount);float actual=Math.max(0,before-getHealth());
        if(result&&actual>0){
            getPersistentData().remove("lunar_dread.rope_until");
            if(getTarget() instanceof ServerPlayer p&&p.getPersistentData().getInt("lunar_dread.rope_owner")==getId()){p.getPersistentData().remove("lunar_dread.rope_lock");MoonIncidents.protect(p,120);}
            if(windup>0){windup=0;entityData.set(PHASE,0);stagger=16;}
            if(getFirstPassenger() instanceof ServerPlayer victim&&source.getEntity() instanceof ServerPlayer attacker){
                if(attacker==victim){
                    long now=level().getGameTime();
                    // AttackEntityEvent samples strength before vanilla resets its attack timer.
                    if(now-lastEscape>=10&&attacker.getPersistentData().getLong("lunar_dread.valid_swing")>=now-1){
                        lastEscape=now;escapeDamage+=actual;
                        if(escapeDamage>=12||random.nextFloat()<.3f)AbductionController.release(victim,false);
                    }
                }else if(actual>=4)AbductionController.release(victim,false);
            }
        }return result;
    }
    @Override public void remove(Entity.RemovalReason reason){
        if(!level().isClientSide&&getFirstPassenger() instanceof ServerPlayer p)AbductionController.release(p,false);super.remove(reason);
    }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return kind==1||kind==2?MoonContent.SCRAPE.get():kind==4?MoonContent.RIFT.get():MoonContent.WHISPER.get();}
    @Override public int getAmbientSoundInterval(){return 300;}
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s){return MoonContent.SCRAPE.get();}
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return MoonContent.WHISPER.get();}
}
