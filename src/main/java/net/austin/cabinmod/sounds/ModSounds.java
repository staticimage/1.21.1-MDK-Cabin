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


    // Stand-in: points at the vanilla elder guardian curse in sounds.json until a real sound exists.
    public static final Supplier<SoundEvent> LIFE_OF_THE_PARTY_JUMPSCARE = registerSoundEvent("life_of_the_party_jumpscare");

    // Jehoash + DJ booth: stand-ins pointing at vanilla sounds in sounds.json until real ones exist.
    public static final Supplier<SoundEvent> JOASH_AMBIENT = registerSoundEvent("joash_ambient");
    public static final Supplier<SoundEvent> JOASH_HURT = registerSoundEvent("joash_hurt");
    public static final Supplier<SoundEvent> JOASH_DEATH = registerSoundEvent("joash_death");
    public static final Supplier<SoundEvent> JOASH_LASER = registerSoundEvent("joash_laser");
    public static final Supplier<SoundEvent> JOASH_TELEPORT = registerSoundEvent("joash_teleport");
    public static final Supplier<SoundEvent> JOASH_WELCOME = registerSoundEvent("joash_welcome");
    public static final Supplier<SoundEvent> JOASH_ILLUSIONS = registerSoundEvent("joash_illusions");
    public static final Supplier<SoundEvent> JOASH_ILLUSION_POP = registerSoundEvent("joash_illusion_pop");
    public static final Supplier<SoundEvent> DJ_BOOTH_USE = registerSoundEvent("dj_booth_use");

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
