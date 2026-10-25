package net.austin.cabinmod.entity.client;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** One choreography drives both hands and controls, in mesh pixels including the booth's -16 Z offset. */
/*
 * HAND/CONTROL COORDINATE CONTRACT
 * Targets are mesh pixels AFTER the booth's -16 Z translation. Platters sit at X=+-14,
 * Y=-4.8,Z=-14.5; palm centers are just above them. The left hand moves to the actual
 * crossfader, and both hand and slider use the same crossfader value. The right record
 * scratches with its hand, then spins freely during the released part of each 16-beat
 * phrase. The six SCRATCH_RATE entries/style offsets are per-record choreography.
 * RecordDance/DiscoMusic supply measured song beat/energy timing, not a fixed BPM.
 * These numbers animate the hands/turntables only. The rave screen is fully static.
 */
public final class DjPerformance {
    private static final float[] SCRATCH_RATE={.5f,.75f,.5f,1f,.75f,1f};
    public record Pose(Vector3f rightHand,Vector3f leftHand,float scratchDegrees,float crossfader,float mixerWeight,float kick) {}
    public static Pose sample(int track,float beats,float energy) {
        int style=Math.floorMod(track,6);
        float phrase=(beats+style*2)%16;
        if(phrase<0) phrase+=16;
        float stroke=0;
        if(phrase<6) {
            float envelope=Mth.sin(phrase/6*Mth.PI);
            stroke=Mth.sin(beats*Mth.TWO_PI*SCRATCH_RATE[style])*envelope*envelope;
        }
        float angle=stroke*.55f;
        float release=phrase<6 ? 0 : Mth.sin((phrase-6)/10*Mth.PI);
        float recordDegrees=phrase<6 ? angle*Mth.RAD_TO_DEG : 1080*smooth((phrase-6)/10);
        Vector3f right=new Vector3f(-14+Mth.sin(angle)*3.6f,-5.9f-2*release,-14.5f-Mth.cos(angle)*3.6f);
        float mixer=phrase<8 ? 0 : phrase<10 ? smooth((phrase-8)/2) : phrase<14 ? 1 : 1-smooth((phrase-14)/2);
        float fader=Mth.sin(beats*Mth.PI*.5f)*2.1f;
        float lift=Mth.sin(mixer*Mth.PI)*3;
        Vector3f left=new Vector3f(Mth.lerp(mixer,14,fader),Mth.lerp(mixer,-5.9f,-7.7f)-lift,Mth.lerp(mixer,-18.1f,-19));
        float beatPulse=.5f+.5f*Mth.cos((beats-Mth.floor(beats))*Mth.TWO_PI);
        return new Pose(right,left,recordDegrees,fader*mixer,mixer,beatPulse*beatPulse*energy);
    }
    private static float smooth(float value) { return value*value*(3-2*value); }
    private DjPerformance() {}
}
