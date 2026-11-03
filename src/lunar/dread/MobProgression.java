package lunar.dread;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** One generation-time gear draw, daily base stats, and pre-armor hit ceilings. */
public final class MobProgression {
    private static final int[][] ARMOR = {
        {95,5,0,0,0}, {80,20,0,0,0}, {65,25,10,0,0}, {50,25,20,5,0},
        {35,20,30,15,0}, {25,15,25,35,0}, {15,10,25,45,5},
        {10,5,20,55,10}, {5,5,15,60,15}, {5,0,10,65,20}
    };
    private static final int[] WEAPON_CHANCE = {0,10,18,20,30,40,50,55,60,65};
    private static final Item[][] ARMOR_ITEMS = {
        {},
        {Items.LEATHER_HELMET,Items.LEATHER_CHESTPLATE,Items.LEATHER_LEGGINGS,Items.LEATHER_BOOTS},
        {Items.CHAINMAIL_HELMET,Items.CHAINMAIL_CHESTPLATE,Items.CHAINMAIL_LEGGINGS,Items.CHAINMAIL_BOOTS},
        {Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS},
        {Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS}
    };
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET
    };

    @SubscribeEvent public void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension()!=Level.OVERWORLD
            || !(event.getEntity() instanceof Mob mob)
            || !(mob instanceof Zombie || mob instanceof AbstractSkeleton)) return;
        if (event.loadedFromDisk() || !DifficultyRules.unlocked(SurvivalRules.day(level),2)
            || mob.getPersistentData().getBoolean("lunar_dread.gear_drawn")) return;
        mob.getPersistentData().putBoolean("lunar_dread.gear_drawn",true);
        int tier=DifficultyRules.tier(SurvivalRules.day(level));
        int roll=mob.getRandom().nextInt(100),kind=0;
        for(int i=0;i<ARMOR[tier-1].length;i++){
            roll-=ARMOR[tier-1][i];if(roll<0){kind=i;break;}
        }
        if(kind>0){
            // Diamond means one piece. Even the last week keeps full diamond extremely rare.
            int pieces=kind==4 ? tier==10 && mob.getTags().contains("lunar_dread.boss")
                && mob.getRandom().nextInt(100)<2 ? 4:1 : tier>=6?3:2;
            int start=mob.getRandom().nextInt(SLOTS.length);
            for(int i=0;i<pieces;i++){
                int index=(start+i)%SLOTS.length;
                if(!mob.getItemBySlot(SLOTS[index]).isEmpty())continue;
                mob.setItemSlot(SLOTS[index],new ItemStack(ARMOR_ITEMS[kind][index]));
                mob.setDropChance(SLOTS[index],0);
            }
        }
        if(mob instanceof Zombie && mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
            && mob.getRandom().nextInt(100)<WEAPON_CHANCE[tier-1]){
            Item weapon=tier>=10&&mob.getRandom().nextInt(100)<8?Items.DIAMOND_SWORD
                :tier>=4&&mob.getRandom().nextInt(100)<(tier>=6?70:15)?Items.IRON_SWORD
                :tier>=3?Items.STONE_SWORD:Items.WOODEN_SWORD;
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon));
            mob.setDropChance(EquipmentSlot.MAINHAND,0);
        }
    }

    @SubscribeEvent public void tick(EntityTickEvent.Post event){
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
            || level.dimension()!=Level.OVERWORLD || mob.tickCount%20!=0
            || !(mob instanceof AbstractSkeleton || mob instanceof Witch
                || mob.getTags().contains("lunar_dread.boss"))
            || !DifficultyRules.unlocked(SurvivalRules.day(level),2))return;
        int day=SurvivalRules.day(level);
        int key=day+(LunarDread.score(level,"#blood_active")==1?100:0);
        var data=mob.getPersistentData();
        if(data.getInt("lunar_dread.combat_key")==key)return;
        boolean fresh=!data.contains("lunar_dread.combat_key")&&mob.tickCount<=20;
        double share=mob.getTags().contains("lunar_dread.boss")?2.5
            :mob.getTags().contains("lunar_dread.air_rider")?.8:1;
        double hp=DifficultyRules.zombieHealth(day)*share
            *(key>100?DifficultyRules.bloodHealthScale(day):1);
        var max=mob.getAttribute(Attributes.MAX_HEALTH);
        if(max!=null){max.setBaseValue(hp);if(fresh)mob.setHealth(mob.getMaxHealth());
            else if(mob.getHealth()>mob.getMaxHealth())mob.setHealth(mob.getMaxHealth());}
        var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null)speed.setBaseValue(mob.getTags().contains("lunar_dread.boss")
            ?Math.min(.34,1.05*DifficultyRules.mobSpeed(day))
            :DifficultyRules.mobSpeed(day));
        if(mob.getTags().contains("lunar_dread.boss")){
            var attack=mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if(attack!=null)attack.setBaseValue(1.3*DifficultyRules.meleeDamage(day)
                *(key>100?DifficultyRules.bloodDamageScale(day):1));
        }
        data.putInt("lunar_dread.combat_key",key);
    }

    @SubscribeEvent public void cap(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)
            || !(player.level() instanceof ServerLevel level) || level.dimension()!=Level.OVERWORLD
            || !(event.getSource().getEntity() instanceof Mob attacker)
            || !(attacker instanceof Enemy) || !DifficultyRules.unlocked(SurvivalRules.day(level),2))return;
        boolean melee=event.getSource().getDirectEntity()==attacker;
        boolean arrow=event.getSource().getDirectEntity() instanceof AbstractArrow
            && attacker instanceof AbstractSkeleton;
        if(!melee&&!arrow)return;
        int day=SurvivalRules.day(level);
        double cap=DifficultyRules.meleeDamage(day)
            *(LunarDread.score(level,"#blood_active")==1?DifficultyRules.bloodDamageScale(day):1);
        if(attacker instanceof HorrorMob || attacker.getTags().contains("lunar_dread.boss"))cap*=1.3;
        else if(attacker.getTags().contains("lunar_dread.runner"))cap*=.9;
        if(arrow){
            // Skeleton arrows have no attack-damage attribute; give them the
            // daily target before armor/shields instead of only capping vanilla.
            cap*=attacker.getTags().contains("lunar_dread.air_rider")?.85:.9;
            event.setAmount((float)cap);
        }else if(event.getAmount()>cap)event.setAmount((float)cap);
    }
}
