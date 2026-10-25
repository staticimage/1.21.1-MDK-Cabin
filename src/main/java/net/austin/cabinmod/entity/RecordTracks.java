package net.austin.cabinmod.entity;

import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

/** Stable saved indices shared by the boss, booth, music and record renderer. */
public final class RecordTracks {
    public static final int COUNT = 6;
    public static int valid(int track) { return Math.floorMod(track, COUNT); }
    public static String name(int track) {
        return new String[]{"mo2_disc", "brg_disc", "hyg_disc", "bla_disc", "dna_disc", "sos_disc"}[valid(track)];
    }
    public static ItemStack stack(int track) {
        return new ItemStack(switch (valid(track)) {
            case 1 -> ModItems.BRG_DISC.get(); case 2 -> ModItems.HYG_DISC.get();
            case 3 -> ModItems.BLA_DISC.get(); case 4 -> ModItems.DNA_DISC.get();
            case 5 -> ModItems.SOS_DISC.get(); default -> ModItems.MO2_DISC.get();
        });
    }
    public static SoundEvent sound(int track) {
        return switch (valid(track)) {
            case 1 -> ModSounds.BRG_DISC.get(); case 2 -> ModSounds.HYG_DISC.get();
            case 3 -> ModSounds.BLA_DISC.get(); case 4 -> ModSounds.DNA_DISC.get();
            case 5 -> ModSounds.SOS_DISC.get(); default -> ModSounds.MO2_DISC.get();
        };
    }
    public static int index(ItemStack stack) {
        for (int i = 0; i < COUNT; i++) if (stack.is(stack(i).getItem())) return i;
        return -1;
    }
    private RecordTracks() {}
}
