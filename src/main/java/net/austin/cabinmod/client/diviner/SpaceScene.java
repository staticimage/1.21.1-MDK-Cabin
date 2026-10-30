package net.austin.cabinmod.client.diviner;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.mesh.CabinMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Draws the Blender planet, its cloud shell, the atmosphere and the comet into the post chain's "planet"
 * target, which shaders/program/diviner_cosmos.fsh composites over the stars.
 * Space units: planet radius 1, centred at the origin, with the diviner's site rotated to +Y so the
 * space camera can share the real camera's yaw/pitch (looking down at the ground = looking down at the planet).
 */
final class SpaceScene {
    private static final ResourceLocation ALBEDO = tex("planet_albedo");
    private static final ResourceLocation DATA = tex("planet_data");
    private static final ResourceLocation CLOUDS = tex("planet_clouds");
    // a green coast on the baked map (u, v from the top) - the diviner's spot on the planet
    private static final float SITE_U = 0.322f, SITE_V = 0.39f;

    private static Matrix4f planetModel;
    private static boolean filtered;

    private SpaceScene() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/diviner/" + name + ".png");
    }

    static void reset() {
        planetModel = null;
        filtered = false;
    }

    static void render(RenderTarget target, Matrix4f view, Matrix4f proj, Vector3f camera, Vector3f sun, float time,
                       Vector3f comet, float cometScale) {
        if (target == null) return;
        Minecraft mc = Minecraft.getInstance();
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        CabinMesh planet = CabinMesh.get("planet"), clouds = CabinMesh.get("planet_clouds"), atmo = CabinMesh.get("planet_atmo");
        if (planet == null || DivinerShaders.planet == null) return;
        if (planetModel == null) {
            Vector3f site = planet.directionAtUv(SITE_U, SITE_V);
            planetModel = new Matrix4f().rotation(new Quaternionf().rotationTo(site, new Vector3f(0, 1, 0)));
        }
        if (!filtered) {
            for (ResourceLocation id : new ResourceLocation[]{ALBEDO, DATA, CLOUDS}) mc.getTextureManager().getTexture(id).setFilter(true, false);
            filtered = true;
        }
        RenderSystem.setShaderTexture(0, ALBEDO);
        RenderSystem.setShaderTexture(1, DATA);
        RenderSystem.setShaderTexture(2, CLOUDS);
        float cloudSpin = time * 0.0015f;

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        common(DivinerShaders.planet, planetModel, camera, sun, cloudSpin);
        planet.draw(view, proj, DivinerShaders.planet);

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        if (clouds != null && DivinerShaders.clouds != null) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            common(DivinerShaders.clouds, planetModel, camera, sun, cloudSpin);
            clouds.draw(view, proj, DivinerShaders.clouds);
        }
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        if (atmo != null && DivinerShaders.atmosphere != null) {
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            common(DivinerShaders.atmosphere, new Matrix4f(), camera, sun, cloudSpin);
            DivinerShaders.atmosphere.safeGetUniform("Intensity").set(1f);
            atmo.draw(view, proj, DivinerShaders.atmosphere);
            RenderSystem.enableDepthTest();
        }
        CabinMesh cometMesh = CabinMesh.get("comet");
        if (comet != null && cometMesh != null && DivinerShaders.comet != null) {
            RenderSystem.disableCull();
            // tail (+Y in the mesh) streams away from the planet
            Matrix4f model = new Matrix4f().translate(comet)
                    .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(comet).normalize()))
                    .scale(cometScale);
            ShaderInstance s = DivinerShaders.comet;
            s.safeGetUniform("ModelMat").set(model);
            s.safeGetUniform("CameraPos").set(camera.x, camera.y, camera.z);
            s.safeGetUniform("Tint").set(1f, 1f, 1f, 1f);
            s.safeGetUniform("Glow").set(1.6f);
            s.safeGetUniform("iTime").set(time);
            cometMesh.draw(view, proj, s);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        mc.getMainRenderTarget().bindWrite(true);
    }

    private static void common(ShaderInstance s, Matrix4f model, Vector3f camera, Vector3f sun, float cloudSpin) {
        s.safeGetUniform("ModelMat").set(model);
        s.safeGetUniform("CameraPos").set(camera.x, camera.y, camera.z);
        s.safeGetUniform("SunDir").set(sun.x, sun.y, sun.z);
        s.safeGetUniform("CloudSpin").set(cloudSpin);
    }
}
