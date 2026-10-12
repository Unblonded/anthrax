package unblonded.anthrax.modules.mining;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Confirms a known seed against the world's bedrock. Vanilla's surface rules decide every bedrock block
 * in the fading layers with a positional random roll derived from the seed: the floor goes from always
 * bedrock at the bottom to never 5 blocks up, and the Nether ceiling does the reverse over its top 5
 * blocks. This regenerates those rolls for the given seed and compares them block by block with the
 * loaded chunks. The right seed matches 100%; any other seed agrees only by chance (about 60%).
 */
final class BedrockCheck {
    record Result(int matched, int total, int chunks) {
        double score() { return total == 0 ? 0 : matched / (double) total; }
    }

    private BedrockCheck() {}

    static Result run(SeedOrePredictor.Snapshot snap, long seed) {
        var key = snap.nether ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.OVERWORLD;
        var registries = VanillaRegistries.createLookup();
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value().noiseSettings();
        RandomState state = RandomState.create(registries, key, seed);
        PositionalRandomFactory floor = state.getOrCreateRandomFactory(Identifier.withDefaultNamespace("bedrock_floor"));
        PositionalRandomFactory roof = state.getOrCreateRandomFactory(Identifier.withDefaultNamespace("bedrock_roof"));
        int bottom = settings.minY(), top = settings.minY() + settings.height() - 1;

        int matched = 0, total = 0, chunks = 0;
        for (long chunk : snap.chunks.keySet()) {
            int bx = ChunkPos.getX(chunk) << 4, bz = ChunkPos.getZ(chunk) << 4;
            chunks++;
            for (int x = bx; x < bx + 16; x++)
                for (int z = bz; z < bz + 16; z++) {
                    // floor: verticalGradient("bedrock_floor", bottom, aboveBottom(5))
                    for (int y = bottom + 1; y < bottom + 5; y++) {
                        boolean expected = gradient(floor, x, y, z, bottom, bottom + 5);
                        boolean actual = snap.get(x, y, z).is(Blocks.BEDROCK);
                        total++;
                        if (expected == actual) matched++;
                    }
                    // Nether roof: not(verticalGradient("bedrock_roof", belowTop(5), top))
                    if (snap.nether)
                        for (int y = top - 4; y < top; y++) {
                            boolean expected = !gradient(roof, x, y, z, top - 5, top);
                            boolean actual = snap.get(x, y, z).is(Blocks.BEDROCK);
                            total++;
                            if (expected == actual) matched++;
                        }
                }
        }
        return new Result(matched, total, chunks);
    }

    /** SurfaceRules vertical gradient: true at and below {@code trueAt}, false from {@code falseAt}, a roll in between. */
    private static boolean gradient(PositionalRandomFactory random, int x, int y, int z, int trueAt, int falseAt) {
        if (y <= trueAt) return true;
        if (y >= falseAt) return false;
        double chance = Mth.map((double) y, (double) trueAt, (double) falseAt, 1.0, 0.0);
        return (double) random.at(x, y, z).nextFloat() < chance;
    }
}
