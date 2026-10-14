package unblonded.anthrax.modules.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.gui.ThemeColors;
import unblonded.anthrax.modules.Setting;

/**
 * Swords drawn as knives: karambit, butterfly, bayonet, flip, huntsman or gut knife, in a choice of finishes,
 * with a draw animation when you pull one out, an inspect key and knife-specific tricks. Purely visual and
 * client-side (WeaponHandMixin for first person, WeaponLayerMixin for third person).
 */
public final class WeaponsModule extends CosmeticModule {
    public static final WeaponsModule INSTANCE = new WeaponsModule();
    private static final RenderType TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath("anthrax", "textures/misc/white.png"));

    private final Setting.Mode knife = setting(Setting.Mode.standalone("Knife", "Karambit", "Karambit", "Butterfly", "Bayonet", "Flip", "Huntsman", "Gut"),
            "Which knife your swords turn into.");
    private final Setting.Mode finish = setting(Setting.Mode.standalone("Finish", "Fade", "Steel", "Fade", "Doppler", "Crimson", "Tiger", "Hardened", "Night", "Gold", "Emerald", "Ultraviolet", "Rainbow", "Theme"),
            "The blade's colour pattern.");
    private final Setting.Mode handle = setting(Setting.Mode.standalone("Handle", "Black", "Black", "Wood", "White", "Match finish"), "Handle colour.");
    private final Setting.Key inspectKey = setting(Setting.Key.standalone("Inspect key", GLFW.GLFW_KEY_V, this::inspect), "Plays the inspect animation while you hold a knife.");
    private final Setting.Bool draw = setting(Setting.Bool.standalone("Draw animation", true), "Flip, spin or unfold the knife when you switch to it.");
    private final Setting.Bool slash = setting(Setting.Bool.standalone("Slash animation", true), "A knife-style slash when you attack.");
    private final Setting.Bool tricks = setting(Setting.Bool.standalone("Idle tricks", true), "Now and then, a spin (karambit) or flip (butterfly) while idle.");
    private final Setting.Bool sounds = setting(Setting.Bool.standalone("Sounds", true), "Quiet metal sounds for draws and flips. Only you hear them.");
    private final Setting.Num size = setting(Setting.Num.standalone("Size", 1.0, 0.6, 1.5, 0.05), "Knife size in your hand.");
    private final Setting.Bool thirdPerson = setting(Setting.Bool.standalone("Third person", true), "Show the knife on your character in third person too.");
    private final Setting.Bool everyone = setting(Setting.Bool.standalone("Everyone's swords", false), "Draw every player's and mob's sword as your knife (only on your screen).");

    private long drawStart = -1, inspectStart = -1, trickStart = -1, nextTrick;
    private float slashDir = 1;
    private boolean wasSwinging;
    private String lastHeld = "";
    private int flipSounds;

    private WeaponsModule() { super("Weapons", "Turns swords into knives: karambit, butterfly, bayonet and more, with finishes, draw and inspect animations. Only you see it"); }

    private static boolean sword(ItemStack s) { return !s.isEmpty() && s.is(ItemTags.SWORDS); }

    private float drawSeconds() { return knife.get().equals("Butterfly") ? 0.9f : 0.7f; }
    private float inspectSeconds() { return switch (knife.get()) { case "Butterfly" -> 3.2f; case "Karambit" -> 2.8f; default -> 2.6f; }; }

    private void inspect() {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || !sword(mc.player.getMainHandItem()) || mc.gui.screen() != null) return;
        inspectStart = System.currentTimeMillis();
        trickStart = -1;
        flipSounds = 0;
        if (sounds.get()) Fx.ui(SoundEvents.PLAYER_ATTACK_SWEEP, 1.7f, 0.12f);
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        ItemStack main = p.getMainHandItem();
        String held = p.getInventory().getSelectedSlot() + ":" + main.getItem();
        long now = System.currentTimeMillis();
        if (!held.equals(lastHeld)) {
            lastHeld = held;
            inspectStart = -1;
            trickStart = -1;
            if (sword(main) && draw.get()) {
                drawStart = now;
                flipSounds = 0;
                if (sounds.get()) Fx.ui(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.5f, 0.35f);
            }
            nextTrick = now + 6000 + Fx.RND.nextInt(8000);
        }
        // each new attack slashes the other way, like CS
        if (p.swinging && !wasSwinging) slashDir = -slashDir;
        wasSwinging = p.swinging;
        // idle tricks, only when nothing else is playing and you are not fighting
        boolean busy = drawing(now) || inspecting(now) || p.swinging;
        if (tricks.get() && sword(main) && !busy && trickStart < 0 && now > nextTrick
                && (knife.get().equals("Butterfly") || knife.get().equals("Karambit") || knife.get().equals("Flip"))) {
            trickStart = now;
            flipSounds = 0;
        }
        if (trickStart >= 0 && now - trickStart > 1400) { trickStart = -1; nextTrick = now + 7000 + Fx.RND.nextInt(9000); }
        if (busy) nextTrick = Math.max(nextTrick, now + 4000);
        // butterfly clicks at the moments the handles latch
        if (sounds.get() && knife.get().equals("Butterfly")) {
            float pr = butterflyProgress(now);
            int clicks = (int) Math.floor(pr * 4);
            if (clicks > flipSounds && pr < 1.5f) { flipSounds = clicks; Fx.ui(SoundEvents.TRIPWIRE_CLICK_ON, 1.9f, 0.25f); }
        }
    }

    /** True while a knife is in the main hand and the module draws it, so the vanilla switch dip is skipped. */
    public boolean ownsDraw(LocalPlayer p) { return isEnabled() && draw.get() && p != null && sword(p.getMainHandItem()); }

    private boolean drawing(long now) { return drawStart >= 0 && now - drawStart < drawSeconds() * 1000; }
    private boolean inspecting(long now) { return inspectStart >= 0 && now - inspectStart < inspectSeconds() * 1000; }

    /** How far through its current flip sequence the butterfly is, in half-flips (for the click sounds). */
    private float butterflyProgress(long now) {
        if (drawing(now)) return (now - drawStart) / (drawSeconds() * 1000f) * 2;
        if (inspecting(now)) return (now - inspectStart) / (inspectSeconds() * 1000f) * 4;
        if (trickStart >= 0) return (now - trickStart) / 1400f * 2;
        return 9;
    }

    @Override protected void onToggle(boolean enabled) { lastHeld = ""; drawStart = inspectStart = trickStart = -1; }

    @Override public String hudInfo() { return knife.get(); }

    // ------------------------------------------------------------------ rendering hooks

    /** First person: draws the knife instead of the sword. Returns true when the vanilla item should be skipped. */
    public boolean firstPerson(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, PoseStack pose, SubmitNodeCollector collector, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || entity != mc.player || !sword(stack) || !ctx.firstPerson()) return false;
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean mainHand = stack == mc.player.getMainHandItem();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        KnifeMesh m = new KnifeMesh();
        if (left) m.scaleX(-1);
        // the slash moves the whole hand (the vanilla sword swing is switched off for knives, see ownsSwing)
        if (mainHand && slash.get()) slash(m, mc.player.getAttackAnim(partial));
        if (knife.get().equals("Karambit")) {
            // real karambit grip: index finger through the ring at the top of the fist, handle down through the
            // hand, claw coming out below the little finger and hooking forward; a spin turns it around the finger
            m.translate(-0.16f, 0.6f, -0.2f).rotZ(18).rotX(-34).rotY(-58).scale(0.85f);
        } else {
            // grip in the palm, blade up and forward like the vanilla sword, flat side toward you
            m.translate(-0.09f, 0.25f, -0.06f).rotZ(24).rotX(-22).rotY(-12);
        }
        animate(m, mainHand);
        m.scale((float) (double) size.get() * 1.25f);
        build(m, mainHand);
        submit(collector, pose, m, light);
        return true;
    }

    /** Third person: the knife in a held hand. Returns true when the vanilla item should be skipped. */
    public boolean thirdPerson(ArmedEntityRenderState state, ItemStack stack, HumanoidArm arm, PoseStack pose, SubmitNodeCollector collector, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || !thirdPerson.get() || !sword(stack)) return false;
        boolean self = mc.player != null && state instanceof AvatarRenderState a && a.id == mc.player.getId();
        if (!self && !everyone.get()) return false;
        KnifeMesh m = new KnifeMesh();
        if (arm == HumanoidArm.LEFT) m.scaleX(-1);
        // the layer has already moved to the hand; stand the knife up in the fist with the edge forward
        m.translate(0, 0.02f, 0.02f).rotY(-90);
        boolean main = self && stack == mc.player.getMainHandItem();
        if (main) animateThirdPerson(m);
        m.scale((float) (double) size.get() * 1.15f);
        build(m, main);
        submit(collector, pose, m, light);
        return true;
    }

    private static void submit(SubmitNodeCollector collector, PoseStack pose, KnifeMesh m, int light) {
        float[] v = m.bake();
        collector.submitCustomGeometry(pose, TYPE, (p, buf) -> KnifeMesh.emit(v, p, buf, light));
    }

    // ------------------------------------------------------------------ animation
    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static float easeOut(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (1 - t) * (1 - t) * (1 - t); }

    /** Draw, inspect, slash and idle sway, in hand space around the grip. */
    private void animate(KnifeMesh m, boolean main) {
        long now = System.currentTimeMillis();
        double time = now / 1000.0;
        m.rotZ((float) Math.sin(time * 1.3) * 1.2f).translate(0, (float) Math.sin(time * 2.1) * 0.004f, 0);   // breathing
        if (!main) return;
        String k = knife.get();
        if (drawing(now)) {
            float t = (now - drawStart) / (drawSeconds() * 1000f), e = easeOut(t);
            if (k.equals("Karambit")) m.translate(-0.05f * (1 - e), 0.18f * (float) Math.sin(Math.PI * t), 0);
            else if (k.equals("Butterfly")) m.translate(-0.06f * (1 - e), 0.12f * (1 - e), 0);
            else m.translate(0.04f * (1 - e), -0.14f * (1 - e), 0.04f * (1 - e)).rotX(40 * (1 - e)).rotZ(-25 * (1 - e));
            if (!k.equals("Butterfly") && !k.equals("Flip") && !k.equals("Karambit")) m.rotY(-360 * (1 - e));   // spin up the blade axis
        }
        if (inspecting(now)) {
            float t = (now - inspectStart) / (inspectSeconds() * 1000f);
            float in = ease(t / 0.18f), out = ease((t - 0.82f) / 0.18f), show = in * (1 - out);
            // lift it up and turn the flat toward you, then turn it over halfway through
            if (k.equals("Karambit")) m.translate(-0.1f * show, 0.2f * show, 0.02f * show);
            else m.translate(-0.06f * show, 0.06f * show, 0.08f * show).rotZ(-30 * show).rotX(22 * show);
            // the other knives turn over to show the second side, and keep turning the same way back to the start
            float turn = k.equals("Karambit") || k.equals("Butterfly") ? 0 : 180 * ease((t - 0.38f) / 0.22f) + 180 * out;
            // the karambit keeps its ring facing you, so the spin reads as a turn around the finger
            if (!k.equals("Karambit")) m.rotY(-70 * show - turn);
        }
    }

    /**
     * A CS-style slash in arm space: the hand drives forward and sweeps across the screen while the knife rolls
     * into the cut, alternating direction each attack (right to left, then back). The karambit hooks harder.
     */
    private void slash(KnifeMesh m, float a) {
        if (a <= 0) return;
        // fast out, slower recovery: the cut lands in the first third of the swing
        float out = (float) Math.sin(Math.min(1, a / 0.35f) * Math.PI / 2);
        float back = a < 0.35f ? 1 : 1 - ease((a - 0.35f) / 0.65f);
        float s = out * back;
        float k = ease(Math.min(1, a / 0.45f)) * 2 - 1;              // sweeps from one side to the other
        float d = slashDir;
        boolean hook = knife.get().equals("Karambit");
        m.translate(-0.26f * s + 0.22f * d * k * s, 0.08f * s, -0.32f * s);
        m.rotY(d * 70 * k * s);                                      // across the screen
        m.rotZ(d * (hook ? 70 : 50) * s);                            // roll the edge into the cut
        m.rotX(-(hook ? 65 : 50) * s);                               // tip leads forward
    }

    /** True when the vanilla first-person swing should be skipped because the knife plays its own slash. */
    public boolean ownsSwing(LocalPlayer p) { return isEnabled() && slash.get() && p != null && sword(p.getMainHandItem()); }

    private void animateThirdPerson(KnifeMesh m) {
        long now = System.currentTimeMillis();
        if (drawing(now)) {
            float e = easeOut((now - drawStart) / (drawSeconds() * 1000f));
            m.rotY(-360 * (1 - e));
        }
    }

    /** Builds the chosen knife (with its moving parts posed for any running draw, inspect or trick). */
    private void build(KnifeMesh m, boolean main) {
        long now = System.currentTimeMillis();
        KnifeModels.Look look = look();
        switch (knife.get()) {
            case "Butterfly" -> {
                float p = 1;   // 0 closed, 1 open
                if (main && drawing(now)) p = ease((now - drawStart) / (drawSeconds() * 1000f));
                else if (main && inspecting(now)) {
                    float t = (now - inspectStart) / (inspectSeconds() * 1000f);
                    p = (float) Math.abs(Math.cos(t * Math.PI * 2));   // closes and opens twice
                } else if (main && trickStart >= 0) {
                    float t = (now - trickStart) / 1400f;
                    p = (float) Math.abs(Math.cos(t * Math.PI));
                }
                float[] a = butterflyAngles(p);
                KnifeModels.butterfly(m, look, a[0], a[1]);
            }
            case "Bayonet" -> KnifeModels.bayonet(m, look);
            case "Flip" -> {
                float fold = 0;
                if (main && drawing(now)) fold = 180 * (1 - easeOut((now - drawStart) / (drawSeconds() * 1000f)));
                else if (main && inspecting(now)) {
                    float t = (now - inspectStart) / (inspectSeconds() * 1000f);
                    fold = t > 0.55f && t < 0.8f ? 180 * (float) Math.sin((t - 0.55f) / 0.25f * Math.PI) : 0;
                } else if (main && trickStart >= 0) fold = 180 * (float) Math.sin(Math.min(1, (now - trickStart) / 1400f) * Math.PI);
                KnifeModels.flip(m, look, fold);
            }
            case "Huntsman" -> KnifeModels.huntsman(m, look);
            case "Gut" -> KnifeModels.gut(m, look);
            default -> {
                // karambit: spins around the finger ring (the model's origin), lifted a little mid-spin so the
                // ring and the sweeping blade stay in view
                float spin = 0, lift = 0;
                if (main && drawing(now)) {
                    float t = (now - drawStart) / (drawSeconds() * 1000f);
                    spin = 360 * easeOut(t);
                    lift = (float) Math.sin(Math.PI * Math.min(1, t));
                } else if (main && inspecting(now)) {
                    float t = ((now - inspectStart) / (inspectSeconds() * 1000f) - 0.2f) / 0.55f;
                    spin = 720 * ease(t);
                    lift = t > 0 && t < 1 ? (float) Math.sin(Math.PI * t) : 0;
                } else if (main && trickStart >= 0) {
                    float t = (now - trickStart) / 1100f;
                    spin = 360 * ease(t);
                    lift = t < 1 ? (float) Math.sin(Math.PI * t) : 0;
                }
                // the ring sits in the lower palm, high enough to stay on screen while the knife turns around it
                // the ring is the pivot: everything turns around its centre
                m.translate(0, 0.04f * lift, 0).rotZ(-spin);
                m.rotZ(180);    // hang the knife from the ring: handle down, claw below the fist
                KnifeModels.karambit(m, look);
            }
        }
    }

    /** Balisong pose for an opening fraction: the blade and free handle swing up together, then the handle comes back down. */
    private static float[] butterflyAngles(float p) {
        // 1) the bite handle swings out around its pin while the blade stays tucked in the safe handle
        if (p < 0.5f) return new float[]{180, 180 * ease(p * 2)};
        // 2) blade and bite handle turn over together around the safe pin: the blade comes up and the
        //    bite handle lands back beside the safe handle, closing the grip
        float q = ease((p - 0.5f) * 2);
        return new float[]{180 * (1 - q), 180 + 180 * q};
    }

    // ------------------------------------------------------------------ finishes
    private KnifeModels.Look look() {
        double time = System.currentTimeMillis() / 1000.0;
        String f = finish.get();
        KnifeModels.Finish fin = switch (f) {
            case "Steel" -> (u, v) -> mix(0x7E858C, 0xE4E8EC, 0.5f + 0.35f * v - 0.1f * u);
            case "Fade" -> (u, v) -> {
                float x = Math.max(0, Math.min(1, u * 1.1f + v * 0.08f));
                return x < 0.45f ? mix(0xF4D03F, 0xEC5A9C, x / 0.45f) : mix(0xEC5A9C, 0x6B3FD8, (x - 0.45f) / 0.55f);
            };
            case "Doppler" -> (u, v) -> {
                float n = noise(u * 7, v * 2.5f);
                return n < 0.4f ? mix(0x0D0618, 0x3A1C6E, n / 0.4f) : mix(0x3A1C6E, 0x3D8BFF, (n - 0.4f) / 0.6f);
            };
            case "Emerald" -> (u, v) -> {
                float n = noise(u * 7 + 3, v * 2.5f);
                return n < 0.45f ? mix(0x02140A, 0x0E7A3A, n / 0.45f) : mix(0x0E7A3A, 0x5CFFA0, (n - 0.45f) / 0.55f);
            };
            case "Crimson" -> (u, v) -> {
                boolean web = Math.abs(Math.sin(u * 26 + v * 3)) < 0.12 || Math.abs(Math.sin(u * 9 - v * 7)) < 0.1;
                return web ? 0x1A0306 : mix(0x8E0C1C, 0xC4182C, 0.5f + 0.4f * v);
            };
            case "Tiger" -> (u, v) -> Math.sin(u * 15 + v * 2.2) > 0.35 ? 0x1C1206 : mix(0xE08A1E, 0xF8C657, 0.5f + 0.4f * v);
            case "Hardened" -> (u, v) -> {
                float n = noise(u * 5 + 11, v * 3);
                return n < 0.35f ? mix(0x1F3F94, 0x3E67C9, n / 0.35f) : n < 0.7f ? mix(0x8E949B, 0xC9CED3, (n - 0.35f) / 0.35f) : mix(0xB88A24, 0xE8C25A, (n - 0.7f) / 0.3f);
            };
            case "Night" -> (u, v) -> mix(0x1D2026, 0x4A5059, 0.5f + 0.4f * v);
            case "Gold" -> (u, v) -> mix(0xB8862A, 0xFFE38A, 0.5f + 0.4f * v - 0.1f * u);
            case "Ultraviolet" -> (u, v) -> mix(0x1E0F33, 0x6A33A8, 0.35f + 0.35f * v + 0.2f * u);
            case "Rainbow" -> (u, v) -> hsl((float) (u * 260 + time * 70), 0.85f, 0.58f + 0.08f * v);
            case "Theme" -> (u, v) -> mix(ThemeColors.accent() & 0xFFFFFF, ThemeColors.accent2() & 0xFFFFFF, u);
            default -> (u, v) -> 0xC0C6CC;
        };
        int metal = f.equals("Gold") ? 0xD9B45A : 0xA9B0B8;
        int h, hd;
        switch (handle.get()) {
            case "Wood" -> { h = 0x7A4A26; hd = 0x55321A; }
            case "White" -> { h = 0xE6E6E6; hd = 0xB8B8B8; }
            case "Match finish" -> { h = KnifeModels.darken(fin.blade(0.5f, 0), 0.25f); hd = KnifeModels.darken(fin.blade(0.5f, 0), 0.5f); }
            default -> { h = 0x26282C; hd = 0x141518; }
        }
        return new KnifeModels.Look(fin, h, hd, metal);
    }

    /** Smooth 2D value noise in 0..1, cheap and stable per knife. */
    private static float noise(float x, float y) {
        double n = Math.sin(x * 1.7 + Math.sin(y * 2.3 + x * 0.7) * 1.9) * 0.5 + Math.sin(y * 3.1 - x * 1.3 + 1.2) * 0.3 + Math.sin(x * 4.7 + y * 0.9) * 0.2;
        return (float) Math.max(0, Math.min(1, n * 0.5 + 0.5));
    }
}
