// Temperature axis (cold side): the frost overlay reuses vanilla's powder-snow outline texture
// but composes it spatially — the mask boundary slides from the screen rim inward as the body
// cools, replacing the flat alpha fade of the vanilla HUD blit with visible growth.

layout(std140) uniform TemperatureConfig {
    float FrostStrength;
};

uniform sampler2D FrostSampler;

const float FrostRim = 1.3;          // edge length at which frost sits when it first appears
const float FrostMaskSoftness = 0.25;

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
