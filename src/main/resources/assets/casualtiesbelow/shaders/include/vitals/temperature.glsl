// Temperature axis: cold side = frost overlay (vanilla's powder-snow outline texture with a
// growing spatial mask, replacing the flat alpha fade of the vanilla HUD blit); hot side = heat
// haze (a time-wobbled re-sample of the scene, like the consciousness distortion) plus a warm
// edge tint. The two strengths are mutually exclusive by construction — the body cannot be
// below the frost onset and above the heat onset at once.

layout(std140) uniform TemperatureConfig {
    float FrostStrength;
    float HeatStrength;
    float HeatTime;
};

uniform sampler2D FrostSampler;

const float FrostRim = 1.3;          // edge length at which frost sits when it first appears
const float FrostMaskSoftness = 0.25;

// Heat haze: vertical strip wobble (hot air rising reads as horizontal-band distortion). The two
// sine speeds are deliberately non-harmonic so the motion never locks into a diagonal pattern.
const float HeatWobbleFrequency = 40.0;
const float HeatWobbleSpeedA = 0.22;
const float HeatWobbleSpeedB = 0.17;
const float HeatWobbleTexels = 4.5;
const float HeatHazeBlend = 0.9;

// Heat tint: the whole picture yellows as the body overheats — blue suppressed hardest.
const vec3 HeatTint = vec3(1.06, 1.02, 0.72);

vec3 applyFrost(vec3 color, float edge, vec2 uv) {
    float strength = clamp(FrostStrength, 0.0, 1.0);
    if (strength <= 0.0) {
        return color;
    }

    float boundary = FrostRim - strength;
    float mask = smoothstep(boundary, boundary + FrostMaskSoftness, edge);
    vec4 frost = texture(FrostSampler, uv);
    return mix(color, frost.rgb, frost.a * mask * strength);
}

// Runs early, before the color axes: the haze re-samples the raw scene, so anything composited
// before it would be lost in the hazed pixels (same constraint as the consciousness distortion).
vec3 applyHeatHaze(vec3 sceneColor, vec2 coordinates, vec2 texelSize) {
    float strength = clamp(HeatStrength, 0.0, 1.0);
    if (strength <= 0.0) {
        return sceneColor;
    }

    vec2 wobble = vec2(
        sin(coordinates.y * HeatWobbleFrequency + HeatTime * HeatWobbleSpeedA),
        sin(coordinates.x * HeatWobbleFrequency + HeatTime * HeatWobbleSpeedB)
    ) * texelSize * HeatWobbleTexels * strength;
    vec3 hazed = texture(InSampler, clampToTexture(coordinates + wobble, texelSize)).rgb;
    return mix(sceneColor, hazed, strength * HeatHazeBlend);
}

vec3 applyHeatTint(vec3 color) {
    float strength = clamp(HeatStrength, 0.0, 1.0);
    if (strength <= 0.0) {
        return color;
    }
    return color * mix(vec3(1.0), HeatTint, strength);
}
