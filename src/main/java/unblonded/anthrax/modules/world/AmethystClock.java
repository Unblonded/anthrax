package unblonded.anthrax.modules.world;

/**
 * Estimates hours of player presence from amethyst geode maturity.
 *
 * <p>Generation (GeodeFeature, vanilla config): 35% of budding amethyst blocks get one crystal of a
 * uniformly random stage (small, medium, large, cluster); other faces start empty. Growth: a budding
 * block is random-ticked on average every 4096/3 game ticks and grows with chance 1/5 toward one of six
 * faces, so each face advances about 72000 * 3/4096 * 1/5 * 1/6 = 1.76 stages per hour, capped at a
 * full cluster. Random ticks only happen inside a player's simulation distance, so the measured
 * maturity is effectively "player time spent near here". Harvesting crystals resets faces, so the
 * estimate is a lower bound.
 */
final class AmethystClock {
    static final double STEPS_PER_HOUR = 72000.0 * 3 / 4096 / 5 / 6;
    private static final double MAX_HOURS = 12;

    private AmethystClock() {}

    /** @param faces open or budded faces, @param stageSum summed stage (0..4 per face), @param budding budding blocks */
    static float hours(int faces, int stageSum, int budding) {
        if (faces <= 0) return -1;
        double observed = stageSum / (double) faces;
        double q = Math.min(1.0, 0.35 * budding / faces);   // chance a given face was seeded at generation
        if (observed <= expected(q, 0)) return 0f;
        if (observed >= expected(q, MAX_HOURS) - 1e-3) return (float) MAX_HOURS;
        double lo = 0, hi = MAX_HOURS;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (expected(q, mid) < observed) lo = mid; else hi = mid;
        }
        return (float) ((lo + hi) / 2);
    }

    /** Expected mean stage per face after {@code hours} of simulation. */
    static double expected(double q, double hours) {
        double lambda = STEPS_PER_HOUR * hours;
        double[] poisson = new double[5];      // P(N=0..3), and P(N>=4) in slot 4
        double p = Math.exp(-lambda), cumulative = 0;
        for (int k = 0; k < 4; k++) {
            poisson[k] = p;
            cumulative += p;
            p *= lambda / (k + 1);
        }
        poisson[4] = Math.max(0, 1 - cumulative);
        double mean = 0;
        for (int s0 = 0; s0 <= 4; s0++) {
            double w = s0 == 0 ? 1 - q : q / 4;
            for (int n = 0; n <= 4; n++) mean += w * poisson[n] * Math.min(4, s0 + n);
        }
        return mean;
    }
}
