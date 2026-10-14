package unblonded.anthrax.modules.cosmetic;

import unblonded.anthrax.modules.Setting;

/** Thin arcs around your crosshair for health, hunger, armour and air, so you can keep your eyes on the fight. */
public final class VitalsModule extends CosmeticModule {
    public static final VitalsModule INSTANCE = new VitalsModule();
    private final Setting.Num radius = setting(Setting.Num.standalone("Radius", 26, 14, 60, 1), "Distance from the crosshair, in pixels at scale 1.");
    private final Setting.Num thickness = setting(Setting.Num.standalone("Thickness", 3, 1, 6, 1), "");
    private final Setting.Bool numbers = setting(Setting.Bool.standalone("Numbers", false), "Small numbers at the end of each arc.");
    private final Setting.Bool fadeWhenFull = setting(Setting.Bool.standalone("Fade when full", true), "Arcs fade away when the value is full.");
    private final Setting.Bool armour = setting(Setting.Bool.standalone("Armour", true), "");
    private final Setting.Num opacity = setting(Setting.Num.standalone("Opacity", 85, 20, 100, 5), "");

    private VitalsModule() { super("Vitals", "Health, hunger, armour and air as thin arcs around your crosshair"); }

    public float radius() { return (float) radius.get(); }
    public int thickness() { return (int) thickness.get(); }
    public boolean numbers() { return numbers.get(); }
    public boolean fadeWhenFull() { return fadeWhenFull.get(); }
    public boolean armour() { return armour.get(); }
    public float opacity() { return (float) (opacity.get() / 100); }
}
