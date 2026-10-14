package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.List;

/** Extra flair when you break a block: a shatter of its own pieces, coloured sparks and a fading glow box. */
public final class BlockEffectsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final BlockEffectsModule INSTANCE = new BlockEffectsModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Shatter", "Shatter", "Sparks", "Block colour dust", "Particles"),
            "Shatter throws extra block pieces, Sparks a burst of electric sparks, Block colour dust uses the block's map colour, Particles the choice below.");
    private final Setting.Mode particles = setting(Setting.Mode.standalone("Particles", "End rod", Fx.PARTICLES), "Used by the Particles style.");
    private final Palette palette = palette("Theme");
    private final Setting.Num amount = setting(Setting.Num.standalone("Amount", 16, 4, 60, 2), "How many particles each break throws.");
    private final Setting.Bool glow = setting(Setting.Bool.standalone("Glow box", true), "A box that swells and fades where the block was.");
    private final Setting.Bool onPlace = setting(Setting.Bool.standalone("Also on place", false), "Play the effect when you place blocks too.");

    private record Box(BlockPos pos, int rgb, long born) {}
    private final List<Box> boxes = new ArrayList<>();

    private BlockEffectsModule() { super("Block Effects", "Shatter, sparks and a glow box whenever you break a block"); }

    public void onBreak(BlockPos pos, BlockState state) {
        if (!isEnabled() || state.isAir()) return;
        play(pos, state);
    }

    public void onPlace(BlockPos pos, BlockState state) {
        if (isEnabled() && onPlace.get()) play(pos, state);
    }

    private void play(BlockPos pos, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = Vec3.atCenterOf(pos);
        int n = (int) amount.get();
        int mapRgb = state.getMapColor(mc.level, pos).col;
        switch (style.get()) {
            case "Shatter" -> Fx.burst(new BlockParticleOption(ParticleTypes.BLOCK, state), c, n, 0.8, 0.25);
            case "Sparks" -> Fx.burst(ParticleTypes.ELECTRIC_SPARK, c, n, 0.7, 0.35);
            case "Block colour dust" -> { for (int i = 0; i < n; i++) Fx.burst(Fx.dust(mapRgb, 1.1f), c, 1, 0.9, 0.12); }
            default -> { for (int i = 0; i < n; i++) Fx.burst(Fx.named(particles.get(), palette.rgb(i / (double) n)), c, 1, 0.8, 0.15); }
        }
        if (glow.get()) boxes.add(new Box(pos.immutable(), style.get().equals("Block colour dust") ? mapRgb : palette.rgb(), System.currentTimeMillis()));
        if (boxes.size() > 64) boxes.removeFirst();
    }

    @Override public boolean active() { return isEnabled() && !boxes.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        boxes.removeIf(b -> now - b.born > 450);
        for (Box b : boxes) {
            float t = (now - b.born) / 450f;
            double grow = 0.02 + 0.18 * t;
            AABB box = new AABB(b.pos).inflate(grow);
            int a = Math.round(200 * (1 - t));
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(WorldOverlays.argb(a, b.rgb), 2f, WorldOverlays.argb(a / 4, b.rgb)));
        }
    }

    @Override protected void onToggle(boolean enabled) { boxes.clear(); }
}
