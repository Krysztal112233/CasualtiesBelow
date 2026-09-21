// Consciousness axis: full-screen darkening toward blackout plus zoom-ghost and double-vision
// distortion. Block members are written by VitalsPostEffect in declaration order.

layout(std140) uniform ConsciousnessConfig {
    float DarknessStrength;
    float BlurStrength;
};

const float VignetteOpacityMultiplier = 2.0;
const float ZoomDistance = 0.06;
const float DoubleVisionBlend = 0.44;

vec2 clampToTexture(vec2 coordinates, vec2 texelSize) {
    return clamp(coordinates, texelSize * 0.5, 1.0 - texelSize * 0.5);
}

vec3 applyConsciousnessDistortion(
    vec3 sceneColor,
    vec2 coordinates,
    vec2 centered,
    vec2 texelSize,
    float strength
) {
    vec2 zoomOffset = centered * ZoomDistance * strength;
    vec3 color = sceneColor * 0.28;
    color += texture(InSampler, coordinates - zoomOffset * 0.25).rgb * 0.24;
    color += texture(InSampler, coordinates - zoomOffset * 0.50).rgb * 0.20;
    color += texture(InSampler, coordinates - zoomOffset * 0.75).rgb * 0.16;
    color += texture(InSampler, coordinates - zoomOffset).rgb * 0.12;

    vec2 doubleVisionOffset = texelSize * vec2(14.0, 5.0) * strength;
    vec3 doubleVision = 0.5 * (
        texture(InSampler, clampToTexture(coordinates - doubleVisionOffset, texelSize)).rgb
        + texture(InSampler, clampToTexture(coordinates + doubleVisionOffset, texelSize)).rgb
    );
    return mix(color, doubleVision, DoubleVisionBlend * strength);
}

vec3 applyDarkness(vec3 color, float vignette) {
    float darkness = clamp(DarknessStrength, 0.0, 1.0);
    float hazeFactor = 1.0 - darkness;
    float vignetteFactor = max(
        0.0,
        1.0 - darkness * VignetteOpacityMultiplier * vignette
    );
    return color * hazeFactor * vignetteFactor;
}
