// Pain shock axis: red pulsing edge vignette with animated film grain.

layout(std140) uniform ShockConfig {
    float ShockStrength;
    float ShockNoiseStrength;
    float ShockTime;
};

const vec3 ShockEdgeMultiplier = vec3(0.55, 0.03, 0.01);
const float ShockNoiseFramesPerTick = 0.1;
const float ShockNoisePixelScale = 0.3333333;
const float MaxShockNoiseStrength = 0.5;

vec3 applyPainShock(vec3 color, float vignette) {
    float shock = clamp(ShockStrength, 0.0, 1.0) * vignette;
    color *= mix(vec3(1.0), ShockEdgeMultiplier, shock);

    float noiseAmount = clamp(ShockNoiseStrength, 0.0, MaxShockNoiseStrength) * shock;
    if (noiseAmount > 0.0) {
        float noiseFrame = floor(ShockTime * ShockNoiseFramesPerTick);
        vec2 noiseCell = floor(gl_FragCoord.xy * ShockNoisePixelScale);
        float noise = staticNoise(noiseCell + vec2(noiseFrame * 17.0, noiseFrame * 29.0));
        float grain = (noise * 2.0 - 1.0) * 0.6;
        float brightFlake = smoothstep(0.92, 1.0, noise) * 0.8;
        color = clamp(color + vec3((grain + brightFlake) * noiseAmount), 0.0, 1.0);
    }
    return color;
}
