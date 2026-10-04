package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Client-side time override. Server time resumes naturally when the module is disabled. */
public final class TimeChangerModule extends FeatureModule {
    private final Setting.Num time = setting(Setting.Num.standalone("Time", 1000, 0, 24000, 100));
    private ClientLevel capturedLevel;
    private long originalTime;

    public TimeChangerModule() {
        super("Time Changer", "Set the local world's displayed time", "Visuals", false);
    }

    @Override public void tick(Minecraft client) {
        if (client.level == null) return;
        if (capturedLevel != client.level) {
            capturedLevel = client.level;
            originalTime = client.level.getGameTime();
        }
        client.level.setTimeFromServer((long) time.get());
    }

    @Override protected void onToggle(boolean enabled) {
        if (enabled) {
            capturedLevel = null;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (capturedLevel != null && client.level == capturedLevel) capturedLevel.setTimeFromServer(originalTime);
        capturedLevel = null;
    }
}
