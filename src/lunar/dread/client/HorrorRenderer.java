package lunar.dread.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import lunar.dread.HorrorMob;

/** Original articulated meshes: long forearms, folded spine, timber limbs, needle neck and split sails. */
@EventBusSubscriber(modid="lunar_dread",value=Dist.CLIENT)
public final class HorrorRenderer {
    private static final java.util.Map<java.util.UUID,net.minecraft.client.player.RemotePlayer> mirrors=new java.util.HashMap<>();
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){mirrors.clear();}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;var cam=e.getCamera().getPosition();float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        if(mc.level.getGameTime()%100==0)mirrors.keySet().removeIf(id->java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).noneMatch(v->v.getUUID().equals(id)));
        RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.disableCull();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof HorrorMob m&&m.kind()==9&&m.mirror()!=null){
            var source=mc.level.getPlayerByUUID(m.mirror());if(source==null)continue;
            var clone=mirrors.computeIfAbsent(m.getUUID(),id->new net.minecraft.client.player.RemotePlayer(mc.level,source.getGameProfile()));
            if(clone.level()!=mc.level){mirrors.remove(m.getUUID());continue;}
            clone.tickCount=m.tickCount;clone.yBodyRot=m.yBodyRot;clone.yBodyRotO=m.yBodyRotO;clone.yHeadRot=m.yHeadRot;clone.yHeadRotO=m.yHeadRotO;
            clone.setXRot(m.getXRot());clone.setYRot(m.getYRot());clone.setPos(m.position());
            for(var slot:net.minecraft.world.entity.EquipmentSlot.values())clone.setItemSlot(slot,source.getItemBySlot(slot).copy());
            var pose=e.getPoseStack();pose.pushPose();var pos=m.getPosition(partial).subtract(cam);pose.translate(pos.x,pos.y,pos.z);
            var renderer=mc.getEntityRenderDispatcher().getRenderer(clone);renderer.render(clone,m.getYRot(),partial,pose,mc.renderBuffers().bufferSource(),15728880);pose.popPose();
        }
        var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);boolean any=false;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof lunar.dread.SupplyCrate crate){
            var pose=e.getPoseStack();pose.pushPose();var pos=crate.getPosition(partial).subtract(cam);pose.translate(pos.x,pos.y,pos.z);
            box(b,pose,0,.45,0,.9,.9,.9,new Vector3f(.4f,.29f,.15f));box(b,pose,0,.5,-.46,.15,.18,.03,new Vector3f(.6f,.57f,.48f));
            for(int i=0;i<8;i++){double a=i*Math.PI/4,d=(i+1)*Math.PI/4;Vec3 left=new Vec3(Math.cos(a)*2,2.7,Math.sin(a)*2),right=new Vec3(Math.cos(d)*2,2.7,Math.sin(d)*2),top=new Vec3(0,3.5,0);
                MoonClient.quad(b,pose.last().pose(),left,right,top,top,new Vector3f(.67f,.6f,.42f),1);MoonClient.ray(b,pose.last().pose(),left,new Vec3(0,.8,0),.015,new Vector3f(.75f,.7f,.55f),1);}
            pose.popPose();any=true;
        }
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof HorrorMob m&&m.distanceToSqr(cam)<128*128){
            var pose=e.getPoseStack();pose.pushPose();var pos=m.getPosition(partial).subtract(cam);pose.translate(pos.x,pos.y,pos.z);pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,m.yBodyRotO,m.yBodyRot)));
            float time=m.tickCount+partial,step=(float)Math.sin(time*.6)*Math.min(1,(float)m.getDeltaMovement().horizontalDistance()*5),reach=m.phase()==1?1:m.phase()==2?.7f:0;
            Vector3f skin=switch(m.kind()){case 1->new Vector3f(.23f,.25f,.21f);case 2->new Vector3f(.25f,.18f,.13f);case 3->new Vector3f(.36f,.33f,.3f);case 4->new Vector3f(.18f,.13f,.23f);default->new Vector3f(.37f,.25f,.23f);};
            if(m.kind()>=5){
                lunar(b,pose,m.kind(),time,step);
            }else if(m.kind()==1){
                box(b,pose,0,.8,0,.76,.5,1.1,skin);box(b,pose,0,.7,-.66,.6,.5,.4,skin);
                for(int i=0;i<5;i++)box(b,pose,0,1.13+i*.035,.4-i*.23,.1,.38+i*.045,.13,new Vector3f(.62f,.59f,.43f));
                for(int s:new int[]{-1,1}){limb(b,pose,s*.4,.8,-.25,65+s*step*35,.14,.85,.16,skin);limb(b,pose,s*.27,.6,.4,-40-s*step*40,.17,.65,.18,skin);}
                eyes(b,pose,0,.82,-.88,.18);
            }else if(m.kind()==4){
                box(b,pose,0,.9,0,.4,1.0,.4,skin);box(b,pose,0,1.55,-.1,.35,.35,.4,skin);
                for(int s:new int[]{-1,1}){
                    pose.pushPose();pose.translate(s*.2,1.35,0);pose.mulPose(Axis.ZP.rotationDegrees(s*(18+15*(float)Math.sin(time*.18))));
                    box(b,pose,s*.75,0,0,1.5,.12,.65,skin);box(b,pose,s*.9,-.22,.12,1.3,.33,.04,new Vector3f(.29f,.2f,.32f));pose.popPose();
                    limb(b,pose,s*.15,.8,0,s*step*35,.12,.8,.13,skin);
                }eyes(b,pose,0,1.6,-.32,.1);
            }else{
                boolean beam=m.kind()==2,watch=m.kind()==3;double torso=watch?.24:beam?.34:.66;
                box(b,pose,0,1.72,0,torso,1.0,.35,skin);
                box(b,pose,0,watch?2.55:2.38,-.12,watch?.23:.38,watch?.65:.45,.34,skin);
                for(int s:new int[]{-1,1}){
                    limb(b,pose,s*(watch?.15:beam?.22:.38),2.1,0,-reach*85+s*step*18,.12,beam?1.7:1.4,.14,skin);
                    limb(b,pose,s*.2,1.25,0,s*step*35,.14,1.25,.16,skin);
                    if(beam)box(b,pose,s*.35,1.8,.15,.13,.9,.65,new Vector3f(.15f,.11f,.08f));
                }
                if(watch)box(b,pose,0,2.85,-.15,.6,.08,.12,new Vector3f(.1f,.08f,.07f));
                eyes(b,pose,0,watch?2.65:2.4,-.31,watch?.06:.11);
            }
            pose.popPose();any=true;
            if(m.ropeTarget()>=0){var target=mc.level.getEntity(m.ropeTarget());if(target!=null)MoonClient.ray(b,e.getPoseStack().last().pose(),m.getEyePosition().subtract(cam),target.getEyePosition().subtract(cam),.025,new Vector3f(.66f,.44f,.72f),1);}
        }
        if(any)BufferUploader.drawWithShader(b.buildOrThrow());else b.build();RenderSystem.enableCull();
    }
    private static void lunar(BufferBuilder b,PoseStack p,int kind,float time,float step){
        if(kind==9)return;
        Vector3f c=switch(kind){case 5,6->new Vector3f(.3f,.11f,.38f);case 7,8->new Vector3f(.55f,.57f,.46f);case 10->new Vector3f(.58f,.35f,.09f);case 11,12->new Vector3f(.09f,.34f,.34f);case 13,14->new Vector3f(.46f,.25f,.13f);case 15,16->new Vector3f(.61f,.5f,.56f);default->new Vector3f(.32f,.2f,.5f);};
        if(kind==5||kind==13){
            p.pushPose();p.translate(0,1.2+Math.sin(time*.04)*.1,0);p.mulPose(Axis.YP.rotationDegrees(time*(kind==5?1:-.7f)));
            box(b,p,0,0,0,.45,.65,.45,c);for(int i=0;i<4;i++){p.mulPose(Axis.YP.rotationDegrees(90));box(b,p,.65,0,0,.18,1.3,.22,c);box(b,p,.45,.55,0,.8,.13,.2,c);}p.popPose();
        }else if(kind==6){
            box(b,p,0,.9,0,1,.7,1.3,c);for(int s:new int[]{-1,1})for(int j:new int[]{-1,1}){limb(b,p,s*.45,.8,j*.5,s*j*step*35,.16,.8,.16,c);box(b,p,s*.27,1,-.8,.4,.4,.6,c);eyes(b,p,s*.27,1.1,-1.11,.1);}
        }else if(kind==7){
            box(b,p,0,1.3,0,.25,1.9,.22,c);box(b,p,0,2.18,0,.75,.5,.12,c);for(int s:new int[]{-1,1}){box(b,p,s*.55,2.18,0,.35,.65,.15,c);limb(b,p,s*.15,1.1,0,s*step*25,.1,1.1,.12,c);}
        }else if(kind==8||kind==18){
            for(int s:new int[]{-1,1}){limb(b,p,s*.24,1.3,0,s*step*25,.12,1.3,.13,c);box(b,p,s*.23,1.7,0,.12,.85,.12,c);}box(b,p,0,2.2,0,.55,.18,.35,c);
            for(int i=0;i<4;i++)box(b,p,0,1.3+i*.2,0,.55,.06,.24,c);
        }else if(kind==10){
            box(b,p,0,1.4,0,.5,1.1,.3,c);for(int s:new int[]{-1,1}){limb(b,p,s*.17,1,0,s*step*30,.14,1,.16,c);limb(b,p,s*.35,1.8,0,-45+step*15,.08,1.4,.08,c);}p.pushPose();p.translate(0,2.15,0);p.mulPose(Axis.YP.rotationDegrees(time*2));box(b,p,0,0,0,.7,.12,.4,c);p.popPose();
        }else if(kind==11||kind==12){
            box(b,p,0,1.1,0,kind==12?.9:.45,1.4,.35,c);for(int i=0;i<(kind==12?5:3);i++)limb(b,p,(i-(kind==12?2:1))*.18,.5,0,step*15,.08,.5,.08,c);
            for(int i=0;i<(kind==12?3:1);i++){box(b,p,(i-(kind==12?1:0))*.3,1.9,0,.24,.4,.25,c);box(b,p,(i-(kind==12?1:0))*.3,1.88,-.15,.12,.18,.03,new Vector3f(.01f,.03f,.035f));}
        }else if(kind==14){
            box(b,p,0,1.3,.2,.7,1,.8,c);for(int s:new int[]{-1,1}){limb(b,p,s*.22,.9,0,s*step*35,.18,.9,.18,c);limb(b,p,s*.5,1.7,0,-90,.15,.8,.15,c);}box(b,p,0,1.8,-.35,.4,.4,.3,c);
        }else if(kind==15){
            box(b,p,0,1.2,0,.15,1.7,.12,c);for(int s:new int[]{-1,1}){limb(b,p,s*.2,1.8,0,-30+step*20,.12,1.6,.1,c);for(int j=0;j<4;j++)box(b,p,s*.27+j*.035,.25,-.45,.03,.3,.03,c);}box(b,p,0,2.15,0,.48,.25,.08,c);
        }else if(kind==16){
            for(int j=0;j<5;j++){p.pushPose();p.translate(0,.3+j*.37,0);p.mulPose(Axis.YP.rotationDegrees(j%2==0?30:-30));box(b,p,0,0,0,.8-j*.07,.3,.08,c);p.popPose();}
        }else{
            box(b,p,0,1.8,0,1.1,.35,1.1,c);for(int i=0;i<6;i++)limb(b,p,Math.cos(i)*.4,1.7,Math.sin(i)*.4,15*(float)Math.sin(time*.08+i),.055,1.4,.055,c);
        }
    }
    private static void eyes(BufferBuilder b,PoseStack p,double x,double y,double z,double gap){for(int s:new int[]{-1,1})box(b,p,x+s*gap,y,z,.055,.035,.025,new Vector3f(.85f,.67f,.48f));}
    private static void limb(BufferBuilder b,PoseStack p,double x,double y,double z,float angle,double w,double h,double d,Vector3f c){p.pushPose();p.translate(x,y,z);p.mulPose(Axis.XP.rotationDegrees(angle));box(b,p,0,-h*.5,0,w,h,d,c);p.popPose();}
    private static void box(BufferBuilder b,PoseStack p,double x,double y,double z,double w,double h,double d,Vector3f c){
        double a=x-w/2,f=x+w/2,g=y-h/2,k=y+h/2,l=z-d/2,n=z+d/2;var m=p.last().pose();
        Vec3 A=new Vec3(a,g,l),B=new Vec3(f,g,l),C=new Vec3(f,k,l),D=new Vec3(a,k,l),E=new Vec3(a,g,n),F=new Vec3(f,g,n),G=new Vec3(f,k,n),H=new Vec3(a,k,n);
        MoonClient.quad(b,m,A,B,C,D,c,1);MoonClient.quad(b,m,F,E,H,G,new Vector3f(c).mul(.66f),1);
        MoonClient.quad(b,m,E,A,D,H,new Vector3f(c).mul(.75f),1);MoonClient.quad(b,m,B,F,G,C,new Vector3f(c).mul(.85f),1);
        MoonClient.quad(b,m,D,C,G,H,new Vector3f(c).mul(1.12f),1);MoonClient.quad(b,m,E,F,B,A,new Vector3f(c).mul(.5f),1);
    }
}
