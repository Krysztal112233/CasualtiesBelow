// Blood loss axis: color drains toward gray as blood volume drops.

layout(std140) uniform BloodLossConfig {
    float DesaturationStrength;
};

vec3 applyBloodLossDesaturation(vec3 color, float strength) {
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(color, vec3(luminance), clamp(strength, 0.0, 1.0));
}
