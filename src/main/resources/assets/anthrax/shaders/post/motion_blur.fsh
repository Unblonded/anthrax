#version 330

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

layout(std140) uniform MotionBlurConfig {
    float Strength;
};

out vec4 fragColor;

// Blends this frame with the accumulated previous frames: a short trail on fast turns and movement.
void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 previous = texture(PrevSampler, texCoord);
    fragColor = vec4(mix(current.rgb, previous.rgb, Strength), 1.0);
}
