// Grime (dirtiness) axis: brown-toned inward film with static mottling and large blotches.

layout(std140) uniform GrimeConfig {
    float GrimeVignetteStrength;
};

const vec3 GrimeTint = vec3(0.36, 0.27, 0.16);
const float GrimeNoisePixelScale = 0.25;
const float GrimeNoiseDepth = 0.35;
const vec3 GrimeBlotchTint = vec3(0.30, 0.21, 0.11);
const float GrimeBlotchThreshold = 0.58;
const float GrimeBlotchSoftness = 0.30;

vec3 applyGrimeVignette(vec3 color, float vignette, vec2 uv) {
    float strength = clamp(GrimeVignetteStrength, 0.0, 1.0);
    if (strength <= 0.0) {
        return color;
    }

    float noise = staticNoise(floor(gl_FragCoord.xy * GrimeNoisePixelScale));
    float mottle = 1.0 - GrimeNoiseDepth * strength * vignette * noise;
    color = color * mix(vec3(1.0), GrimeTint, strength * vignette) * mottle;

    // Large static blotches smeared across the view: two noise octaves, aspect-corrected,
    // thresholded so only the densest cells read as mud spots. Screen-anchored on purpose —
    // they are the dirt on the player's own eyes, not part of the world.
    vec2 dimensions = vec2(textureSize(InSampler, 0));
    vec2 blotchUv = uv * vec2(dimensions.x / dimensions.y, 1.0);
    float blob = valueNoise(blotchUv * 2.5) * 0.65
        + valueNoise(blotchUv * 6.0 + 17.3) * 0.35;
    float blotch = smoothstep(
        GrimeBlotchThreshold,
        GrimeBlotchThreshold + GrimeBlotchSoftness,
        blob
    ) * strength;
    return color * mix(vec3(1.0), GrimeBlotchTint, clamp(blotch, 0.0, 1.0));
}
