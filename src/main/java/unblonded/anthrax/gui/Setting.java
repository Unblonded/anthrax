package unblonded.anthrax.gui;

import java.util.List;
import java.util.function.*;

/**
 * A setting the GUI can render automatically.
 * Every setting is bound to a getter/setter, so it can wrap fields on your existing modules.
 */
public abstract class Setting {
    public final String name;

    protected Setting(String name) {
        this.name = name;
    }

    // ------------------------------------------------------------------
    public static final class Bool extends Setting {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        public Bool(String name, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(name);
            this.getter = getter;
            this.setter = setter;
        }

        /** Setting that stores its own value. */
        public static Bool standalone(String name, boolean def) {
            boolean[] box = {def};
            return new Bool(name, () -> box[0], v -> box[0] = v);
        }

        public boolean get() { return getter.getAsBoolean(); }
        public void set(boolean v) { setter.accept(v); }
        public void toggle() { set(!get()); }
    }

    // ------------------------------------------------------------------
    public static final class Num extends Setting {
        public final double min, max, step;
        public final int decimals;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;

        public Num(String name, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) {
            super(name);
            this.min = min;
            this.max = max;
            this.step = step;
            this.decimals = step >= 1 ? 0 : (int) Math.ceil(-Math.log10(step));
            this.getter = getter;
            this.setter = setter;
        }

        public static Num standalone(String name, double def, double min, double max, double step) {
            double[] box = {def};
            return new Num(name, min, max, step, () -> box[0], v -> box[0] = v);
        }

        public double get() { return getter.getAsDouble(); }

        public void set(double v) {
            double c = Math.max(min, Math.min(max, v));
            if (step > 0) c = min + Math.round((c - min) / step) * step;
            setter.accept(Math.max(min, Math.min(max, c)));
        }

        public double fraction() { return max == min ? 0 : (get() - min) / (max - min); }

        public void setFraction(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }

        public String display() {
            return decimals <= 0 ? Long.toString(Math.round(get())) : String.format("%." + decimals + "f", get());
        }
    }

    // ------------------------------------------------------------------
    public static final class Mode extends Setting {
        public final List<String> options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        public Mode(String name, List<String> options, Supplier<String> getter, Consumer<String> setter) {
            super(name);
            this.options = options;
            this.getter = getter;
            this.setter = setter;
        }

        public static Mode standalone(String name, String def, String... options) {
            String[] box = {def};
            return new Mode(name, List.of(options), () -> box[0], v -> box[0] = v);
        }

        public String get() { return getter.get(); }

        public void cycle(int dir) {
            int i = Math.floorMod(options.indexOf(get()) + dir, options.size());
            setter.accept(options.get(i));
        }
    }
}
