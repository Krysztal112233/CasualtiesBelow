#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform VitalsConfig {
    float DarknessStrength;
    float BlurStrength;
    float DesaturationStrength;
    float DiscomfortVignetteStrength;
    float ShockStrength;
    float ShockNoiseStrength;
    float ShockTime;
    float GrimeVignetteStrength;
};

out vec4 fragColor;

const float VignetteOpacityMultiplier = 2.0;
const float ZoomDistance = 0.025;
const float DoubleVisionBlend = 0.44;
const vec3 ShockEdgeMultiplier = vec3(0.55, 0.03, 0.01);
const float ShockNoiseFramesPerTick = 0.1;
const float ShockNoisePixelScale = 0.3333333;
const float MaxShockNoiseStrength = 0.5;
const vec3 GrimeTint = vec3(0.36, 0.27, 0.16);
const float GrimeNoisePixelScale = 0.25;
const float GrimeNoiseDepth = 0.35;
const vec3 GrimeBlotchTint = vec3(0.30, 0.21, 0.11);
const float GrimeBlotchThreshold = 0.58;
const float GrimeBlotchSoftness = 0.30;

float staticNoise(vec2 position) {
    vec3 value = fract(vec3(position.xyx) * 0.1031);
    value += dot(value, value.yzx + 33.33);
    return fract((value.x + value.y) * value.z);
}

/** Smooth value noise over the staticNoise hash: interpolating the lattice turns the digital
  * static into organic blobs for the grime blotches.
  */
float valueNoise(vec2 position) {
    vec2 cell = floor(position);
    vec2 fraction = fract(position);
    vec2 blend = fraction * fraction * (3.0 - 2.0 * fraction);
    float a = staticNoise(cell);
    float b = staticNoise(cell + vec2(1.0, 0.0));
    float c = staticNoise(cell + vec2(0.0, 1.0));
    float d = staticNoise(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, blend.x), mix(c, d, blend.x), blend.y);
}

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

    vec2 doubleVisionOffset = texelSize * vec2(6.0, 2.0) * strength;
    vec3 doubleVision = 0.5 * (
        texture(InSampler, clampToTexture(coordinates - doubleVisionOffset, texelSize)).rgb
        + texture(InSampler, clampToTexture(coordinates + doubleVisionOffset, texelSize)).rgb
    );
    return mix(color, doubleVision, DoubleVisionBlend * strength);
}

vec3 applyBloodLossDesaturation(vec3 color, float strength) {
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(color, vec3(luminance), clamp(strength, 0.0, 1.0));
}

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

vec3 applyDiscomfortVignette(vec3 color, float vignette) {
    float strength = clamp(DiscomfortVignetteStrength, 0.0, 1.0);
    return color * (1.0 - strength * vignette);
}

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

vec3 applyDarkness(vec3 color, float vignette) {
    float darkness = clamp(DarknessStrength, 0.0, 1.0);
    float hazeFactor = 1.0 - darkness;
    float vignetteFactor = max(
        0.0,
        1.0 - darkness * VignetteOpacityMultiplier * vignette
    );
    return color * hazeFactor * vignetteFactor;
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec2 centered = texCoord - 0.5;
    vec2 texelSize = 1.0 / vec2(textureSize(InSampler, 0));
    float vignette = smoothstep(0.45, 1.25, length(centered * 2.0));
    // Grime creeps further inward than the other edge effects so it reads as a film over the
    // whole view rather than just darkened corners.
    float grimeVignette = smoothstep(0.30, 1.30, length(centered * 2.0));
    float blur = clamp(BlurStrength, 0.0, 1.0);

    vec3 color = scene.rgb;
    if (blur > 0.0) {
        color = applyConsciousnessDistortion(color, texCoord, centered, texelSize, blur);
    }
    color = applyBloodLossDesaturation(color, DesaturationStrength);
    color = applyPainShock(color, vignette);
    color = applyDiscomfortVignette(color, vignette);
    color = applyGrimeVignette(color, grimeVignette, texCoord);
    color = applyDarkness(color, vignette);

    fragColor = vec4(color, scene.a);
}
