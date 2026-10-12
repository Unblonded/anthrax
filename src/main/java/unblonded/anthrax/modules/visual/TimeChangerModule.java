package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Client-side time-of-day override. Applied where the client clock is read (see ClientClockMixin),
 * so the server's time is never touched and real time resumes the instant the module is disabled.
 */
public final class TimeChangerModule extends FeatureModule {
    public static final TimeChangerModule INSTANCE = new TimeChangerModule();
    private static final long DAY = 24000L;

    private final Setting.Num time = setting(Setting.Num.standalone("Time", 1000, 0, 24000, 100));
    private final Setting.Bool cycle = setting(Setting.Bool.standalone("Animate cycle", false));
    private final Setting.Num speed = setting(Setting.Num.standalone("Cycle speed", 10, 1, 100, 1));
    private final long enabledAt = System.currentTimeMillis();

    private TimeChangerModule() {
        super("Time Changer", "Set the time of day you see, without touching the server", "Visuals", false);
    }

    /** Keeps the day count (moon phase) of the real clock and replaces only the time of day. */
    public long override(long realTotalTicks) {
        long timeOfDay = (long) time.get();
        if (cycle.get()) {
            long elapsedTicks = (System.currentTimeMillis() - enabledAt) / 50L;
            timeOfDay = Math.floorMod(timeOfDay + elapsedTicks * (long) speed.get(), DAY);
        }
        return realTotalTicks - Math.floorMod(realTotalTicks, DAY) + Math.min(timeOfDay, DAY - 1);
    }

    @Override public String hudInfo() {
        return cycle.get() ? "Cycle" : Long.toString((long) time.get());
    }
}
