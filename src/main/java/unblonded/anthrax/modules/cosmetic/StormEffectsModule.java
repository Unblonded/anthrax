package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;

/**
 * A storm only you see: rain (or snow in cold biomes, as vanilla decides) and harmless lightning strikes around
 * you. The real weather is untouched; StormMixin only raises the rain and thunder levels your client reads.
 */
public final class StormEffectsModule extends CosmeticModule {
    public static final StormEffectsModule INSTANCE = new StormEffectsModule();
    private final Setting.Num rain = setting(Setting.Num.standalone("Rain", 100, 0, 100, 5), "How heavy the rain is. 0 for lightning only.");
    private final Setting.Num thunder = setting(Setting.Num.standalone("Darkness", 70, 0, 100, 5), "How dark and stormy the sky gets.");
    private final Setting.Bool lightning = setting(Setting.Bool.standalone("Lightning strikes", true), "Bolts strike the ground around you, with thunder.");
    private final Setting.Num frequency = setting(Setting.Num.standalone("Strikes per minute", 6, 1, 60, 1), "");
    private final Setting.Num distance = setting(Setting.Num.standalone("Strike distance", 50, 15, 120, 5), "How far away bolts land, roughly.");

    private float level;   // fades in and out instead of switching

    private StormEffectsModule() { super("Storm Effects", "A client-side thunderstorm with rain and harmless lightning strikes. The real weather is untouched"); }

    @Override public void tick(Minecraft mc) {
        level = Math.min(1, level + 0.02f);
        if (!lightning.get() || mc.level == null || mc.player == null || mc.level.dimensionType().hasCeiling()) return;
        if (Fx.RND.nextDouble() < frequency.get() / 1200.0) {
            double a = Fx.RND.nextDouble() * Math.PI * 2, d = distance.get() * (0.4 + Fx.RND.nextDouble() * 0.8);
            int x = (int) Math.floor(mc.player.getX() + Math.cos(a) * d), z = (int) Math.floor(mc.player.getZ() + Math.sin(a) * d);
            int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            Fx.lightning(new Vec3(x + 0.5, y, z + 0.5));
        }
    }

    @Override protected void onToggle(boolean enabled) { level = 0; }

    /** Raised rain level for StormMixin. */
    public float rain(float vanilla) { return isEnabled() ? Math.max(vanilla, level * (float) (rain.get() / 100)) : vanilla; }

    /** Thunder is multiplied by rain in vanilla, so this is the darkness on its own. */
    public float thunder(float vanilla) { return isEnabled() ? Math.max(vanilla, level * (float) (thunder.get() / 100)) : vanilla; }
}
