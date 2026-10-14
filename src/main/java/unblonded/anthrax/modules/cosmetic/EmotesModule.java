package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import unblonded.anthrax.modules.Setting;

/**
 * Emotes for your own character: press the key and your model waves, dabs, claps and so on. Purely client-side,
 * so only you see it (in third person; the module can switch the camera for you). Applied by PlayerAnimMixin.
 */
public final class EmotesModule extends CosmeticModule {
    public static final EmotesModule INSTANCE = new EmotesModule();
    private final Setting.Mode emote = setting(Setting.Mode.standalone("Emote", "Wave", "Wave", "Dab", "T-Pose", "Clap", "Salute", "Facepalm", "Zombie", "Dance", "Shrug", "Point", "Cheer", "Headbang"),
            "Which emote the key plays.");
    private final Setting.Key key = setting(Setting.Key.standalone("Play key", Setting.Key.NONE, this::play), "Plays the chosen emote (the module must be on).");
    private final Setting.Num duration = setting(Setting.Num.standalone("Duration (s)", 3, 1, 15, 0.5), "How long the emote lasts.");
    private final Setting.Bool stopOnMove = setting(Setting.Bool.standalone("Stop when moving", true), "Walking cancels the emote.");
    private final Setting.Bool camera = setting(Setting.Bool.standalone("Switch to third person", true), "Shows you from the front while emoting, then switches back.");

    private long started = -1;
    private CameraType savedCamera;

    private EmotesModule() { super("Emotes", "Press a key to wave, dab, clap, dance and more. Only you see it, in third person"); }

    private void play() {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null) return;
        if (started >= 0) { stop(mc); return; }   // the key toggles
        started = System.currentTimeMillis();
        if (camera.get() && mc.options.getCameraType().isFirstPerson()) {
            savedCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
    }

    private void stop(Minecraft mc) {
        started = -1;
        if (savedCamera != null) { mc.options.setCameraType(savedCamera); savedCamera = null; }
    }

    @Override public void tick(Minecraft mc) {
        if (started < 0 || mc.player == null) return;
        boolean moving = mc.player.input != null && mc.player.input.getMoveVector().lengthSquared() > 0.01;
        if (System.currentTimeMillis() - started > duration.get() * 1000 || (stopOnMove.get() && moving)) stop(mc);
    }

    @Override protected void onToggle(boolean enabled) { if (!enabled && started >= 0) stop(Minecraft.getInstance()); }

    @Override public String hudInfo() { return started >= 0 ? emote.get() : null; }

    /** Called after the vanilla pose is set up. */
    public void pose(PlayerModel m, AvatarRenderState s) {
        if (!isEnabled() || started < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || s.id != mc.player.getId()) return;
        float t = (System.currentTimeMillis() - started) / 1000f;
        float in = Math.min(1, t * 5);   // ease into the pose
        float pi = Mth.PI;
        switch (emote.get()) {
            case "Wave" -> {
                arm(m.rightArm, -2.7f, 0, 0.35f + 0.4f * Mth.sin(t * 9), in);
            }
            case "Dab" -> {
                arm(m.rightArm, -2.4f, 0, -0.9f, in);
                arm(m.leftArm, -1.6f, 0.9f, 0.3f, in);
                m.head.xRot = lerp(m.head.xRot, 0.6f, in);
                m.head.yRot = lerp(m.head.yRot, 0.7f, in);
            }
            case "T-Pose" -> {
                arm(m.rightArm, 0, 0, pi / 2, in);
                arm(m.leftArm, 0, 0, -pi / 2, in);
            }
            case "Clap" -> {
                float c = 0.25f + 0.25f * Mth.sin(t * 14);
                arm(m.rightArm, -1.3f, -c - 0.2f, 0, in);
                arm(m.leftArm, -1.3f, c + 0.2f, 0, in);
            }
            case "Salute" -> {
                arm(m.rightArm, -2.4f, -0.6f, 0.9f, in);
                m.head.xRot = lerp(m.head.xRot, -0.1f, in);
            }
            case "Facepalm" -> {
                arm(m.rightArm, -2.1f, -0.7f, 0.2f, in);
                m.head.xRot = lerp(m.head.xRot, 0.55f, in);
            }
            case "Zombie" -> {
                arm(m.rightArm, -pi / 2, 0, 0.05f * Mth.sin(t * 3), in);
                arm(m.leftArm, -pi / 2, 0, -0.05f * Mth.sin(t * 3), in);
            }
            case "Dance" -> {
                float b = Mth.sin(t * 8);
                arm(m.rightArm, -1.2f + 0.8f * b, 0, 0.6f, in);
                arm(m.leftArm, -1.2f - 0.8f * b, 0, -0.6f, in);
                m.rightLeg.xRot = lerp(m.rightLeg.xRot, 0.4f * b, in);
                m.leftLeg.xRot = lerp(m.leftLeg.xRot, -0.4f * b, in);
                m.head.zRot = lerp(m.head.zRot, 0.25f * b, in);
                m.body.zRot = lerp(m.body.zRot, 0.08f * b, in);
            }
            case "Shrug" -> {
                float up = 0.5f + 0.5f * Mth.sin(t * 4);
                arm(m.rightArm, -0.4f, 0, 0.9f + 0.2f * up, in);
                arm(m.leftArm, -0.4f, 0, -0.9f - 0.2f * up, in);
                m.head.zRot = lerp(m.head.zRot, 0.2f, in);
            }
            case "Point" -> arm(m.rightArm, -pi / 2 + m.head.xRot, m.head.yRot, 0, in);
            case "Cheer" -> {
                float b = 0.15f * Mth.sin(t * 10);
                arm(m.rightArm, -2.9f + b, 0, 0.3f, in);
                arm(m.leftArm, -2.9f - b, 0, -0.3f, in);
                m.head.xRot = lerp(m.head.xRot, -0.35f, in);
            }
            case "Headbang" -> {
                m.head.xRot = lerp(m.head.xRot, 0.35f + 0.45f * Mth.sin(t * 13), in);
                arm(m.rightArm, -2.6f, 0, 0.4f, in);
            }
            default -> {}
        }
    }

    private static void arm(net.minecraft.client.model.geom.ModelPart arm, float x, float y, float z, float in) {
        arm.xRot = lerp(arm.xRot, x, in);
        arm.yRot = lerp(arm.yRot, y, in);
        arm.zRot = lerp(arm.zRot, z, in);
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
