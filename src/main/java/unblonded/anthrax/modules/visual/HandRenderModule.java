package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** First-person hand scale/offset, the optional sword-blocking pose, and the neon hand/item outline. */
public final class HandRenderModule extends FeatureModule {
    public static final HandRenderModule INSTANCE = new HandRenderModule();
    private final Setting.Num scale = setting(Setting.Num.standalone("Scale", 1, .1, 5, .05));
    private final Setting.Num x = setting(Setting.Num.standalone("X offset", 0, -10, 10, .05));
    private final Setting.Num y = setting(Setting.Num.standalone("Y offset", 0, -10, 10, .05));
    private final Setting.Num z = setting(Setting.Num.standalone("Z offset", 0, -10, 10, .05));
    private final Setting.Bool swordBlocking = setting(Setting.Bool.standalone("Sword blocking", false));

    // ---- outline
    private final Setting.Bool outline = setting(Setting.Bool.standalone("Hand and item outline", true));
    private final Setting.Bool outlineArm = setting(Setting.Bool.standalone("Outline arm", true));
    private final Setting.Bool outlineItems = setting(Setting.Bool.standalone("Outline held items", true));
    private final Setting.Mode style = setting(Setting.Mode.standalone("Outline style", "Neon", "Neon", "Solid", "Soft", "Double"), "Neon is a glow with a bright core, Solid one clean line, Soft glow only, Double a two-tone stripe.");
    private final Setting.Mode outlineColor = setting(Setting.Mode.standalone("Outline color", "Rainbow",
            "Rainbow", "Gradient", "Custom", "Theme", "Fire", "Toxic", "Vaporwave", "Ocean", "Ice", "White"));
    private final Setting.Num hue = setting(Setting.Num.standalone("Custom hue", 285, 0, 360, 1));
    private final Setting.Num hue2 = setting(Setting.Num.standalone("Gradient second hue", 185, 0, 360, 1));
    private final Setting.Num saturation = setting(Setting.Num.standalone("Saturation", 95, 0, 100, 1));
    private final Setting.Num brightness = setting(Setting.Num.standalone("Brightness", 62, 20, 90, 1));
    private final Setting.Num speed = setting(Setting.Num.standalone("Color speed", 1, 0, 5, .05));
    private final Setting.Num spread = setting(Setting.Num.standalone("Color spread", 1.2, 0, 6, .05), "How much the colors change along the outline. 0 makes the whole outline one color.");
    private final Setting.Num width = setting(Setting.Num.standalone("Line width", 1.6, .5, 8, .1));
    private final Setting.Num glow = setting(Setting.Num.standalone("Glow strength", 70, 0, 100, 5));
    private final Setting.Num glowSize = setting(Setting.Num.standalone("Glow size", 1, .2, 3, .05), "How far the glow reaches out from the line.");
    private final Setting.Num core = setting(Setting.Num.standalone("White core", 55, 0, 100, 5), "How white the centre line of the Neon style is.");
    private final Setting.Bool glint = setting(Setting.Bool.standalone("Glint sweep", true), "A bright band that passes across the outline now and then.");
    private final Setting.Num glintSpeed = setting(Setting.Num.standalone("Glint speed", 1, .1, 4, .05));
    private final Setting.Num glintWidth = setting(Setting.Num.standalone("Glint width", .18, .04, .6, .01));
    private final Setting.Num pulse = setting(Setting.Num.standalone("Pulse amount", 0, 0, 100, 5), "Makes the outline breathe in width and glow.");
    private final Setting.Num pulseSpeed = setting(Setting.Num.standalone("Pulse speed", 1.5, .1, 6, .05));

    private HandRenderModule() { super("Hand Render", "Scale, move, pose and outline the first-person hand", "Visuals", true); }
    public float scale() { return (float)scale.get(); }
    public float x() { return (float)x.get(); }
    public float y() { return (float)y.get(); }
    public float z() { return (float)z.get(); }
    public boolean swordBlocking() { return swordBlocking.get(); }
    /** The outline only draws while the module itself is on. */
    public boolean outline() { return isEnabled() && outline.get(); }
    public boolean outlineArm() { return outline() && outlineArm.get(); }
    public boolean outlineItems() { return outline() && outlineItems.get(); }
    public String style() { return style.get(); }
    public String outlineColor() { return outlineColor.get(); }
    public float hue() { return (float) hue.get(); }
    public float hue2() { return (float) hue2.get(); }
    public float saturation() { return (float) saturation.get() / 100f; }
    public float brightness() { return (float) brightness.get() / 100f; }
    public float speed() { return (float) speed.get(); }
    public float spread() { return (float) spread.get(); }
    public float width() { return (float) width.get(); }
    public float glowStrength() { return (float) glow.get() / 100f; }
    public float glowSize() { return (float) glowSize.get(); }
    public float core() { return (float) core.get() / 100f; }
    public boolean glint() { return glint.get(); }
    public float glintSpeed() { return (float) glintSpeed.get(); }
    public float glintWidth() { return (float) glintWidth.get(); }
    public float pulse() { return (float) pulse.get() / 100f; }
    public float pulseSpeed() { return (float) pulseSpeed.get(); }
}
