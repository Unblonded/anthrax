package unblonded.anthrax.modules;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns feature instances and dispatches their client-thread tick lifecycle. */
public final class FeatureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Anthrax/Features");
    private static final List<FeatureModule> FEATURES = new ArrayList<>();

    private FeatureManager() {}

    public static void register(FeatureModule feature) {
        FEATURES.add(feature);
    }

    public static List<FeatureModule> all() { return List.copyOf(FEATURES); }

    private static long lastFrameNs = System.nanoTime();

    /** Per-frame dispatch for features that need smooth motion, e.g. view rotation. */
    public static void frame(Minecraft client) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = now;
        for (FeatureModule feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            Safe.run(feature, () -> feature.frame(client, dt));
        }
    }

    public static void tick(Minecraft client) {
        for (FeatureModule feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            Safe.run(feature, () -> feature.tick(client));
        }
    }
}
