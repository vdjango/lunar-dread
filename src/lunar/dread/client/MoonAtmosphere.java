package lunar.dread.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import lunar.dread.MoonCycle;
import lunar.dread.LunarDreadConfig;
import lunar.dread.MoonFogProfile;
import lunar.dread.MoonUpdates;

/** Local visual state never changes controls, system brightness or the chosen server event. */
@EventBusSubscriber(modid="lunar_dread",value=Dist.CLIENT)
public final class MoonAtmosphere {
    private static MoonUpdates.World world;
    private static MoonUpdates.Personal personal;
    private static ClientLevel worldLevel,personalLevel;
    private static long worldAt,personalAt;
    private static boolean menu;
    private static float roll,pitch,oldRoll,oldPitch;
    private static float fogDepth,fogTarget;
    private static long navUntil;
    private static boolean navAlways;
    private static int navHash;
    public static void navigate(int mode){var mc=Minecraft.getInstance();if(mc.level==null)return;navUntil=mc.level.getGameTime()+100;if(mode==1)navAlways=!navAlways;}
    private static final java.util.Set<Integer> rotated=new HashSet<>();
    public static void receive(MoonUpdates.World v){var mc=Minecraft.getInstance();world=v;worldLevel=mc.level;worldAt=mc.level==null?0:mc.level.getGameTime();}
    public static void receivePersonal(MoonUpdates.Personal v){var mc=Minecraft.getInstance();personal=v;personalLevel=mc.level;personalAt=mc.level==null?0:mc.level.getGameTime();LunarAudio.receive(v.threat());}
    public static boolean current(){var l=Minecraft.getInstance().level;return world!=null&&l!=null&&l==worldLevel&&l.dimension()==Level.OVERWORLD&&l.getGameTime()-worldAt<=40;}
    public static int kind(){var l=Minecraft.getInstance().level;return current()&&Math.floorMod(l.getDayTime(),24000)>=13000?world.kind():MoonCycle.NORMAL;}
    public static boolean special(){int k=kind();return k==MoonCycle.BLOOD||k>=7;}
    public static double clock(float partial){var l=Minecraft.getInstance().level;return current()?world.tick()+l.getGameTime()-worldAt+partial:0;}
    private static boolean personal(){var l=Minecraft.getInstance().level;return personal!=null&&l!=null&&l==personalLevel&&l.dimension()==Level.OVERWORLD&&l.getGameTime()-personalAt<=20;}
    public static boolean giant(){return kind()==MoonCycle.BLOOD&&world.form()==1;}
    public static boolean nearShell(net.minecraft.world.entity.Entity entity){return entity!=null&&current()&&world.shell()!=null&&entity.position().distanceToSqr(Vec3.atCenterOf(world.shell()))<52*52;}
    public static float growth(float partial){return giant()?(float)Mth.clamp((clock(partial)-world.awakenedAt())/60,0,1):0;}
    public static float bloodSize(float partial){
        var mc=Minecraft.getInstance();double fov=mc.options.fov().get();double aspect=mc.getWindow().getWidth()/(double)Math.max(1,mc.getWindow().getHeight());
        double target=240*Math.tan(Math.toRadians(fov*.5))/Math.tan(Math.toRadians(35))*Math.sqrt(aspect/(16.0/9));
        double g=growth(partial);g=g*g*(3-2*g);double size=20+(Math.clamp(target,100,440)-20)*g;
        double p=(clock(partial)%56)/56;
        double beat=Math.exp(-Math.pow((p-.16)/.055,2))+.55*Math.exp(-Math.pow((p-.34)/.065,2));
        return(float)(size*(1+(giant()?.04:.018)*beat)*(MoonClient.phase()>0?1.025:1));
    }
    public static Vector3f tint(float partial){
        float b=(float)(.5+.5*Math.sin(clock(partial)*Math.PI/70));
        return switch(kind()){
            case MoonCycle.NORMAL->new Vector3f(.42f,.48f,.58f);
            case MoonCycle.HARVEST->new Vector3f(.78f,.57f,.27f);
            case MoonCycle.BLUE->new Vector3f(.28f,.48f,.82f);
            case MoonCycle.PURPLE->new Vector3f(.35f+b*.2f,.015f,.6f+b*.25f);
            case MoonCycle.PALE->new Vector3f(.78f,.82f,.69f);
            case MoonCycle.MIRROR->new Vector3f(.12f,.18f,.24f);
            case MoonCycle.GRAVITY->new Vector3f(.3f,.2f,.72f);
            case MoonCycle.AMBER->new Vector3f(.95f,.48f,.04f);
            case MoonCycle.TIDE->new Vector3f(.05f,.5f,.57f);
            case MoonCycle.RUST->new Vector3f(.65f,.2f,.035f);
            case MoonCycle.PAPER->new Vector3f(.66f,.36f,.5f);
            case MoonCycle.ASH->new Vector3f(.82f,.27f,.09f);
            case MoonCycle.WIND->new Vector3f(.5f,.88f,.85f);
            case MoonCycle.GREEN->new Vector3f(.24f,.75f,.35f);
            case MoonCycle.STAR->new Vector3f(.55f,.73f,1);
            default->new Vector3f(.34f+b*.52f,.008f+b*.025f,.025f+b*.065f);
        };
    }
    public static float blackout(float partial){
        if(!personal()||personal.blackAge()<0||Minecraft.getInstance().screen!=null)return 0;
        float age=personal.blackAge()+(Minecraft.getInstance().level.getGameTime()-personalAt)+partial;
        if(age<40)return Mth.clamp(age/40,0,1)*.22f;
        if(age<45)return(age-40)/5;
        if(age<45+personal.blackLength())return 1;
        return Mth.clamp(1-(age-45-personal.blackLength())/8,0,1);
    }
    public static Vec3 force(LivingEntity e){var mc=Minecraft.getInstance();return e==mc.player&&personal()&&personal.gravityAge()>=0?personal.force():Vec3.ZERO;}
    public static float tumbleAge(LivingEntity entity,float partial){
        if(!current())return -1;
        for(var t:world.tumbling())if(t.entity()==entity.getId())return(float)(clock(partial)-t.start());
        return -1;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(mc.level.getGameTime()%10==0)fogTarget=lunar.dread.BloodFogProfile.depth(mc.level,mc.player.blockPosition());
        fogDepth=Mth.lerp(.025f,fogDepth,fogTarget);
        if(current()){
            int hash=world.beacons().hashCode();if(hash!=navHash){navHash=hash;navUntil=mc.level.getGameTime()+100;}
        }
        boolean open=mc.screen!=null;if(open!=menu){menu=open;PacketDistributor.sendToServer(new MoonUpdates.Menu(open));}
        oldRoll=roll;oldPitch=pitch;
        float age=personal()&&personal.gravityAge()>=0?personal.gravityAge()+(mc.level.getGameTime()-personalAt):-1;
        float targetRoll=age<0?roll+Mth.wrapDegrees(-roll):age*.82f;
        float targetPitch=age<0?0:22*(float)Math.sin(age*.018);
        if(mc.player.getVehicle() instanceof lunar.dread.HorrorMob carrier){targetRoll=68+8*(float)Math.sin(carrier.tickCount*.6);targetPitch=8*(float)Math.sin(carrier.tickCount*.6);}
        roll=Mth.lerp(.14f,roll,targetRoll);pitch=Mth.lerp(.14f,pitch,targetPitch);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        float p=(float)e.getPartialTick();e.setRoll(e.getRoll()+Mth.lerp(p,oldRoll,roll));e.setPitch(e.getPitch()+Mth.lerp(p,oldPitch,pitch));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void pose(RenderLivingEvent.Pre<?,?> e){
        float age=tumbleAge(e.getEntity(),e.getPartialTick());boolean carried=e.getEntity().getVehicle() instanceof lunar.dread.HorrorMob;if(age<0&&!carried)return;
        var pose=e.getPoseStack();pose.pushPose();float center=e.getEntity().getBbHeight()*.5f;
        pose.translate(0,center,0);pose.mulPose(Axis.ZP.rotationDegrees(carried?75:age*.82f));pose.mulPose(Axis.XP.rotationDegrees(carried?15:70+20*(float)Math.sin(age*.018)));pose.translate(0,-center,0);
        rotated.add(e.getEntity().getId());
    }
    @SubscribeEvent public static void poseEnd(RenderLivingEvent.Post<?,?> e){if(rotated.remove(e.getEntity().getId()))e.getPoseStack().popPose();}
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        var mc=Minecraft.getInstance();if(mc.level==null||!LunarDreadConfig.MOON_FOG_ENABLED.get()||mc.gameRenderer.getMainCamera().getFluidInCamera()!=FogType.NONE)return;
        float strength=MoonFogProfile.intensity(mc.level.getDayTime());if(strength<=0)return;
        int phase=kind();var c=tint((float)e.getPartialTick());
        float weight=MoonFogProfile.colorWeight(phase,strength);
        float red=phase==MoonCycle.BLOOD?.22f:c.x*.48f;
        float green=phase==MoonCycle.BLOOD?.008f:c.y*.48f;
        float blue=phase==MoonCycle.BLOOD?.022f:c.z*.48f;
        e.setRed(Mth.lerp(weight,e.getRed(),red));
        e.setGreen(Mth.lerp(weight,e.getGreen(),green));
        e.setBlue(Mth.lerp(weight,e.getBlue(),blue));
    }
    @SubscribeEvent public static void fogDistance(ViewportEvent.RenderFog e){
        var mc=Minecraft.getInstance();if(mc.level==null||!LunarDreadConfig.MOON_FOG_ENABLED.get()||e.getType()!=FogType.NONE||e.getMode()!=net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN)return;
        float strength=MoonFogProfile.intensity(mc.level.getDayTime());if(strength<=0)return;
        var profile=MoonFogProfile.range(kind(),fogDepth,strength,e.getNearPlaneDistance(),e.getFarPlaneDistance(),
            LunarDreadConfig.BLOOD_FOG_PEAK_DISTANCE.get().floatValue(),LunarDreadConfig.MOON_FOG_DISTANCE_SCALE.get().floatValue());
        e.setNearPlaneDistance(profile.near());e.setFarPlaneDistance(profile.far());e.setCanceled(true);
    }
    private static MoonUpdates.Beacon nearest(){
        var mc=Minecraft.getInstance();if(!current()||mc.player==null)return null;MoonUpdates.Beacon nearest=null;double best=Double.MAX_VALUE;
        for(var b:world.beacons()){double distance=mc.player.position().distanceToSqr(Vec3.atCenterOf(b.pos()));if(distance<best){best=distance;nearest=b;}}return nearest;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hud(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null)return;var gui=e.getGuiGraphics();int w=gui.guiWidth(),h=gui.guiHeight();
        var b=nearest();if(b!=null&&!mc.options.hideGui&&mc.screen==null&&(navAlways||mc.level.getGameTime()<navUntil||mc.player.position().distanceToSqr(Vec3.atCenterOf(b.pos()))<256)){
            var to=Vec3.atCenterOf(b.pos()).subtract(mc.player.position());int distance=(int)Math.ceil(Math.sqrt(to.x*to.x+to.z*to.z)),height=b.pos().getY()-mc.player.getBlockY();
            float yaw=(float)Math.toDegrees(Math.atan2(-to.x,to.z)),turn=Mth.wrapDegrees(yaw-mc.player.getYRot());
            String direction=Math.abs(turn)<18?"↑ 正前方":turn>0?"→ 向右转":"← 向左转";
            if(Math.abs(turn)>145)direction="↓ 在身后";
            String label=(b.status()==3?"货箱正在降落 · 留意落点":b.status()==0?"空投 · 先清除守卫":b.status()==2?"星砂 · 可领取":"空投 · 箱子已解锁")+"   "+direction+"   "+distance+"m";
            int color=b.status()==0?0xffffc265:b.status()==2?0xffa0d8ff:0xff8be6ae;
            // Jade occupies the top centre; place guidance below its target tooltip.
            gui.fill(w/2-155,40,w/2+155,67,0x99070a0b);gui.drawCenteredString(mc.font,label,w/2,43,color);
            gui.drawCenteredString(mc.font,"X "+b.pos().getX()+"  Y "+b.pos().getY()+"  Z "+b.pos().getZ()+"  高差 "+(height>0?"+":"")+height+" · 隔山请绕行，沿光柱接近",w/2,55,0xffc1c7ce);
        }
        float black=blackout(mc.getTimer().getGameTimeDeltaPartialTick(false));
        if(black>0){
            // HUD elements have their own positive Z layers. A Z=0 rectangle
            // darkens the world but leaves chat, crosshair and the minimap visible.
            gui.flush();gui.pose().pushPose();gui.pose().translate(0,0,10000);
            try{
                if(personal()&&personal.blackAge()<40){int a=(int)(black*255);gui.fillGradient(0,0,w,h/3,a<<24,0);gui.fillGradient(0,h*2/3,w,h,0,a<<24);}
                else gui.fill(0,0,w,h,((int)(Math.min(1,black)*255)<<24));
                gui.flush();
            }finally{gui.pose().popPose();}
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent e){
        if(!current()||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();var camera=e.getCamera().getPosition();var matrix=e.getPoseStack().last().pose();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try{
            var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);boolean drawn=false;
            for(var drop:world.beacons()){
                var pos=Vec3.atCenterOf(drop.pos()).subtract(camera);if(pos.lengthSqr()>384*384)continue;
                var c=drop.status()==0?new Vector3f(1,.62f,.17f):drop.status()==2?new Vector3f(.4f,.75f,1):new Vector3f(.25f,1,.5f);
                MoonClient.ray(b,matrix,pos.add(0,1,0),pos.add(0,48,0),.12,c,.65f);MoonClient.ring(b,matrix,pos.add(0,.12,0),1.1,c,.7f);drawn=true;
            }
            for(var wing:world.wings()){
                var mob=mc.level.getEntity(wing.entity());if(mob==null)continue;
                var pos=mob.getPosition(e.getPartialTick().getGameTimeDeltaPartialTick(false)).subtract(camera).add(0,mob.getBbHeight()+.5,0);
                var c=wing.style()==1?new Vector3f(.86f,.78f,.7f):wing.style()==2?new Vector3f(.24f,.08f,.09f):new Vector3f(.4f,.38f,.27f);
                double beat=Math.sin((clock(0)+mob.getId())*.22)*.12;
                // Cloth, wool lobes, ribbed goat membranes and feathered chicken wings.
                if(wing.style()==2||wing.style()==3){
                    for(int side:new int[]{-1,1})for(int i=0;i<5;i++){
                        double z=(i-2)*.42;Vec3 root=pos.add(side*.18,.45,0);
                        Vec3 tip=pos.add(side*(1.5-Math.abs(i-2)*.13),.25+beat,z);
                        Vec3 next=pos.add(side*(1.4-Math.abs(i-1)*.12),.22+beat,z+.35);
                        MoonClient.quad(b,matrix,root,tip,next,root,c,.96f);
                        MoonClient.ray(b,matrix,root,tip,wing.style()==2?.035:.055,new Vector3f(c).mul(1.3f),1);
                    }
                }else for(int i=0;i<8;i++){
                    double a=i*Math.PI/4,d=(i+1)*Math.PI/4;
                    Vec3 left=pos.add(Math.cos(a)*1.6,0,Math.sin(a)*1.6),right=pos.add(Math.cos(d)*1.6,0,Math.sin(d)*1.6),top=pos.add(0,.75,0);
                    if(wing.style()==1){
                        Vec3 puff=pos.add(Math.cos((a+d)/2)*.9,.65+beat,Math.sin((a+d)/2)*.9);
                        MoonClient.quad(b,matrix,left,right,puff,puff,c,.97f);
                        MoonClient.quad(b,matrix,left,puff,top,top,new Vector3f(c).mul(.85f),.97f);
                    }else MoonClient.quad(b,matrix,left,right,top,top,c,.95f);
                    MoonClient.ray(b,matrix,left,pos.add(0,-.7,0),.016,new Vector3f(.75f,.72f,.62f),.9f);
                }drawn=true;
            }
            if(special()){
                var c=tint(0);float g=growth(0);var layer=new Vector3f(c.x*.13f,c.y*.09f,c.z*.12f);
                for(int i=0;i<8;i++){
                    double angle=clock(0)*.0006+i*Math.PI/4;Vec3 center=new Vec3(Math.cos(angle)*58,70+Math.sin(angle*2)*9,Math.sin(angle)*58);
                    MoonClient.quad(b,matrix,center.add(-42,0,-24),center.add(42,0,-24),center.add(42,0,24),center.add(-42,0,24),layer,.14f+.17f*g);drawn=true;
                }
            }
            if(drawn)BufferUploader.drawWithShader(b.buildOrThrow());else b.build();
        }finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){world=null;personal=null;worldLevel=null;personalLevel=null;roll=pitch=oldRoll=oldPitch=fogDepth=fogTarget=0;menu=false;rotated.clear();navUntil=0;navHash=0;navAlways=false;}
}
