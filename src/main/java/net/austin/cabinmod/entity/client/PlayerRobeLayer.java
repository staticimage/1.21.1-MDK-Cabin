package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.JehoashRobeClient;
import net.austin.cabinmod.entity.client.physics.ClothSheet;
import net.austin.cabinmod.entity.client.physics.PlayerRobePose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Adds the garment to the real player renderer; skin, animation and equipment stay vanilla. */
public final class PlayerRobeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
            "textures/entity/joash/jehoash_brg_cloak.png");
    private final ClothSheet world, preview;

    public PlayerRobeLayer(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        world=ClothSheet.player(slim);
        preview=ClothSheet.player(slim);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                  float swing, float amount, float partial, float age, float yaw, float pitch) {
        if(!JehoashRobeClient.isWearing(player) || player.isInvisible() || player.isSpectator()) {
            forget(player);
            return;
        }
        var frame=JehoashRobeClient.frame(player,pose);
        if(frame==null) return;
        // Inventory rendering has an orthographic projection and temporarily
        // changes the same player's pose. It must not change their world cloth.
        boolean gui=RenderSystem.getProjectionMatrix().m33()>.5f;
        ClothSheet cloth=gui?preview:world;
        cloth.updatePlayer(player,age,PlayerRobePose.of(getParentModel(),frame,!gui),!gui);
        cloth.render(player,pose,buffers,TEXTURE,light,LivingEntityRenderer.getOverlayCoords(player,0));
    }

    public void forget(AbstractClientPlayer player) { world.forget(player);preview.forget(player); }
    public void clear() { world.clear();preview.clear(); }
}
