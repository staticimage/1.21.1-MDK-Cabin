package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// Used for both Jehoash and his illusions, so the decoys look exactly like him.
public class JehoashRenderer<T extends ClipMob> extends MobRenderer<T, JehoashPlaceholderModel<T>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/entity/joash/joash_placeholder.png");

    public JehoashRenderer(EntityRendererProvider.Context context) {
        super(context, new JehoashPlaceholderModel<>(context.bakeLayer(JehoashPlaceholderModel.LAYER_LOCATION)), 0.8f);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }
}
