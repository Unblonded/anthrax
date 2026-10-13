package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** New chat messages slide in instead of the chat jumping up a line (see ChatAnimationMixin). */
public final class ChatAnimationsModule extends FeatureModule {
    public static final ChatAnimationsModule INSTANCE = new ChatAnimationsModule();
    private final Setting.Num duration = setting(Setting.Num.standalone("Duration (ms)", 180, 60, 600, 20), "How long the slide takes.");
    private volatile long lastMessage;

    private ChatAnimationsModule() { super("Chat Animations", "New chat messages slide in instead of the chat jumping up a line", "Visuals", false); }

    public void onMessage() { lastMessage = System.currentTimeMillis(); }

    /** 1 just after a message arrives, easing to 0. */
    public float remaining() {
        if (!isEnabled()) return 0;
        float t = (System.currentTimeMillis() - lastMessage) / (float) (double) duration.get();
        if (t >= 1) return 0;
        float e = 1 - t;
        return e * e * e;
    }
}
