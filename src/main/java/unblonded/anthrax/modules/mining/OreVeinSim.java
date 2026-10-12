package unblonded.anthrax.modules.mining;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;

import java.lang.reflect.Method;

/**
 * The large copper and iron ore veins (with raw ore blocks and granite/tuff filler). These are not
 * features: they are placed while the terrain is filled from noise, before any ore feature runs. This
 * evaluates vanilla's own vein filler with the vein density functions interpolated across noise cells
 * exactly as {@code NoiseChunk} does (corner values, then lerp on y, x, z), so the result is bit-identical.
 */
final class OreVeinSim {
    private final NoiseChunk.BlockStateFiller filler;
    private final PositionalRandomFactory deepslate;

    OreVeinSim(HolderGetter.Provider registries, long seed) {
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        RandomState state = RandomState.create(registries, NoiseGeneratorSettings.OVERWORLD, seed);
        NoiseRouter router = state.router();
        deepslate = state.getOrCreateRandomFactory(net.minecraft.resources.Identifier.withDefaultNamespace("deepslate"));
        int cw = settings.noiseSettings().getCellWidth(), ch = settings.noiseSettings().getCellHeight();
        DensityFunction.Visitor interpolate = f -> f instanceof DensityFunctions.MarkerOrMarked m
                && m.type().name().equals("Interpolated") ? new Interpolated(m.wrapped(), cw, ch) : f;
        filler = create(router.veinToggle().mapAll(interpolate), router.veinRidged().mapAll(interpolate),
                router.veinGap().mapAll(interpolate), state.oreRandom());
    }

    private static NoiseChunk.BlockStateFiller create(DensityFunction toggle, DensityFunction ridged, DensityFunction gap,
                                                      PositionalRandomFactory random) {
        try {
            Method m = OreVeinifier.class.getDeclaredMethod("create", DensityFunction.class, DensityFunction.class,
                    DensityFunction.class, PositionalRandomFactory.class);
            m.setAccessible(true);
            return (NoiseChunk.BlockStateFiller) m.invoke(null, toggle, ridged, gap, random);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create the ore vein filler", e);
        }
    }

    /** What the vein filler put at this (solid) position during terrain generation, or null for plain stone. */
    BlockState at(int x, int y, int z) {
        if (y < -64 || y > 64) return null;   // veins span y -60..50
        return filler.calculate(new DensityFunction.SinglePointContext(x, y, z));
    }

    /**
     * Whether terrain at this position was deepslate: the overworld surface rule's vertical gradient
     * (always below y=0, never from y=8, a positional random roll in between).
     */
    boolean deepslate(int x, int y, int z) {
        if (y <= 0) return true;
        if (y >= 8) return false;
        double chance = Mth.map((double) y, 0.0, 8.0, 1.0, 0.0);
        return (double) deepslate.at(x, y, z).nextFloat() < chance;
    }

    /** NoiseChunk's interpolation of a density function between noise cell corners. */
    private static final class Interpolated implements DensityFunction {
        private final DensityFunction inner;
        private final int cw, ch;
        private final Long2DoubleOpenHashMap corners = new Long2DoubleOpenHashMap();

        Interpolated(DensityFunction inner, int cw, int ch) { this.inner = inner; this.cw = cw; this.ch = ch; }

        private double corner(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            if (corners.containsKey(key)) return corners.get(key);
            double v = inner.compute(new SinglePointContext(x, y, z));
            if (corners.size() > 400_000) corners.clear();
            corners.put(key, v);
            return v;
        }

        @Override public double compute(FunctionContext ctx) {
            int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
            int x0 = Math.floorDiv(x, cw) * cw, y0 = Math.floorDiv(y, ch) * ch, z0 = Math.floorDiv(z, cw) * cw;
            double dx = (double) (x - x0) / (double) cw, dy = (double) (y - y0) / (double) ch, dz = (double) (z - z0) / (double) cw;
            double n000 = corner(x0, y0, z0), n100 = corner(x0 + cw, y0, z0);
            double n010 = corner(x0, y0 + ch, z0), n110 = corner(x0 + cw, y0 + ch, z0);
            double n001 = corner(x0, y0, z0 + cw), n101 = corner(x0 + cw, y0, z0 + cw);
            double n011 = corner(x0, y0 + ch, z0 + cw), n111 = corner(x0 + cw, y0 + ch, z0 + cw);
            double xz00 = Mth.lerp(dy, n000, n010), xz10 = Mth.lerp(dy, n100, n110);
            double xz01 = Mth.lerp(dy, n001, n011), xz11 = Mth.lerp(dy, n101, n111);
            double z0v = Mth.lerp(dx, xz00, xz10), z1v = Mth.lerp(dx, xz01, xz11);
            return Mth.lerp(dz, z0v, z1v);
        }

        @Override public void fillArray(double[] out, ContextProvider provider) { provider.fillAllDirectly(out, this); }
        @Override public DensityFunction mapChildren(Visitor visitor) { return visitor.apply(new Interpolated(inner.mapAll(visitor), cw, ch)); }
        @Override public double minValue() { return inner.minValue(); }
        @Override public double maxValue() { return inner.maxValue(); }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return inner.codec(); }
    }
}
