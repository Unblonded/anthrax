#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GradeConfig {
    float Exposure;
    float Contrast;
    float Saturation;
    float Warmth;
    float Vignette;
    float Bloom;
};

in vec2 texCoord;

out vec4 fragColor;

// Realistic World colour grade: a soft glow around bright areas, exposure, white balance, contrast, saturation
// and a vignette, finished with a gentle filmic shoulder so highlights roll off instead of clipping.
void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec2 px = 1.0 / InSize;
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235988;
        vec2 d = vec2(cos(a), sin(a));
        glow += max(texture(InSampler, texCoord + d * px * 4.0).rgb - 0.62, 0.0);
        glow += max(texture(InSampler, texCoord + d * px * 10.0).rgb - 0.62, 0.0);
    }
    c += glow / 24.0 * Bloom * 2.2;
    c *= Exposure;
    c *= vec3(1.0 + Warmth * 0.08, 1.0 + Warmth * 0.01, 1.0 - Warmth * 0.09);
    c = (c - 0.5) * Contrast + 0.5;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, Saturation);
    c = max(c, 0.0);
    c = c * (1.0 + c / 6.0) / (1.0 + c * 0.16);
    float v = distance(texCoord, vec2(0.5));
    c *= 1.0 - Vignette * smoothstep(0.32, 0.86, v);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
