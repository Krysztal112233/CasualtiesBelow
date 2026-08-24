#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform ConsciousnessConfig {
    float Strength;
};

out vec4 fragColor;

const float HazeFraction = 0.35;

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec2 centered = texCoord * 2.0 - 1.0;
    float vignette = smoothstep(0.45, 1.25, length(centered));
    float hazeFactor = 1.0 - Strength * HazeFraction;
    float vignetteFactor = 1.0 - Strength * vignette;

    fragColor = vec4(scene.rgb * hazeFactor * vignetteFactor, scene.a);
}
