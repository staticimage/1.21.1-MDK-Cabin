package net.austin.cabinmod.client.diviner;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.mesh.CabinMesh;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

// Core shaders for the Transient Diviner cinematic (assets/cabinmod/shaders/core/diviner_*.json).
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DivinerShaders {
    static ShaderInstance planet, clouds, atmosphere, comet, crater, fx, trail, ground, circle, glass, rift;

    private DivinerShaders() {
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        VertexFormat mesh = DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL;
        VertexFormat sprite = DefaultVertexFormat.POSITION_TEX_COLOR;
        event.registerShader(shader(event, "diviner_planet", mesh), s -> planet = s);
        event.registerShader(shader(event, "diviner_clouds", mesh), s -> clouds = s);
        event.registerShader(shader(event, "diviner_atmosphere", mesh), s -> atmosphere = s);
        event.registerShader(shader(event, "diviner_comet", mesh), s -> comet = s);
        event.registerShader(shader(event, "diviner_crater", mesh), s -> crater = s);
        event.registerShader(shader(event, "diviner_fx", sprite), s -> fx = s);
        event.registerShader(shader(event, "diviner_trail", sprite), s -> trail = s);
        event.registerShader(shader(event, "diviner_ground", sprite), s -> ground = s);
        event.registerShader(shader(event, "diviner_circle", sprite), s -> circle = s);
        event.registerShader(shader(event, "diviner_glass", DefaultVertexFormat.POSITION_TEX), s -> glass = s);
        event.registerShader(shader(event, "diviner_rift", sprite), s -> rift = s);
    }

    private static ShaderInstance shader(RegisterShadersEvent event, String name, VertexFormat format) throws IOException {
        return new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, name), format);
    }

    @SubscribeEvent
    public static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            CabinMesh.clearCache();
            StarColors.clearCache();
            DivinerCinematic.onResourcesReloaded();
        });
    }
}
