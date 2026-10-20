package net.austin.cabinmod.client;

import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.minecraft.world.phys.Vec3;

// Client-only calls made from common code. Only reached inside client-side branches/tickers,
// so a dedicated server never loads this class.
public final class JehoashClientHooks {
    private JehoashClientHooks() {
    }

    public static void onDeathStarted(Vec3 where) {
        OrbitalStrikeEffect.start(where);
    }

    public static void tickBooth(DjBoothBlockEntity booth) {
        DiscoMusic.tickBooth(booth);
    }
}
