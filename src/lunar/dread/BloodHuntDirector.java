package lunar.dread;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A saved player clock and a finite, real excavation order, rather than teleported attackers. */
public final class BloodHuntDirector {
    public static int threshold(int day,int count){return Math.max(75,150-10*Math.max(0,count-1)-5*Math.max(0,(day-1)/20))*20;}
    public static boolean assigned(Mob m){return m.getPersistentData().getLong("lunar_dread.hunt_until")>m.level().getGameTime();}
    static void combat(ServerPlayer p){
        var d=p.getPersistentData();d.putInt("lunar_dread.unhurt",0);
        if(d.getLong("lunar_dread.hunt_order")>p.level().getGameTime())d.putLong("lunar_dread.hunt_order",Math.min(d.getLong("lunar_dread.hunt_order"),p.level().getGameTime()+300));
    }
    @SubscribeEvent public void hurt(LivingDamageEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||e.getNewDamage()<2)return;
        var source=e.getSource().getEntity();
        if(source instanceof Mob m&&(m instanceof Enemy||AnimalCombat.aggressive(m)))combat(p);
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public void shield(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent e){
        if(!e.isCanceled()&&e.getBlocked()&&e.getBlockedDamage()>0&&e.getEntity() instanceof ServerPlayer p
            &&e.getDamageSource().getEntity() instanceof Mob m&&(m instanceof Enemy||AnimalCombat.aggressive(m)))combat(p);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)p.getPersistentData().putLong("lunar_dread.hunt_grace",p.level().getGameTime()+600);}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p){combat(p);p.getPersistentData().putLong("lunar_dread.hunt_grace",p.level().getGameTime()+600);}}
    @SubscribeEvent public void clone(PlayerEvent.Clone e){var old=e.getOriginal().getPersistentData();var d=e.getEntity().getPersistentData();d.putInt("lunar_dread.unhurt",old.getInt("lunar_dread.unhurt"));d.putLong("lunar_dread.hunt_cooldown",old.getLong("lunar_dread.hunt_cooldown"));}
    static boolean order(ServerPlayer p){
        var l=p.serverLevel();long now=l.getGameTime();
        int orders=0,assigned=0;for(var q:l.players())if(q.getPersistentData().getLong("lunar_dread.hunt_order")>now)orders++;
        for(var e:l.getAllEntities())if(e instanceof Mob m&&assigned(m))assigned++;
        if(orders>=2||assigned>=12||p.getPersistentData().getLong("lunar_dread.hunt_order")>now)return false;
        var candidates=new ArrayList<>(l.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(96),m->m instanceof Enemy&&!m.isNoAi()&&m.isAlive()&&!assigned(m)&&!m.isVehicle()&&m.distanceToSqr(p)>=32*32));
        candidates.sort(Comparator.comparingDouble(m->m.distanceToSqr(p)));
        int wanted=Math.min(6,3+LunarDread.score(l,"#blood_count")/2),count=0;
        for(var m:candidates){if(count>=wanted||assigned+count>=12)break;assign(m,p,now);count++;}
        // At most two reinforcements, sharing the lunar and normal population caps.
        for(int i=0;i<2&&count<wanted;i++){
            double a=l.random.nextDouble()*Math.PI*2;int r=32+l.random.nextInt(25);
            BlockPos pos=SurvivalRules.surface(l,p.getBlockX()+(int)(Math.cos(a)*r),p.getBlockZ()+(int)(Math.sin(a)*r));
            if(pos==null)continue;var m=HorrorDirector.spawn(l,p,1,pos);if(m!=null){assign(m,p,now);count++;}
        }
        if(count==0){p.getPersistentData().putLong("lunar_dread.hunt_retry",now+200);return false;}
        p.getPersistentData().putLong("lunar_dread.hunt_order",now+1280);p.getPersistentData().putLong("lunar_dread.hunt_cooldown",now+2180);
        HorrorCueDirector.cue(p,MoonContent.SCRAPE.get(),.7f,.6f);return true;
    }
    private static void assign(Mob m,ServerPlayer p,long now){var d=m.getPersistentData();d.putUUID("lunar_dread.hunt_player",p.getUUID());d.putLong("lunar_dread.hunt_start",now+80);d.putLong("lunar_dread.hunt_until",now+1280);d.remove("lunar_dread.bait_until");}
    @SubscribeEvent public void mob(EntityTickEvent.Post e){
        if(!(e.getEntity() instanceof Mob m)||!(m.level() instanceof ServerLevel l)||!assigned(m))return;
        var d=m.getPersistentData();if(!LivingMoon.active(l)){d.remove("lunar_dread.hunt_until");return;}
        var p=l.getServer().getPlayerList().getPlayer(d.getUUID("lunar_dread.hunt_player"));
        if(p==null||p.level()!=l||!LunarDread.player(p)){d.remove("lunar_dread.hunt_until");return;}
        if(p.getPersistentData().getLong("lunar_dread.hunt_order")<=l.getGameTime()){d.remove("lunar_dread.hunt_until");return;}
        if(l.getGameTime()<d.getLong("lunar_dread.hunt_start")){m.getNavigation().stop();return;}
        if(m.tickCount%20==0){m.setTarget(p);m.getNavigation().moveTo(p,1.25);}
        HuntDigging.tick(m,p,false);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        if(e.getServer().getTickCount()%20!=0)return;
        for(var p:e.getServer().getPlayerList().getPlayers()){
            if(!LunarDread.player(p))continue;var l=p.serverLevel();var d=p.getPersistentData();long now=l.getGameTime();
            if(!LivingMoon.active(l)){d.putInt("lunar_dread.unhurt",0);d.remove("lunar_dread.hunt_order");continue;}
            if(now<d.getLong("lunar_dread.hunt_grace"))continue;
            boolean close=false;for(var m:l.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(8),m->m instanceof Enemy&&m.isAlive()&&!m.isNoAi()&&m.getTarget()==p&&m.hasLineOfSight(p))) {close=true;break;}
            int contact=close?d.getInt("lunar_dread.hunt_contact")+20:0;d.putInt("lunar_dread.hunt_contact",contact);
            if(contact>=60){combat(p);continue;}
            d.putInt("lunar_dread.unhurt",Math.min(24000,d.getInt("lunar_dread.unhurt")+20));
            if(d.getInt("lunar_dread.unhurt")>=threshold(SurvivalRules.day(l),LunarDread.score(l,"#blood_count"))
                &&now>=d.getLong("lunar_dread.hunt_cooldown")&&now>=d.getLong("lunar_dread.hunt_retry")&&!MoonIncidents.busy(p))order(p);
        }
    }
}
