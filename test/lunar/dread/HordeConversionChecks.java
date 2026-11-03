package lunar.dread;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.smileycorp.hordes.common.capability.HordesCapabilities;
import net.smileycorp.hordes.hordeevent.HordeSpawnData;
import net.smileycorp.hordes.hordeevent.capability.HordeEvent;
import net.smileycorp.hordes.hordeevent.capability.HordeSavedData;

/** Reproduce the logged vanilla conversion with an active horde and an offline owner. */
public final class HordeConversionChecks {
    private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    public static void run(ServerPlayer player)throws Exception{
        var level=player.serverLevel();long time=level.getDayTime();
        var entities=new java.util.ArrayList<Mob>();var missing=UUID.randomUUID();
        var data=HordeSavedData.getData(level);var event=data.getEvent(missing);
        var timer=HordeEvent.class.getDeclaredField("timer");timer.setAccessible(true);
        var spawn=HordeEvent.class.getDeclaredField("spawnData");spawn.setAccessible(true);
        try{
            level.setDayTime(9*24000L+18000);
            timer.setInt(event,1000);spawn.set(event,new HordeSpawnData(event));
            check(event.isActive()&&event.getSpawnData()!=null,"Regression must exercise an active horde");
            check(level.getServer().getPlayerList().getPlayer(missing)==null,"Owner must really be offline");
            var direct=EntityType.DROWNED.create(level);entities.add(direct);
            var cap=direct.getCapability(HordesCapabilities.HORDESPAWN);
            check(cap!=null,"Hordes capability must be installed");
            cap.setPlayerUUID(missing.toString());event.registerEntity(direct,null);
            check(!cap.isHordeSpawned(),"Missing-owner registration must clear ownership safely");
            var zombie=EntityType.ZOMBIE.create(level);entities.add(zombie);
            zombie.moveTo(278.5,100,278.5);zombie.setNoAi(true);level.addFreshEntity(zombie);
            zombie.getCapability(HordesCapabilities.HORDESPAWN).setPlayerUUID(missing.toString());
            var drowned=zombie.convertTo(EntityType.DROWNED,true);
            check(drowned!=null&&drowned.getType()==EntityType.DROWNED,"Vanilla conversion must still finish");
            entities.add(drowned);
            check(!drowned.getCapability(HordesCapabilities.HORDESPAWN).isHordeSpawned(),
                "Converted orphan must not retain a missing owner");
            event.registerEntity(direct,player);
            check(cap.getPlayerUUID().equals(player.getUUID().toString()),"Online-owner registration must retain vanilla Hordes behavior");
            event.removeEntity(direct);
            System.out.println("HORDE_OFFLINE_OWNER_REAL_CONVERSION_OK");
        }finally{
            for(var entity:entities)entity.discard();
            var events=HordeSavedData.class.getDeclaredField("events");events.setAccessible(true);
            ((java.util.Map<?,?>)events.get(data)).remove(missing);data.setDirty();
            level.setDayTime(time);
        }
    }
}
