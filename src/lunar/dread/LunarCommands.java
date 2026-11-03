package lunar.dread;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.goat.Goat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit operator tools. Natural event timing and population budgets remain unchanged. */
public final class LunarCommands {
    public static final List<String> ANIMALS=List.of("goat_fire","goat_ram","goat_trap","sheep","llama",
        "chicken","cow","pig","rabbit","wolf","fox","cat","bee","hoof","panda","bear","frog","turtle","axolotl","bat","squid");
    public static final List<String> CREATURES;
    public static final List<String> EVENTS;
    static {
        var creatures=new ArrayList<>(List.of("skin_hauler","spine_digger","bent_beam","eave_watcher","tether_wing"));
        creatures.addAll(List.of(MoonContent.ACTORS));CREATURES=List.copyOf(creatures);
        var events=new ArrayList<>(List.of("moon.strike","moon.gaze","moon.blackout","moon.gravity",
            "moon.contact","blood.hunt","air.raid","supply.guarded","supply.rich","supply.star","player.cure"));
        for(String id:List.of("purple","pale","mirror","amber","tide","rust","paper"))events.add("anomaly."+id);
        for(String id:CREATURES)events.add("creature."+id);
        EVENTS=List.copyOf(events);
    }
    private static int fail(CommandSourceStack s,String text){s.sendFailure(Component.literal(text));return 0;}
    static boolean creature(ServerPlayer p,int kind){
        var l=p.serverLevel();
        if(!LunarDreadConfig.HORROR_ENCOUNTERS_ENABLED.get()||!AnimalCombat.worldActive(l))return false;
        boolean cave=BloodFogProfile.depth(l,p.blockPosition())>.45;
        for(int i=0;i<64;i++){
            double angle=l.random.nextDouble()*Math.PI*2;int r=28+l.random.nextInt(21);
            int x=p.getBlockX()+(int)(Math.cos(angle)*r),z=p.getBlockZ()+(int)(Math.sin(angle)*r);
            var pos=cave?HorrorDirector.caveSpot(l,x,p.getBlockY(),z):SurvivalRules.surface(l,x,z);
            if(pos!=null&&HorrorDirector.spawn(l,p,kind,pos)!=null)return true;
        }
        return false;
    }
    static boolean matches(Mob mob,String action){
        String type=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        return switch(action){
            case "goat_fire","goat_ram","goat_trap"->type.equals("goat");
            case "hoof"->Set.of("horse","donkey","mule","camel").contains(type);
            case "bear"->type.equals("polar_bear");
            default->type.equals(action);
        };
    }
    static int animal(CommandSourceStack source,Mob mob,String action,ServerPlayer target){
        if(!ANIMALS.contains(action)||!matches(mob,action))return fail(source,"动物与技能不匹配；查看指令补全。");
        if(!(mob.level() instanceof ServerLevel l)||target.level()!=l||!LunarDread.player(target)
            ||!AnimalCombat.enabled(l)||!mob.hasLineOfSight(target)||mob.distanceToSqr(target)>16*16)
            return fail(source,"需要同世界、16 格内可见的生存玩家，且动物机制已启用、非恢复夜。");
        if(mob.isNoAi()||mob instanceof net.minecraft.world.entity.AgeableMob age&&age.isBaby()
            ||mob instanceof net.minecraft.world.entity.TamableAnimal pet&&pet.isTame()
            ||mob instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse&&horse.isTamed())
            return fail(source,"请选择有 AI 的成年野生动物。");
        AnimalCombat.retaliate(mob,target);mob.setTarget(target);
        mob.getPersistentData().putBoolean("lunar_dread.pursuing",true);
        if(action.equals("goat_trap"))AnimalCombat.goatTrap((Goat)mob,l);
        else if(action.equals("goat_ram")){
            mob.getPersistentData().putInt("lunar_dread.windup",20);mob.addTag("lunar_dread.preparing");
            l.broadcastEntityEvent(mob,(byte)58);
        }else AnimalCombat.begin(mob,action,36);
        source.sendSuccess(()->Component.literal("已安排 "+action+"；保留前摇、射程、遮挡与数量限制。"),false);return 1;
    }
    static int trigger(CommandSourceStack source,String id,List<ServerPlayer> targets){
        if(!EVENTS.contains(id))return fail(source,"未知事件；用 /lunardread event list 查看目录。");
        var l=source.getServer().overworld();
        if(!id.equals("player.cure")&&(LunarDread.score(l,"#day")==0||LunarDread.score(l,"#finished")==1))
            return fail(source,"世界进度未初始化或已经结束。");
        var eligible=targets.stream().filter(p->p.level()==l&&LunarDread.player(p)).toList();
        if(eligible.isEmpty())return fail(source,"没有主世界的生存玩家。");
        boolean blood=id.startsWith("moon.")&&!id.equals("moon.gravity")&&!id.equals("moon.contact")||id.equals("blood.hunt");
        boolean anomaly=id.startsWith("anomaly.");
        if((blood||anomaly||id.equals("moon.gravity")||id.equals("moon.contact"))&&!MoonCycle.night(l))
            return fail(source,"该事件需要夜晚；先执行 /time set night。");
        if(blood&&!LivingMoon.active(l))MoonCycle.force(l,MoonCycle.BLOOD);
        if(anomaly){
            int kind=List.of(MoonCycle.IDS).indexOf(id.substring(8));
            if(MoonCycle.kind(l)!=kind)MoonCycle.force(l,kind);
        }
        if(id.equals("moon.gravity")||id.equals("moon.contact")){
            if(MoonCycle.peaceful(l))MoonCycle.force(l,MoonCycle.GRAVITY);
        }
        int count=0;
        if(id.equals("moon.strike"))count=LivingMoon.trigger(l,eligible);
        else for(var p:eligible){
            boolean ok=switch(id){
                case "moon.gaze"->MoonDiscovery.attempt(p,true);
                case "moon.blackout"->MoonIncidents.blink(p,true);
                case "moon.gravity"->MoonGravity.begin(l,p,true,false);
                case "moon.contact"->MoonGravity.begin(l,p,true,true);
                case "blood.hunt"->BloodHuntDirector.order(p);
                case "air.raid"->AirRaid.spawn(l,p,true)>0;
                case "supply.guarded"->SupplyDrops.spawn(l,p.position(),0,false);
                case "supply.rich"->SupplyDrops.spawn(l,p.position(),1,false);
                case "supply.star"->SupplyDrops.spawn(l,p.position(),0,true);
                case "player.cure"->{MoonIncidents.cure(p);yield true;}
                default->id.startsWith("anomaly.")
                    ?MoonAnomalies.begin(l,p,List.of(MoonCycle.IDS).indexOf(id.substring(8)))
                    :id.startsWith("creature.")&&creature(p,CREATURES.indexOf(id.substring(9)));
            };
            if(ok)count++;
        }
        if(count==0)return fail(source,"未触发：检查数量上限、已加载空地、遮挡、配置、恢复夜或玩家正在经历其他事件。");
        final int done=count;source.sendSuccess(()->Component.literal(id+"：已接受 "+done+" 个目标/事件；阶段完成后才会产生攻击。"),false);return count;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event){
        var trigger=Commands.argument("id",StringArgumentType.word()).suggests((c,b)->{
            for(String id:EVENTS)if(id.startsWith(b.getRemaining()))b.suggest(id);return b.buildFuture();
        }).executes(c->trigger(c.getSource(),StringArgumentType.getString(c,"id"),List.of(c.getSource().getPlayerOrException())))
        .then(Commands.argument("players",EntityArgument.players()).executes(c->trigger(c.getSource(),
            StringArgumentType.getString(c,"id"),List.copyOf(EntityArgument.getPlayers(c,"players")))));
        event.getDispatcher().register(Commands.literal("lunardread")
            .then(Commands.literal("event").requires(s->s.hasPermission(2))
                .then(Commands.literal("list").executes(c->{c.getSource().sendSuccess(()->Component.literal(String.join(", ",EVENTS)),false);return EVENTS.size();}))
                .then(Commands.literal("trigger").then(trigger))
                .then(Commands.literal("cancel").executes(c->{var l=c.getSource().getServer().overworld();MoonIncidents.clearWorld(l);MoonAnomalies.clear(l);LivingMoon.cancel(l);return 1;})))
            .then(Commands.literal("animal").requires(s->s.hasPermission(2))
                .then(Commands.argument("animal",EntityArgument.entity())
                    .then(Commands.argument("action",StringArgumentType.word()).suggests((c,b)->{for(String id:ANIMALS)if(id.startsWith(b.getRemaining()))b.suggest(id);return b.buildFuture();})
                        .then(Commands.argument("player",EntityArgument.player()).executes(c->{
                            var actor=EntityArgument.getEntity(c,"animal");
                            return actor instanceof Mob mob?animal(c.getSource(),mob,StringArgumentType.getString(c,"action"),EntityArgument.getPlayer(c,"player")):fail(c.getSource(),"目标必须是动物。");
                        })))))
            .then(Commands.literal("moon").requires(s->s.hasPermission(2))
                .then(Commands.literal("list").executes(c->{c.getSource().sendSuccess(()->Component.literal(String.join(", ",MoonCycle.IDS)),false);return MoonCycle.IDS.length;}))
                .then(Commands.literal("form").then(Commands.argument("form",StringArgumentType.word()).suggests((c,b)->{b.suggest("normal");b.suggest("giant");return b.buildFuture();})
                    .executes(c->{var l=c.getSource().getServer().overworld();String form=StringArgumentType.getString(c,"form");
                        if(!Set.of("normal","giant").contains(form)||!LivingMoon.active(l))return fail(c.getSource(),"先切换到血月夜晚；形态只能是 normal 或 giant。");
                        var s=MoonCycle.state(l);s.form=form.equals("giant")?1:0;s.awakenedAt=l.getGameTime();s.forced=true;s.setDirty();MoonCycle.sync(l);return 1;
                    })))
                .then(Commands.literal("auto").executes(c->{var l=c.getSource().getServer().overworld();MoonIncidents.clearWorld(l);MoonAnomalies.clear(l);LivingMoon.cancel(l);var s=MoonCycle.state(l);
                    s.day=-1;s.forced=false;MoonCycle.score(l,"#blood_active",0);MoonCycle.score(l,"#is_blood",0);s.setDirty();MoonCycle.ensure(l);MoonCycle.sync(l);return 1;}))));
    }
}
