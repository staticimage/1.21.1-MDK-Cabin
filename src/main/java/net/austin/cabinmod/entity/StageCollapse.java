package net.austin.cabinmod.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared server/render timeline. The dodge occurs just before the beam crosses head height. */
/*
 * COLLAPSE TIMELINE CONTRACT
 * All timings are server ticks: DODGE=59, IMPACT=74, END=110. JehoashEntity uses these
 * for gameplay; FallingStageRigRenderer, ArenaLightEntity, JehoashModel and the world
 * FX use the same timeline. drop/roll/retreat describe visual rig motion. The beam
 * retreats behind the booth as it falls, preserving the locked record booth. Change
 * timing constants and pose key ranges together when extending the cutscene. No code
 * in this system takes over a player's camera; multiplayer clients share server time.
 */
public final class StageCollapse {
    public static final int DODGE=59, IMPACT=74, END=110;
    public static final int REVEAL_START=82, CLOTH_RELEASE=88, REVEAL_RECOVERY_END=128;
    public static final int CLOTH_LIFETIME=38, CLOTH_FADE_TICKS=8;

    /** The same envelope runs on both sides of the collapse/on-foot boundary. */
    public static float garmentThrow(float t) {
        float peak = 97.5f;
        return t < peak ? smooth(Mth.clamp((t-REVEAL_START)/(peak-REVEAL_START),0,1))
                : 1-smooth(Mth.clamp((t-peak)/(REVEAL_RECOVERY_END-peak),0,1));
    }

    public static float revealRecovery(float t) {
        return smooth(Mth.clamp((t-END)/(REVEAL_RECOVERY_END-END),0,1));
    }
    public static double drop(float t) {
        if(t<20) return 0;
        if(t<48) return .65*smooth((t-20)/28);
        if(t<IMPACT) { double f=(t-48)/(IMPACT-48); return .65+4.05*f*f; }
        return 4.7-.18*Math.exp(-(t-IMPACT)*.13)*Math.abs(Math.sin((t-IMPACT)*.65));
    }
    public static float roll(float t) {
        if(t<48) return (float)(Math.sin(t*.55)*(.3+t*.035));
        if(t<IMPACT) return (float)(Math.sin((t-48)/(IMPACT-48)*Math.PI)*9);
        return (float)(Math.sin((t-IMPACT)*.48)*2*Math.exp(-(t-IMPACT)*.13));
    }
    public static double retreat(float t) { return -1.6*smooth(Mth.clamp((t-42)/32,0,1)); }
    public static Vec3 offset(float t,double x,double y,double z) {
        double r=roll(t)*Math.PI/180;
        return new Vec3(x*Math.cos(r)-y*Math.sin(r),x*Math.sin(r)+y*Math.cos(r)-drop(t),z+retreat(t));
    }
    private static float smooth(float f) { return f*f*(3-2*f); }
    private StageCollapse() {}
}
