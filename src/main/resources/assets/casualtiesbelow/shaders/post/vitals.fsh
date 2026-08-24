#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform VitalsConfig {
    float VignetteStrength;
    float HazeStrength;
    float BlurStrength;
};

out vec4 fragColor;

const float HazeFraction = 0.35;
const float VignetteOpacityMultiplier = 2.0;
const float ZoomDistance = 0.025;
const float DoubleVisionBlend = 0.44;

vec2 clampToTexture(vec2 coordinates, vec2 texelSize) {
    return clamp(coordinates, texelSize * 0.5, 1.0 - texelSize * 0.5);
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec2 centered = texCoord - 0.5;
    vec2 texelSize = 1.0 / vec2(textureSize(InSampler, 0));
    vec2 zoomOffset = centered * ZoomDistance * BlurStrength;

    vec3 color = scene.rgb * 0.28;
    color += texture(InSampler, texCoord - zoomOffset * 0.25).rgb * 0.24;
    color += texture(InSampler, texCoord - zoomOffset * 0.50).rgb * 0.20;
    color += texture(InSampler, texCoord - zoomOffset * 0.75).rgb * 0.16;
    color += texture(InSampler, texCoord - zoomOffset).rgb * 0.12;

    vec2 doubleVisionOffset = texelSize * vec2(6.0, 2.0) * BlurStrength;
    vec3 doubleVision = 0.5 * (
        texture(InSampler, clampToTexture(texCoord - doubleVisionOffset, texelSize)).rgb
        + texture(InSampler, clampToTexture(texCoord + doubleVisionOffset, texelSize)).rgb
    );
    color = mix(color, doubleVision, DoubleVisionBlend * BlurStrength);

    float vignette = smoothstep(0.45, 1.25, length(centered * 2.0));
    float hazeFactor = 1.0 - HazeStrength * HazeFraction;
    float vignetteFactor = max(
        0.0,
        1.0 - VignetteStrength * VignetteOpacityMultiplier * vignette
    );

    fragColor = vec4(color * hazeFactor * vignetteFactor, scene.a);
}
