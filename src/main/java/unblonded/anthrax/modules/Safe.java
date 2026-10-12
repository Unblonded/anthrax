package unblonded.anthrax.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unblonded.anthrax.gui.Notifications;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Crash safety: one broken module must never take the game down. Render and tick entry points run their
 * module code through {@link #run}; a failure turns that module off, shows a toast once and logs the
 * stack trace, and everything else keeps working.
 */
public final class Safe {
    private Safe() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Anthrax/Safe");
    /** Things already reported this session, so a failure that repeats every frame only toasts once. */
    private static final Set<String> REPORTED = Collections.synchronizedSet(new HashSet<>());

    /** Runs a module's code; on failure disables the module and tells the player. */
    public static void run(FeatureModule module, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            fail(module, module.name, e);
        }
    }

    /** Runs code that belongs to no single module (a HUD block, an overlay); on failure it is only reported. */
    public static boolean run(String what, Runnable body) {
        try {
            body.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            fail(null, what, e);
            return false;
        }
    }

    /** Turns the module off (if any), logs the error and shows a single toast for it. */
    public static void fail(FeatureModule module, String what, Throwable e) {
        if (module != null) {
            try { module.setEnabled(false); } catch (RuntimeException toggle) { e.addSuppressed(toggle); }
        }
        if (REPORTED.add(what)) {
            LOGGER.error("'{}' failed and was {}", what, module != null ? "turned off" : "skipped", e);
            try {
                Notifications.error(what, module != null ? "Crashed and was turned off (details in the log)"
                        : "Failed to draw (details in the log)");
            } catch (RuntimeException ignored) {}
        }
    }

    /** Lets a module report again after the player turns it back on. */
    public static void forgive(String what) { REPORTED.remove(what); }
}
