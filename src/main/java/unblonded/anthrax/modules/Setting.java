package unblonded.anthrax.modules;

import java.util.List;
import java.util.function.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.math.RoundingMode;

/**
 * A setting the GUI can render automatically.
 * Every setting is bound to a getter/setter, so it can wrap fields on your existing modules.
 */
public abstract class Setting {
    public final String name;
    /** Optional one-line explanation shown when hovering the setting in the menu. */
    public String description;

    protected Setting(String name) {
        this.name = name;
    }

    /** Records the current value as this setting's default. Called once, before saved values load. */
    public void captureDefault() {}
    /** Restores the captured default. */
    public void reset() {}
    public boolean isDefault() { return true; }

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

        private Boolean def;
        @Override public void captureDefault() { if (def == null) def = get(); }
        @Override public void reset() { if (def != null) set(def); }
        @Override public boolean isDefault() { return def == null || def == get(); }
    }

    // ------------------------------------------------------------------
    public static final class Num extends Setting {
        public final double min, max, step;
        public final int decimals;
        private final DecimalFormat displayFormat;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private double displayedValue = Double.NaN;
        private String displayedText;

        public Num(String name, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) {
            super(name);
            this.min = min;
            this.max = max;
            this.step = step;
            this.decimals = step >= 1 ? 0 : (int) Math.ceil(-Math.log10(step));
            if (decimals > 0 && decimals <= 6) {
                String pattern = "0." + "0".repeat(decimals);
                displayFormat = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance());
                displayFormat.setGroupingUsed(false);
                displayFormat.setRoundingMode(RoundingMode.HALF_UP);
            } else {
                displayFormat = null;
            }
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

        /** Moves the value by whole steps (used by scroll/arrow nudging). */
        public void nudge(int steps) { set(get() + steps * (step > 0 ? step : (max - min) / 100.0)); }

        private double def = Double.NaN;
        @Override public void captureDefault() { if (Double.isNaN(def)) def = get(); }
        @Override public void reset() { if (!Double.isNaN(def)) set(def); }
        @Override public boolean isDefault() { return Double.isNaN(def) || Math.abs(def - get()) < 1e-9; }

        public void setFraction(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }

        public String display() {
            double value = get();
            if (displayedText != null && Double.compare(value, displayedValue) == 0) return displayedText;
            displayedValue = value;
            if (decimals <= 0) displayedText = Long.toString(Math.round(value));
            else if (displayFormat == null) displayedText = String.format("%." + decimals + "f", value);
            else displayedText = displayFormat.format(value);
            return displayedText;
        }
    }

    // ------------------------------------------------------------------
    public static final class Mode extends Setting {
        public final List<String> options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        public Mode(String name, List<String> options, Supplier<String> getter, Consumer<String> setter) {
            super(name);
            if (options == null || options.isEmpty()) throw new IllegalArgumentException("A mode must have at least one option");
            this.options = List.copyOf(options);
            this.getter = getter;
            this.setter = setter;
        }

        public static Mode standalone(String name, String def, String... options) {
            List<String> choices = List.of(options);
            if (choices.isEmpty()) throw new IllegalArgumentException("A mode must have at least one option");
            String[] box = {choices.contains(def) ? def : choices.getFirst()};
            return new Mode(name, choices, () -> box[0], v -> box[0] = v);
        }

        public String get() { return getter.get(); }

        public void set(String value) {
            if (options.contains(value)) setter.accept(value);
        }

        public void cycle(int dir) {
            int current = options.indexOf(get());
            if (current < 0) current = 0;
            int i = Math.floorMod(current + dir, options.size());
            setter.accept(options.get(i));
        }

        private String def;
        @Override public void captureDefault() { if (def == null) def = get(); }
        @Override public void reset() { if (def != null) set(def); }
        @Override public boolean isDefault() { return def == null || def.equals(get()); }
    }

    /** Editable short text value, used for search strings, names and commands. */
    public static final class Text extends Setting {
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        public final int maxLength;
        public Text(String name, int maxLength, Supplier<String> getter, Consumer<String> setter) {
            super(name); this.maxLength = maxLength; this.getter = getter; this.setter = setter;
        }
        public static Text standalone(String name, String def, int maxLength) {
            String[] value = {def};
            return new Text(name, maxLength, () -> value[0], next -> value[0] = next);
        }
        public String get() { return getter.get(); }
        public void set(String value) {
            if (value == null) value = "";
            setter.accept(value.substring(0, Math.min(value.length(), maxLength)));
        }

        private String def;
        @Override public void captureDefault() { if (def == null) def = get(); }
        @Override public void reset() { if (def != null) set(def); }
        @Override public boolean isDefault() { return def == null || def.equals(get()); }
    }

    /** One-shot control, rendered like a button in the module settings list. */
    public static final class Action extends Setting {
        private final Runnable action;
        public Action(String name, Runnable action) { super(name); this.action = action; }
        public void run() { action.run(); }
    }
}
