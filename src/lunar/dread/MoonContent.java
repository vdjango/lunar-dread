package lunar.dread;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MoonContent {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("lunar_dread");
    public static final String[] RELICS={"rift_shard","quiet_fibre","mirror_splinter","amber_tick","tide_salt","rust_coil","paper_seal","moon_sample"};
    private static final java.util.List<net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item>> RELIC_ITEMS=java.util.Arrays.stream(RELICS).map(id->ITEMS.registerSimpleItem(id,new net.minecraft.world.item.Item.Properties())).toList();
    public static net.minecraft.world.item.Item relic(int index){return RELIC_ITEMS.get(Math.clamp(index,0,7)).get();}
    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"lunar_dread");
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> HEART=sound("heart"),GRAB=sound("cloth_grab"),SCRAPE=sound("wall_scrape"),WHISPER=sound("whisper"),RIFT=sound("rift"),SILENCE=sound("silence"),TENSION=sound("tension");
    private static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> sound(String id){return SOUNDS.register(id,()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lunar_dread",id)));}
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,"lunar_dread");
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("lunar_dread");
    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"lunar_dread");
    private static final class LunarEffect extends MobEffect { LunarEffect(int color){super(MobEffectCategory.HARMFUL,color);} }
    public static final net.neoforged.neoforge.registries.DeferredHolder<MobEffect,MobEffect> BLACKOUT=EFFECTS.register("moon_blink",()->new LunarEffect(0x160812));
    public static final net.neoforged.neoforge.registries.DeferredHolder<MobEffect,MobEffect> WEIGHTLESS=EFFECTS.register("lunar_pull",()->new LunarEffect(0x735bad));
    public static final net.neoforged.neoforge.registries.DeferredBlock<Block> SHELL=BLOCKS.register("moon_shell",()->new Block(BlockBehaviour.Properties.of().strength(3,12).sound(SoundType.BONE_BLOCK).lightLevel(s->3)));
    public static final net.neoforged.neoforge.registries.DeferredBlock<Block> CORE=BLOCKS.register("moon_core",()->new Block(BlockBehaviour.Properties.of().strength(5,18).sound(SoundType.BONE_BLOCK).lightLevel(s->9)));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<MoonCanopy>> CANOPY=ENTITIES.register("moon_canopy",()->net.minecraft.world.entity.EntityType.Builder.<MoonCanopy>of(MoonCanopy::new,net.minecraft.world.entity.MobCategory.MISC).sized(3.2f,.8f).clientTrackingRange(64).updateInterval(1).build("lunar_dread:moon_canopy"));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<SupplyCrate>> CRATE=ENTITIES.register("supply_crate",()->net.minecraft.world.entity.EntityType.Builder.<SupplyCrate>of(SupplyCrate::new,net.minecraft.world.entity.MobCategory.MISC).sized(.9f,.9f).clientTrackingRange(128).updateInterval(1).build("lunar_dread:supply_crate"));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<HorrorMob>> SNATCHER=horror("skin_hauler",0,.9f,2.8f),DIGGER=horror("spine_digger",1,.8f,1.5f),BUILDER=horror("bent_beam",2,.9f,2.9f),WATCHER=horror("eave_watcher",3,.6f,2.9f),WING=horror("tether_wing",4,1.5f,1.6f);
    public static final String[] ACTORS={"rift_warden","stitched_beast","listener","hollow","reflection","late_shadow","tide_walker","chorus","magnet_cluster","porter","backstage_hand","paper_walker","star_jelly","suspended"};
    private static final java.util.List<net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<HorrorMob>>> LUNAR_TYPES=java.util.stream.IntStream.range(0,ACTORS.length).mapToObj(i->horror(ACTORS[i],i+5,i==1?1.2f:.8f,i==1?1.5f:2.3f)).toList();
    private static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<HorrorMob>> horror(String id,int kind,float w,float h){return ENTITIES.register(id,()->net.minecraft.world.entity.EntityType.Builder.<HorrorMob>of((t,l)->new HorrorMob(t,l,kind),net.minecraft.world.entity.MobCategory.MONSTER).sized(w,h).clientTrackingRange(96).updateInterval(1).build("lunar_dread:"+id));}
    public static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<HorrorMob>> horror(int kind){if(kind>=5)return LUNAR_TYPES.get(Math.clamp(kind-5,0,ACTORS.length-1));return switch(kind){case 1->DIGGER;case 2->BUILDER;case 3->WATCHER;case 4->WING;default->SNATCHER;};}
    public static void register(net.neoforged.bus.api.IEventBus bus){ITEMS.register(bus);SOUNDS.register(bus);EFFECTS.register(bus);BLOCKS.register(bus);ENTITIES.register(bus);bus.addListener(MoonContent::attributes);}
    private static void attributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event){for(int i=0;i<5+ACTORS.length;i++)event.put(horror(i).get(),HorrorMob.attributes().build());}
}
