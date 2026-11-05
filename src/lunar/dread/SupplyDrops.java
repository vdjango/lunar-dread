package lunar.dread;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native, persistent air-drop missions. A guard belongs to its own mission, never all guards globally. */
public final class SupplyDrops {
    private static long lastFailure=-1200;
    static final class Drop {
        UUID id=UUID.randomUUID(),guard,marker,crate;BlockPos pos;int status,tier;long expires;boolean killed,inAir;
        CompoundTag save(){var t=new CompoundTag();t.putUUID("id",id);if(guard!=null)t.putUUID("guard",guard);if(marker!=null)t.putUUID("marker",marker);
            t.putLong("pos",pos.asLong());t.putInt("status",status);t.putInt("tier",tier);t.putLong("expires",expires);t.putBoolean("killed",killed);t.putBoolean("inAir",inAir);if(crate!=null)t.putUUID("crate",crate);return t;}
        static Drop load(CompoundTag t){var d=new Drop();d.id=t.getUUID("id");if(t.hasUUID("guard"))d.guard=t.getUUID("guard");if(t.hasUUID("marker"))d.marker=t.getUUID("marker");
            d.pos=BlockPos.of(t.getLong("pos"));d.status=t.getInt("status");d.tier=t.getInt("tier");d.expires=t.getLong("expires");d.killed=t.getBoolean("killed");d.inAir=t.getBoolean("inAir");if(t.hasUUID("crate"))d.crate=t.getUUID("crate");return d;}
    }
    public static final class State extends SavedData {
        final List<Drop> drops=new ArrayList<>();
        static State load(CompoundTag t,HolderLookup.Provider r){var s=new State();for(var e:t.getList("drops",10)){if(s.drops.size()==4)break;s.drops.add(Drop.load((CompoundTag)e));}return s;}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){var list=new ListTag();drops.forEach(d->list.add(d.save()));t.put("drops",list);return t;}
    }
    static State state(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new,State::load,null),"lunar_dread_supply");}
    public static List<MoonUpdates.Beacon> beacons(ServerLevel l){return state(l).drops.stream().map(d->new MoonUpdates.Beacon(d.pos,d.inAir?3:d.status,d.tier)).toList();}
    static boolean guarded(ServerLevel l){return state(l).drops.stream().anyMatch(d->d.status==0);}
    public static boolean spawn(ServerLevel l,Vec3 origin,int tier,boolean star){
        if(l.dimension()!=net.minecraft.world.level.Level.OVERWORLD||(!star&&guarded(l)))return false;
        for(int i=0;i<64;i++){
            double angle=l.random.nextDouble()*Math.PI*2,range=28+l.random.nextInt(45);
            var pos=SurvivalRules.surface(l,(int)Math.floor(origin.x+Math.cos(angle)*range),(int)Math.floor(origin.z+Math.sin(angle)*range));
            if(pos==null||!l.getBlockState(pos).isAir()||!l.getBlockState(pos.above()).isAir()||l.getBlockEntity(pos.below())!=null)continue;
            if(create(l,pos,tier,star,null)){launch(l,state(l).drops.getLast());return true;}
        }
        if(l.getGameTime()-lastFailure>=1200){lastFailure=l.getGameTime();for(var p:l.players())p.sendSystemMessage(Component.literal("【空投信号】附近没有可安全投放的已加载空地，正在等待下次投放。"));}
        return false;
    }
    static boolean create(ServerLevel l,BlockPos pos,int tier,boolean star,Entity adopted){
        var s=state(l);Drop old=null;if(s.drops.size()>=4){old=s.drops.stream().filter(d->d.status!=0).findFirst().orElse(null);if(old==null)return false;}
        if(!l.hasChunkAt(pos)||!l.getBlockState(pos).isAir())return false;
        var d=new Drop();d.pos=pos.immutable();d.tier=tier;d.status=star?2:0;d.expires=l.getGameTime()+12000;
        if(!star){
            Mob guard=DifficultyRules.week(SurvivalRules.day(l))<=5
                ?new Zombie(EntityType.ZOMBIE,l):new Ravager(EntityType.RAVAGER,l);
            guard.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);guard.addTag("lunar_dread.supply_guard");
            guard.getPersistentData().putUUID("lunar_dread.supply_id",d.id);guard.setPersistenceRequired();guard.setCustomName(Component.literal("空投守卫"));
            guard.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,12000,0,false,false));
            if(!l.noCollision(guard)||!l.addFreshEntity(guard))return false;d.guard=guard.getUUID();
            Entity marker=adopted instanceof Marker?adopted:null;
            if(marker==null){var m=new Marker(EntityType.MARKER,l);m.moveTo(Vec3.atCenterOf(pos));m.addTag("lunar_dread.supply_marker");l.addFreshEntity(m);marker=m;}
            d.marker=marker.getUUID();
        }else{
            l.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());fill(l,pos,0,true);d.expires=l.getGameTime()+6000;
        }
        if(old!=null)s.drops.remove(old);s.drops.add(d);s.setDirty();
        if(!star){MoonCycle.score(l,"#supply_active",1);MoonCycle.score(l,"#last_supply",SurvivalRules.day(l));}
        publishCoords(l,d);
        for(var p:l.players()){
            p.sendSystemMessage(Component.literal((star?"【星砂落点】":"【空投信号】")+"X="+pos.getX()+" Y="+pos.getY()+" Z="+pos.getZ()+(star?"；跟随银蓝光柱，小箱无需守卫。":"；跟随金色光柱和屏幕方向，先击败守卫。")));
            p.playNotifySound(SoundEvents.BEACON_ACTIVATE,SoundSource.NEUTRAL,.7f,.85f);
        }
        MoonCycle.sync(l);return true;
    }
    private static void publishCoords(ServerLevel l,Drop d){MoonCycle.score(l,"#supply_x",d.pos.getX());MoonCycle.score(l,"#supply_y",d.pos.getY());MoonCycle.score(l,"#supply_z",d.pos.getZ());}
    private static ItemStack optionalItem(String namespace,String id,int count){
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace,id));
        return item==Items.AIR?ItemStack.EMPTY:new ItemStack(item,count);
    }
    static void fill(ServerLevel l,BlockPos pos,int tier,boolean star){
        if(!(l.getBlockEntity(pos) instanceof ChestBlockEntity chest))return;
        chest.setItem(0,new ItemStack(Items.IRON_INGOT,star?2:tier==1?8:4));chest.setItem(1,new ItemStack(Items.COPPER_INGOT,star?3:tier==1?10:6));
        chest.setItem(2,new ItemStack(Items.REDSTONE,star?2:tier==1?6:4));chest.setItem(3,new ItemStack(Items.COOKED_BEEF,star?2:4));
        var turretAmmo=optionalItem("openmodularturrets","ammo_bullet",star?8:tier==1?32:16);
        if(!turretAmmo.isEmpty())chest.setItem(4,turretAmmo);
        int day=SurvivalRules.day(l);
        if(star){
            chest.setItem(5,new ItemStack(Items.BOW));
            chest.setItem(6,new ItemStack(Items.ARROW,12));
        }else if(tier>=1){
            chest.setItem(5,new ItemStack(Items.ARROW,24));
            var gun=optionalItem("musketmod",day>=30?"musket":"pistol",1);
            var cartridges=optionalItem("musketmod","cartridge",day>=30?20:12);
            if(gun.isEmpty()||cartridges.isEmpty()){
                chest.setItem(6,new ItemStack(Items.CROSSBOW));
                chest.setItem(7,new ItemStack(Items.ARROW,16));
            }else{
                chest.setItem(6,gun);
                chest.setItem(7,cartridges);
            }
            if(day>=30)chest.setItem(8,new ItemStack(Items.DIAMOND));
        }else{
            chest.setItem(5,new ItemStack(Items.ARROW,16));
            chest.setItem(6,new ItemStack(day>=30?Items.CROSSBOW:Items.BOW));
        }
        chest.setChanged();
    }
    static boolean unlock(ServerLevel l,Drop d){
        if(d.status!=0||!d.killed||d.inAir||!l.hasChunkAt(d.pos))return false;
        // A player may have built over the original beacon. Never overwrite their block/entity.
        if(!l.getBlockState(d.pos).isAir()){
            BlockPos alternate=null;for(var p:BlockPos.betweenClosed(d.pos.offset(-2,0,-2),d.pos.offset(2,1,2)))if(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir()&&l.getBlockState(p.below()).isSolidRender(l,p.below())){alternate=p.immutable();break;}
            if(alternate==null)return false;d.pos=alternate;
        }
        l.setBlockAndUpdate(d.pos,Blocks.CHEST.defaultBlockState());fill(l,d.pos,d.tier,false);d.status=1;d.expires=l.getGameTime()+12000;
        if(d.marker!=null){var marker=l.getEntity(d.marker);if(marker!=null)marker.discard();}
        state(l).setDirty();MoonCycle.score(l,"#supply_active",0);publishCoords(l,d);
        for(var p:l.players())p.sendSystemMessage(Component.literal("【守卫已击败】补给箱已解锁；绿色光柱在领完后消失，信号最长保留10分钟。X="+d.pos.getX()+" Y="+d.pos.getY()+" Z="+d.pos.getZ()));
        MoonCycle.sync(l);return true;
    }
    @SubscribeEvent public void death(LivingDeathEvent e){
        if(!(e.getEntity().level() instanceof ServerLevel l)||l.dimension()!=net.minecraft.world.level.Level.OVERWORLD)return;
        for(var d:state(l).drops)if(e.getEntity().getUUID().equals(d.guard)){d.killed=true;state(l).setDirty();}
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        var l=e.getServer().overworld();if(l.getGameTime()%20!=0)return;var s=state(l);boolean dirty=false;
        var it=s.drops.iterator();while(it.hasNext()){
            var d=it.next();if(d.status==0&&d.killed){unlock(l,d);dirty=true;}
            if(d.inAir&&l.hasChunkAt(d.pos)&&l.getEntity(d.crate)==null){launch(l,d);dirty=true;}
            boolean empty=false;
            if(!d.inAir&&d.status!=0&&l.hasChunkAt(d.pos))empty=!(l.getBlockEntity(d.pos) instanceof ChestBlockEntity chest)||chest.isEmpty();
            if(empty||l.getGameTime()>=d.expires){
                if(d.status==0){var guard=d.guard==null?null:l.getEntity(d.guard);if(guard!=null)guard.discard();var marker=d.marker==null?null:l.getEntity(d.marker);if(marker!=null)marker.discard();MoonCycle.score(l,"#supply_active",0);}
                it.remove();dirty=true;
            }
        }
        if(dirty)s.setDirty();
    }
    private static void launch(ServerLevel l,Drop d){
        if(!d.inAir&&d.status==2&&l.getBlockEntity(d.pos) instanceof ChestBlockEntity chest){chest.clearContent();l.setBlockAndUpdate(d.pos,Blocks.AIR.defaultBlockState());}
        var crate=MoonContent.CRATE.get().create(l);if(crate==null)return;
        crate.mission(d.id);crate.moveTo(d.pos.getX()+.5,Math.min(l.getMaxBuildHeight()-4,d.pos.getY()+36),d.pos.getZ()+.5);
        if(l.addFreshEntity(crate)){d.inAir=true;d.crate=crate.getUUID();state(l).setDirty();}
    }
    @SubscribeEvent public void loaded(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){
        if(!e.loadedFromDisk()||!(e.getLevel() instanceof ServerLevel l))return;
        var entity=e.getEntity();var d=entity.getPersistentData();
        if(entity.getTags().contains("lunar_dread.supply_guard")&&d.hasUUID("lunar_dread.supply_id")&&state(l).drops.stream().noneMatch(drop->drop.id.equals(d.getUUID("lunar_dread.supply_id"))))entity.discard();
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("lunardread").then(Commands.literal("supply")
            .then(Commands.literal("tutorial").requires(s->s.hasPermission(2)).executes(c->{
                var l=c.getSource().getServer().overworld();
                if(!DifficultyRules.safe(SurvivalRules.day(l))||DifficultyRules.withinWeek(SurvivalRules.day(l))!=4)return 0;
                boolean ok=spawn(l,c.getSource().getPosition(),0,true);
                if(ok)MoonCycle.score(l,"#last_supply",SurvivalRules.day(l));
                return ok?1:0;
            }))
            .then(Commands.literal("locate").executes(c->{
                if(c.getSource().getEntity() instanceof ServerPlayer p)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new MoonUpdates.Navigation(0));
                var l=c.getSource().getServer().overworld();var drops=state(l).drops;
                if(drops.isEmpty())c.getSource().sendSuccess(()->Component.literal("当前没有正在发信号的空投。"),false);
                for(var d:drops)c.getSource().sendSuccess(()->Component.literal("主世界 X="+d.pos.getX()+" Y="+d.pos.getY()+" Z="+d.pos.getZ()+(d.inAir?" · 货箱降落中":d.status==0?" · 守卫未清除":" · 可领取")+"。沿屏幕指向接近；跨山或隔墙时先绕路。"),false);
                return drops.size();
            }))
            .then(Commands.literal("track").executes(c->{if(c.getSource().getEntity() instanceof ServerPlayer p)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new MoonUpdates.Navigation(1));return 1;}))
            .then(Commands.literal("spawn").requires(s->s.hasPermission(2)).executes(c->{var l=c.getSource().getServer().overworld();return spawn(l,c.getSource().getPosition(),LunarDread.score(l,"#supply_tier"),false)?1:0;}))
            .then(Commands.literal("prepare").requires(s->s.hasPermission(2)).executes(c->{var l=c.getSource().getServer().overworld();return create(l,BlockPos.containing(c.getSource().getPosition()),LunarDread.score(l,"#supply_tier"),false,c.getSource().getEntity())?1:0;}))
            .then(Commands.literal("check").requires(s->s.hasPermission(2)).executes(c->{var l=c.getSource().getServer().overworld();int n=0;for(var d:state(l).drops)if(unlock(l,d))n++;return n;}))));
    }
}
