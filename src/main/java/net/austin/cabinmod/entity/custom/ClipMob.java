package net.austin.cabinmod.entity.custom;

import net.austin.cabinmod.entity.anim.BakedClips;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Base for mobs animated with clips baked from Blender (see design/BLENDER_CONVENTIONS.md).
// The server plays one action clip at a time; its markers ("hit", "fire", "move:blink", ...) fire
// gameplay events on the exact frame. Until a clip is exported, stand-in timings from fallback() are used.
public abstract class ClipMob extends Monster {
    private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(ClipMob.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ACTION_START = SynchedEntityData.defineId(ClipMob.class, EntityDataSerializers.INT);

    // Client-side animation states, read by the model.
    public final AnimationState idleState = new AnimationState();
    public final AnimationState actionState = new AnimationState();
    public final AnimationState hurtState = new AnimationState();
    public final AnimationState deathState = new AnimationState();
    private int seenActionStart = -1;
    private boolean wasHurt;

    // Server-side action playback.
    private String action = "";
    private int actionTicks;
    private int actionLength;

    protected ClipMob(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    // The animations/<mobId>.json the clips come from.
    public abstract String mobId();

    // Stand-in length and event ticks for a clip that hasn't been exported yet.
    protected abstract Fallback fallback(String clip);

    public record Fallback(int lengthTicks, Map<String, Integer> events) {
        // Fallback.of(30, "fire", 18) -> 30 ticks long, "fire" event at tick 18 ("move:blink" for type:arg).
        public static Fallback of(int lengthTicks, Object... eventTicks) {
            Map<String, Integer> events = new LinkedHashMap<>();
            for (int i = 0; i + 1 < eventTicks.length; i += 2) {
                events.put((String) eventTicks[i], (Integer) eventTicks[i + 1]);
            }
            return new Fallback(lengthTicks, events);
        }
    }

    protected void onClipEvent(String clip, String type, String arg) {
    }

    protected void onClipEnd(String clip) {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, "");
        builder.define(ACTION_START, 0);
    }

    public String currentAction() {
        return this.entityData.get(ACTION);
    }

    public boolean isActing() {
        return !this.action.isEmpty();
    }

    public int clipLengthTicks(String clip) {
        return BakedClips.find(mobId(), clip).map(BakedClips.Clip::lengthTicks).orElseGet(() -> fallback(clip).lengthTicks());
    }

    protected void startAction(String clip) {
        this.action = clip;
        this.actionTicks = 0;
        this.actionLength = clipLengthTicks(clip);
        this.entityData.set(ACTION, clip);
        this.entityData.set(ACTION_START, this.tickCount);
    }

    protected void stopAction() {
        this.action = "";
        this.entityData.set(ACTION, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            tickClientAnimations();
        } else if (!this.isDeadOrDying()) {
            tickAction();
        }
    }

    private void tickAction() {
        if (this.action.isEmpty()) return;
        String running = this.action;
        int from = this.actionTicks - 1, to = this.actionTicks;
        Optional<BakedClips.Clip> clip = BakedClips.find(mobId(), running);
        if (clip.isPresent()) {
            for (BakedClips.Event e : clip.get().eventsBetween(from, to)) {
                onClipEvent(running, e.type(), e.arg());
            }
        } else {
            fallback(running).events().forEach((key, tick) -> {
                if (tick > from && tick <= to) {
                    String[] parts = key.split(":", 2);
                    onClipEvent(running, parts[0], parts.length > 1 ? parts[1] : "");
                }
            });
        }
        this.actionTicks++;
        // An event handler may have started a different action; only end the one that ran out.
        if (running.equals(this.action) && this.actionTicks >= this.actionLength) {
            stopAction();
            onClipEnd(running);
        }
    }

    private void tickClientAnimations() {
        this.idleState.startIfStopped(this.tickCount);

        int start = this.entityData.get(ACTION_START);
        if (currentAction().isEmpty()) {
            this.actionState.stop();
        } else if (start != this.seenActionStart) {
            this.seenActionStart = start;
            this.actionState.start(this.tickCount);
        }

        boolean hurt = this.hurtTime > 0;
        if (hurt && !this.wasHurt) this.hurtState.start(this.tickCount);
        this.wasHurt = hurt;

        if (this.isDeadOrDying()) this.deathState.startIfStopped(this.tickCount);
    }

    // Death lasts as long as the death clip instead of vanilla's 20-tick tip-over.
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= clipLengthTicks("death") && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }
}
