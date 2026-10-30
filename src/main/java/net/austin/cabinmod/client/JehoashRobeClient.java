package net.austin.cabinmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.client.PlayerRobeLayer;
import net.austin.cabinmod.entity.client.physics.ClothSheet;
import net.austin.cabinmod.entity.client.physics.PlayerRobePose;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Client-only attachment, lifecycle and view-model hooks. Equipment uses vanilla networking. */
@EventBusSubscriber(modid=CabinMod.MOD_ID,value=Dist.CLIENT)
public final class JehoashRobeClient {
    private record Basis(AbstractClientPlayer player, Matrix4f pose, Vec3 origin) {}
    private static final ArrayDeque<Basis> BASES=new ArrayDeque<>();
    private static final List<PlayerRobeLayer> LAYERS=new ArrayList<>();
    private static final ViewSleeves[] HANDS=new ViewSleeves[2];

    private JehoashRobeClient() {}

    public static boolean isWearing(AbstractClientPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.JEHOASH_ROBE.get());
    }

    @EventBusSubscriber(modid=CabinMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
            LAYERS.clear();
            BASES.clear();
            for(var skin:event.getSkins()) {
                PlayerRenderer renderer=event.getSkin(skin);
                if(renderer==null) continue;
                boolean slim=skin==PlayerSkin.Model.SLIM;
                var layer=new PlayerRobeLayer(renderer,slim);
                renderer.addLayer(layer);
                LAYERS.add(layer);
                HANDS[slim?1:0]=new ViewSleeves(new PlayerModel<>(event.getEntityModels().bakeLayer(
                        slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim),slim);
            }
        }
    }

    @SubscribeEvent public static void before(RenderPlayerEvent.Pre event) {
        if(!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        var origin=player.getPosition(event.getPartialTick()).add(event.getRenderer().getRenderOffset(player,event.getPartialTick()));
        BASES.push(new Basis(player,new Matrix4f(event.getPoseStack().last().pose()),origin));
    }

    @SubscribeEvent public static void after(RenderPlayerEvent.Post event) {
        if(!BASES.isEmpty() && BASES.peek().player==event.getEntity()) BASES.pop();
    }

    @SubscribeEvent public static void frameStart(RenderFrameEvent.Pre event) { BASES.clear(); }

    public static ClothSheet.PlayerFrame frame(AbstractClientPlayer player, PoseStack modelPose) {
        if(BASES.isEmpty() || BASES.peek().player!=player) return null;
        Basis basis=BASES.peek();
        Matrix4f pixelsToEntity=new Matrix4f(basis.pose).invert().mul(modelPose.last().pose()).scale(1f/16);
        return new ClothSheet.PlayerFrame(basis.origin,pixelsToEntity);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.level==null) {
            for(var layer:LAYERS) layer.clear();
            for(var hands:HANDS) if(hands!=null) hands.clear();
            BASES.clear();
            return;
        }
        for(var player:mc.level.players()) if(!isWearing(player)) {
            for(var layer:LAYERS) layer.forget(player);
            for(var hands:HANDS) if(hands!=null) hands.forget(player);
        }
    }

    @SubscribeEvent public static void arm(RenderArmEvent event) {
        var player=event.getPlayer();
        if(!isWearing(player) || player.isInvisible() || player.isSpectator()) return;
        var sleeves=HANDS[player.getSkin().model()==PlayerSkin.Model.SLIM?1:0];
        if(sleeves!=null) sleeves.render(event);
        // Do not cancel: vanilla still renders its arm, skin sleeve and held item.
    }

    private static final class ViewSleeves {
        final PlayerModel<AbstractClientPlayer> model;
        final ClothSheet right,left;
        ViewSleeves(PlayerModel<AbstractClientPlayer> model, boolean slim) {
            this.model=model;
            right=ClothSheet.playerSleeve(slim,true);
            left=ClothSheet.playerSleeve(slim,false);
        }

        void render(RenderArmEvent event) {
            var player=event.getPlayer();
            var main=armPose(player,InteractionHand.MAIN_HAND);
            var off=main.isTwoHanded() ? (player.getOffhandItem().isEmpty()?HumanoidModel.ArmPose.EMPTY:HumanoidModel.ArmPose.ITEM)
                    :armPose(player,InteractionHand.OFF_HAND);
            model.rightArmPose=player.getMainArm()==HumanoidArm.RIGHT?main:off;
            model.leftArmPose=player.getMainArm()==HumanoidArm.RIGHT?off:main;
            // Match PlayerRenderer.renderHand's neutral setup on a separate model,
            // preserving the renderer's world pose and other mods' arm rendering.
            model.attackTime=0;model.crouching=false;model.swimAmount=0;model.riding=false;model.young=false;
            model.setupAnim(player,0,0,0,0,0);
            model.rightArm.xRot=model.leftArm.xRot=0;
            var mc=Minecraft.getInstance();
            var camera=mc.gameRenderer.getMainCamera();
            var frame=new ClothSheet.PlayerFrame(camera.getPosition(),new Matrix4f().rotation(camera.rotation())
                    .mul(event.getPoseStack().last().pose()).scale(1f/16));
            var pose=PlayerRobePose.of(model,frame,false);
            // The view model has no rendered torso or legs to collide against.
            boolean isRight=event.getArm()==HumanoidArm.RIGHT;
            int armIndex=isRight?2:3;
            pose=new ClothSheet.PlayerPose(pose.upper(),List.of(pose.boxes().get(armIndex)),frame,false,pose.legR(),pose.legL());
            var cloth=isRight?right:left;
            float age=player.tickCount+mc.getTimer().getGameTimeDeltaPartialTick(false);
            cloth.updatePlayer(player,age,pose,true);
            cloth.render(player,event.getPoseStack(),event.getMultiBufferSource(),PlayerRobeLayer.TEXTURE,
                    event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }

        void clear() { right.clear();left.clear(); }
        void forget(AbstractClientPlayer player) { right.forget(player);left.forget(player); }
    }

    private static HumanoidModel.ArmPose armPose(AbstractClientPlayer player, InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if(player.getUsedItemHand()==hand && player.getUseItemRemainingTicks()>0) {
            UseAnim animation=stack.getUseAnimation();
            return switch(animation) {
                case BLOCK -> HumanoidModel.ArmPose.BLOCK;
                case BOW -> HumanoidModel.ArmPose.BOW_AND_ARROW;
                case SPEAR -> HumanoidModel.ArmPose.THROW_SPEAR;
                case CROSSBOW -> HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                case SPYGLASS -> HumanoidModel.ArmPose.SPYGLASS;
                case TOOT_HORN -> HumanoidModel.ArmPose.TOOT_HORN;
                case BRUSH -> HumanoidModel.ArmPose.BRUSH;
                default -> HumanoidModel.ArmPose.ITEM;
            };
        }
        if(!player.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack))
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        var custom=IClientItemExtensions.of(stack).getArmPose(player,hand,stack);
        return custom==null?HumanoidModel.ArmPose.ITEM:custom;
    }
}
