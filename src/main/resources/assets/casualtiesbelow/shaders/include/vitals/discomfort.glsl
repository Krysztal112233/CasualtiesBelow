// Discomfort (nausea) axis: inward-darkening vignette.

layout(std140) uniform DiscomfortConfig {
    float DiscomfortVignetteStrength;
};

vec3 applyDiscomfortVignette(vec3 color, float vignette) {
    float strength = clamp(DiscomfortVignetteStrength, 0.0, 1.0);
    return color * (1.0 - strength * vignette);
}
