package net.austin.cabinmod.sounds;

import net.austin.cabinmod.CabinMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, CabinMod.MOD_ID);

    public static final Supplier<SoundEvent> CABIN_BLOCK_BREAK = registerSoundEvent("cabin_block_break");
    public static final Supplier<SoundEvent> CABIN_BLOCK_STEP = registerSoundEvent("cabin_block_step");
    public static final Supplier<SoundEvent> CABIN_BLOCK_PLACE = registerSoundEvent("cabin_block_place");
    public static final Supplier<SoundEvent> CABIN_BLOCK_HIT = registerSoundEvent("cabin_block_hit");
    public static final Supplier<SoundEvent> CABIN_BLOCK_FALL = registerSoundEvent("cabin_block_fall");

    public static final DeferredSoundType CABIN_BLOCK_SOUNDS = new DeferredSoundType(1f, 1f,
            ModSounds.CABIN_BLOCK_BREAK,
            ModSounds.CABIN_BLOCK_STEP,
            ModSounds.CABIN_BLOCK_PLACE,
            ModSounds.CABIN_BLOCK_HIT,
            ModSounds.CABIN_BLOCK_FALL);


    // Life of the Party jumpscare stinger (tools/audio/build_sounds.py).
    public static final Supplier<SoundEvent> LIFE_OF_THE_PARTY_JUMPSCARE = registerSoundEvent("life_of_the_party_jumpscare");

    // Jehoash + DJ booth - see design/JEHOASH_V2.md section 7 and sounds/credits.txt
    // (tools/audio/build_illusion_sounds.py for joash_illusions / joash_illusion_pop, tools/audio/build_sounds.py
    // for teleport/scratch, tools/audio/build_sounds_v2.py for the rest).
    public static final Supplier<SoundEvent> JOASH_AMBIENT = registerSoundEvent("joash_ambient");
    public static final Supplier<SoundEvent> JOASH_HURT = registerSoundEvent("joash_hurt");
    public static final Supplier<SoundEvent> JOASH_DEATH = registerSoundEvent("joash_death");
    public static final Supplier<SoundEvent> JOASH_WELCOME_VOICE = registerSoundEvent("joash_welcome_voice");
    public static final Supplier<SoundEvent> JOASH_ROAR = registerSoundEvent("joash_roar");
    public static final Supplier<SoundEvent> JOASH_LASER_CHARGE = registerSoundEvent("joash_laser_charge");
    public static final Supplier<SoundEvent> JOASH_LASER = registerSoundEvent("joash_laser");
    public static final Supplier<SoundEvent> JOASH_TELEPORT = registerSoundEvent("joash_teleport");
    public static final Supplier<SoundEvent> JOASH_ILLUSIONS = registerSoundEvent("joash_illusions");
    public static final Supplier<SoundEvent> JOASH_ILLUSION_POP = registerSoundEvent("joash_illusion_pop");
    public static final Supplier<SoundEvent> JOASH_STRIKE = registerSoundEvent("joash_strike");
    public static final Supplier<SoundEvent> DJ_BOOTH_USE = registerSoundEvent("dj_booth_use");
    public static final Supplier<SoundEvent> DJ_BOOTH_NEEDLE = registerSoundEvent("dj_booth_needle");
    public static final Supplier<SoundEvent> DJ_BOOTH_SLAM = registerSoundEvent("dj_booth_slam");
    // The Disco rave track: "Phonk Reel" by kulakovka (Pixabay), see sounds/credits.txt.
    public static final Supplier<SoundEvent> DISCO_MUSIC = registerSoundEvent("disco_music");

    public static final Supplier<SoundEvent> MO2_DISC = registerSoundEvent("mo2_disc");
    public static final ResourceKey<JukeboxSong> MO2_DISC_KEY = createSong("mo2_disc");

    public static final Supplier<SoundEvent> BRG_DISC = registerSoundEvent("brg_disc");
    public static final ResourceKey<JukeboxSong> BRG_DISC_KEY = createSong("brg_disc");

    public static final Supplier<SoundEvent> HYG_DISC = registerSoundEvent("hyg_disc");
    public static final ResourceKey<JukeboxSong> HYG_DISC_KEY = createSong("hyg_disc");

    public static final Supplier<SoundEvent> BLA_DISC = registerSoundEvent("bla_disc");
    public static final ResourceKey<JukeboxSong> BLA_DISC_KEY = createSong("bla_disc");

    public static final Supplier<SoundEvent> DNA_DISC = registerSoundEvent("dna_disc");
    public static final ResourceKey<JukeboxSong> DNA_DISC_KEY = createSong("dna_disc");

    public static final Supplier<SoundEvent> SOS_DISC = registerSoundEvent("sos_disc");
    public static final ResourceKey<JukeboxSong> SOS_DISC_KEY = createSong("sos_disc");



    // Transient Diviner cinematic (tools/audio/build_diviner_sounds.py, synthesized).
    public static final Supplier<SoundEvent> DIVINER_ASCEND = registerSoundEvent("diviner_ascend");
    public static final Supplier<SoundEvent> DIVINER_SPACE_LOOP = registerSoundEvent("diviner_space_loop");
    public static final Supplier<SoundEvent> DIVINER_LAUNCH = registerSoundEvent("diviner_launch");
    public static final Supplier<SoundEvent> DIVINER_DESCEND = registerSoundEvent("diviner_descend");
    public static final Supplier<SoundEvent> DIVINER_IMPACT = registerSoundEvent("diviner_impact");
    public static final Supplier<SoundEvent> DIVINER_IMPACT_STINGER = registerSoundEvent("diviner_impact_stinger");
    public static final Supplier<SoundEvent> DIVINER_FALL_WORLD = registerSoundEvent("diviner_fall_world");
    public static final Supplier<SoundEvent> DIVINER_CRAFT = registerSoundEvent("diviner_craft");
    // the heavens: panel interaction, the Cabin's voice, what other players hear
    public static final Supplier<SoundEvent> DIVINER_SLOT_PLACE = registerSoundEvent("diviner_slot_place");
    public static final Supplier<SoundEvent> DIVINER_SLOT_TAKE = registerSoundEvent("diviner_slot_take");
    public static final Supplier<SoundEvent> DIVINER_STAR_TICK = registerSoundEvent("diviner_star_tick");
    public static final Supplier<SoundEvent> DIVINER_SPACE_SWELL = registerSoundEvent("diviner_space_swell");
    public static final Supplier<SoundEvent> DIVINER_CABIN_HUM = registerSoundEvent("diviner_cabin_hum");
    public static final Supplier<SoundEvent> DIVINER_LAUNCH_WORLD = registerSoundEvent("diviner_launch_world");
    public static final Supplier<SoundEvent> DIVINER_HEAVENS_OPEN = registerSoundEvent("diviner_heavens_open");

    private static ResourceKey<JukeboxSong> createSong(String name){
        return ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,name));
    }

    private static Supplier<SoundEvent> registerSoundEvent(String name){
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, ()-> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus){
        SOUND_EVENTS.register(eventBus);
    }
}
