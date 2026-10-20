package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.DiscoMusic;
import net.austin.cabinmod.entity.client.anim.BakedAnimations;
import net.austin.cabinmod.entity.client.generated.DjBoothModelBase;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

// The DJ booth model (rig_dj_booth). Drawn by Jehoash while it is part of him and by DjBoothRenderer once it
// is a block - same model, same pose, so the hand-over is seamless. Clips: jam_loop (faders, crossfader, knob
// and platter nudges matching his hands, driven by the beat clock) and interrupted (the slam). The platters'
// 33 1/3 rpm spin is added here.
public class DjBoothModel extends DjBoothModelBase<Entity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/entity/dj_booth/dj_booth.png");
    public static final ResourceLocation EMISSIVE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/entity/dj_booth/dj_booth_emissive.png");
    private static final String MOB = "dj_booth";

    private final Vector3f cache = new Vector3f();

    public DjBoothModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    public void poseJam(float beats, float platterDegrees) {
        reset();
        play("jam_loop", DiscoMusic.jamMillis(beats));
        spin(platterDegrees);
    }

    public void poseClip(String clip, long millis, float platterDegrees) {
        reset();
        play(clip, millis);
        spin(platterDegrees);
    }

    public void poseIdle(float platterDegrees) {
        reset();
        spin(platterDegrees);
    }

    private void reset() {
        this.root().getAllParts().forEach(ModelPart::resetPose);
    }

    private void play(String clip, long millis) {
        BakedAnimations.find(MOB, clip).ifPresent(def -> KeyframeAnimations.animate(this, def, millis, 1f, this.cache));
    }

    private void spin(float degrees) {
        for (String name : new String[]{"platter_l", "platter_r"}) {
            ModelPart platter = parts().get(name);
            if (platter != null) platter.yRot -= degrees * Mth.DEG_TO_RAD;
        }
    }
}
