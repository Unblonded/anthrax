package unblonded.anthrax.modules.cosmetic;

import unblonded.anthrax.modules.Setting;

/** Dropped items lie flat on the ground instead of floating and spinning (ItemPhysicsMixin). */
public final class ItemPhysicsModule extends CosmeticModule {
    public static final ItemPhysicsModule INSTANCE = new ItemPhysicsModule();
    private final Setting.Bool flat = setting(Setting.Bool.standalone("Lay flat", true), "Flat items (tools, ingots) lie on their side; blocks sit on the ground.");
    private final Setting.Bool spinInAir = setting(Setting.Bool.standalone("Tumble while falling", true), "Items still turn while they are in the air.");

    private ItemPhysicsModule() { super("Item Physics", "Dropped items lie flat on the ground instead of floating and spinning"); }

    public boolean flat() { return flat.get(); }
    public boolean spinInAir() { return spinInAir.get(); }
}
