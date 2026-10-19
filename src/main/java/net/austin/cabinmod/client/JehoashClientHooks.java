package net.austin.cabinmod.client;

import net.minecraft.world.phys.Vec3;

// Client-only calls made from common entity code. Only referenced inside isClientSide() branches,
// so a dedicated server never loads this class.
public final class JehoashClientHooks {
    private JehoashClientHooks() {
    }

    public static void onDeathStarted(Vec3 where) {
        OrbitalStrikeEffect.start(where);
    }
}
