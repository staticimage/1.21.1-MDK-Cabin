package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// Jehoash and his decoys share the simplified cult robe, physical cloth and mirrored sphere.
public class JehoashRenderer<T extends ClipMob> extends MobRenderer<T, JehoashModel<T>> {
    private static final ResourceLocation TEXTURE = JehoashModel.BODY_TEXTURE;

    public JehoashRenderer(EntityRendererProvider.Context context) {
        super(context, new JehoashModel<>(context.bakeLayer(JehoashModel.LAYER_LOCATION)), 1.0f);
        this.addLayer(new JehoashFaceLayer<>(this));
        this.addLayer(new JehoashClothLayer<>(this));
        this.addLayer(new DiscoBallLayer<>(this));
        this.addLayer(new CultDaggerLayer<>(this));
        this.addLayer(new DjBoothLayer<>(this, context));
    }

    @Override public void render(T entity,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        if(entity instanceof net.austin.cabinmod.entity.custom.JehoashEntity j && j.isCollapsing() && j.stageTime()>=59 && j.stageTime()<63) return;
        super.render(entity,yaw,partial,pose,buffers,light);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }

    // His death is a clip (kneel, arms to the sky), not the vanilla tip-over.
    @Override
    protected float getFlipDegrees(T entity) {
        return 0f;
    }
}
