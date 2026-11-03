package lunar.dread;

import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Shared inertial travel. No flight toggle, levitation potion, teleport or automatic fall rescue. */
public final class GravityPhysics {
    public static Function<LivingEntity,Vec3> clientForce=e->Vec3.ZERO;
    public static java.util.function.Predicate<LivingEntity> clientTumbling=e->false;
    public static boolean tumbling(LivingEntity e){return e.level().isClientSide?clientTumbling.test(e):MoonGravity.attached(e);}
    public static Vec3 acceleration(LivingEntity e){return e.level().isClientSide?clientForce.apply(e):MoonGravity.force(e);}
    public static boolean travel(LivingEntity e,Vec3 input){
        Vec3 force=acceleration(e);
        if(force.lengthSqr()==0||e.isInWaterOrBubble()||e.isInLava()||e.isPassenger()||e.isFallFlying())return false;
        // Very weak sideways corrections conserve drift; jump/sneak never becomes creative flight.
        double yaw=Math.toRadians(e.getYRot());
        Vec3 correction=new Vec3(input.x*Math.cos(yaw)-input.z*Math.sin(yaw),0,input.z*Math.cos(yaw)+input.x*Math.sin(yaw)).scale(.0018);
        Vec3 v=e.getDeltaMovement().scale(.997).add(force).add(correction);
        v=new Vec3(Math.clamp(v.x,-.32,.32),Math.clamp(v.y,-.30,.43),Math.clamp(v.z,-.32,.32));
        e.setDeltaMovement(v);e.move(MoverType.SELF,v);
        // Entity.move handles shape collision and fall-distance bookkeeping, including ceilings.
        if(e.verticalCollision&&v.y>0)e.setDeltaMovement(e.getDeltaMovement().multiply(1,0,1));
        if(e.horizontalCollision)e.setDeltaMovement(e.getDeltaMovement().multiply(.4,1,.4));
        return true;
    }
}
