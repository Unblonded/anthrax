package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** First-person hand scale/offset and the optional sword-blocking pose. */
public final class HandRenderModule extends FeatureModule {
    public static final HandRenderModule INSTANCE = new HandRenderModule();
    private final Setting.Num scale = setting(Setting.Num.standalone("Scale", 1, .1, 5, .05));
    private final Setting.Num x = setting(Setting.Num.standalone("X offset", 0, -10, 10, .05));
    private final Setting.Num y = setting(Setting.Num.standalone("Y offset", 0, -10, 10, .05));
    private final Setting.Num z = setting(Setting.Num.standalone("Z offset", 0, -10, 10, .05));
    private final Setting.Bool swordBlocking = setting(Setting.Bool.standalone("Sword blocking", false));
    private HandRenderModule() { super("Hand Render", "Scale, move, and pose the first-person hand", "Visuals", false); }
    public float scale() { return (float)scale.get(); }
    public float x() { return (float)x.get(); }
    public float y() { return (float)y.get(); }
    public float z() { return (float)z.get(); }
    public boolean swordBlocking() { return swordBlocking.get(); }
}
