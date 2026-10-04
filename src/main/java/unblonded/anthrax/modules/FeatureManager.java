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

    public static void tick(Minecraft client) {
        for (FeatureModule feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.tick(client);
            } catch (RuntimeException exception) {
                try { feature.setEnabled(false); }
                catch (RuntimeException toggleFailure) { exception.addSuppressed(toggleFailure); }
                LOGGER.error("Disabled feature '{}' after a client tick failure", feature.name, exception);
            }
        }
    }
}
