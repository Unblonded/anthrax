package unblonded.anthrax.modules.visual;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.MobCategory;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.client.FriendsModule;

import java.util.Optional;

/**
 * Draws entities in flat colour, visible through walls (see ChamsMixin). The body model is drawn with a copy of
 * the vanilla emissive entity pipeline that has no depth test, on a plain white texture tinted with the chams
 * colour, so it is part of the normal model render and never lags behind.
 */
public final class ChamsModule extends FeatureModule {
    public static final ChamsModule INSTANCE = new ChamsModule();
    private final Setting.Bool players = setting(Setting.Bool.standalone("Players", true), "");
    private final Setting.Bool hostiles = setting(Setting.Bool.standalone("Hostile mobs", true), "");
    private final Setting.Bool passive = setting(Setting.Bool.standalone("Animals and others", false), "");
    private final Setting.Mode colour = setting(Setting.Mode.standalone("Colour", "Red", "Red", "Purple", "Cyan", "Green", "White", "Rainbow"), "Chams colour (friends are always blue).");
    private final Setting.Num opacity = setting(Setting.Num.standalone("Opacity", 0.6, 0.1, 1.0, 0.05), "How solid the colour is.");
    private RenderType type;

    private ChamsModule() { super("Chams", "Draws entities in flat colour, visible through walls. Rendered with the model itself, so there is no delay", "Visuals", false); }

    /** Whether this entity's body should be drawn as chams. */
    public boolean applies(LivingEntityRenderState state) {
        if (!isEnabled() || state.entityType == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (state instanceof AvatarRenderState avatar) {
            if (mc.player != null && avatar.id == mc.player.getId()) return false;
            return players.get();
        }
        MobCategory cat = state.entityType.getCategory();
        if (cat == MobCategory.MONSTER) return hostiles.get();
        return passive.get();
    }

    /** ARGB tint for this entity. */
    public int tint(LivingEntityRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        int rgb = switch (colour.get()) {
            case "Purple" -> 0xB45CFF; case "Cyan" -> 0x40E0FF; case "Green" -> 0x55FF88; case "White" -> 0xFFFFFF;
            case "Rainbow" -> java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000) / 4000f, 0.75f, 1f) & 0xFFFFFF;
            default -> 0xFF4D6D;
        };
        if (state instanceof AvatarRenderState avatar && mc.level != null && FriendsModule.isFriend(mc.level.getEntity(avatar.id)))
            rgb = FriendsModule.COLOR & 0xFFFFFF;
        return ((int) Math.round(opacity.get() * 255) << 24) | rgb;
    }

    /** The see-through flat-colour render type, built on first use (render thread). */
    public RenderType renderType() {
        if (type == null) type = build();
        return type;
    }

    private static RenderType build() {
        RenderPipeline src = RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("anthrax", "pipeline/chams"))
                .withVertexShader(src.getVertexShader())
                .withFragmentShader(src.getFragmentShader())
                .withCull(false)
                .withPolygonMode(src.getPolygonMode())
                .withPrimitiveTopology(src.getPrimitiveTopology())
                .withDepthStencilState(Optional.empty());
        src.getShaderDefines().values().forEach((k, v) -> {
            try { b.withShaderDefine(k, Integer.parseInt(v)); }
            catch (NumberFormatException e) { b.withShaderDefine(k, Float.parseFloat(v)); }
        });
        src.getShaderDefines().flags().forEach(b::withShaderDefine);
        for (var layout : src.getBindGroupLayouts()) b.withBindGroupLayout(layout);
        var targets = src.getColorTargetStates();
        for (int i = 0; i < targets.length; i++) b.withColorTargetState(i, targets[i]);
        var formats = src.getVertexFormatBindings();
        for (int i = 0; i < formats.length; i++) b.withVertexBinding(i, formats[i]);
        RenderPipeline pipeline = b.build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/misc/white.png"))
                .useOverlay()
                .sortOnUpload()
                .createRenderSetup();
        return unblonded.anthrax.mixin.RenderTypeInvoker.anthrax$create("anthrax_chams", setup);
    }
}
