package unblonded.anthrax.render;

/**
 * Chams for armour and held items: set while a chams entity's renderer submits its layers (render thread only),
 * so the submit collector can swap those parts to the chams look too.
 */
public final class ChamsContext {
    private ChamsContext() {}
    /** ARGB tint of the entity being submitted, or 0 when none. */
    public static int tint;
    public static boolean armour, items;
}
