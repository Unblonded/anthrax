package unblonded.anthrax.modules.cosmetic;

import unblonded.anthrax.modules.Setting;

/**
 * First-person hand extras on top of Hand Render: tilt each hand, slow or speed up the swing, skip the
 * item-switch dip and hide the offhand. Applied by HandTweaksMixin; swing speed only changes how it looks.
 */
public final class HandTweaksModule extends CosmeticModule {
    public static final HandTweaksModule INSTANCE = new HandTweaksModule();
    private final Setting.Num mainX = setting(Setting.Num.standalone("Main hand tilt X", 0, -90, 90, 1), "Degrees.");
    private final Setting.Num mainY = setting(Setting.Num.standalone("Main hand tilt Y", 0, -90, 90, 1), "Degrees.");
    private final Setting.Num mainZ = setting(Setting.Num.standalone("Main hand tilt Z", 0, -90, 90, 1), "Degrees.");
    private final Setting.Num offX = setting(Setting.Num.standalone("Offhand tilt X", 0, -90, 90, 1), "Degrees.");
    private final Setting.Num offY = setting(Setting.Num.standalone("Offhand tilt Y", 0, -90, 90, 1), "Degrees.");
    private final Setting.Num offZ = setting(Setting.Num.standalone("Offhand tilt Z", 0, -90, 90, 1), "Degrees.");
    private final Setting.Num offLower = setting(Setting.Num.standalone("Lower offhand", 0, 0, 0.6, 0.02), "Moves the offhand item down, e.g. to keep a shield or totem out of view.");
    private final Setting.Num swing = setting(Setting.Num.standalone("Swing speed", 1.0, 0.25, 3, 0.05), "Above 1 is a slower, longer swing; below 1 a snappier one. Visual only.");
    private final Setting.Bool noEquip = setting(Setting.Bool.standalone("No switch animation", false), "Items don't dip down when you switch slots or use them.");
    private final Setting.Bool hideOffhand = setting(Setting.Bool.standalone("Hide offhand", false), "Don't draw the offhand item in first person.");

    private HandTweaksModule() { super("Hand Tweaks", "Tilt each first-person hand, change the swing speed, remove the switch animation or hide the offhand"); }

    public float[] main() { return new float[]{(float) mainX.get(), (float) mainY.get(), (float) mainZ.get()}; }
    public float[] off() { return new float[]{(float) offX.get(), (float) offY.get(), (float) offZ.get()}; }
    public float offLower() { return (float) offLower.get(); }
    public float swing() { return isEnabled() ? (float) swing.get() : 1f; }
    public boolean noEquip() { return isEnabled() && noEquip.get(); }
    public boolean hideOffhand() { return isEnabled() && hideOffhand.get(); }
}
