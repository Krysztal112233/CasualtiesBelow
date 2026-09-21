#version 330

// One fullscreen pass composing every vitals axis. Each axis owns a named std140 uniform block
// and lives in its own include under shaders/include/vitals/; #moj_import inlines them in order,
// so the import list above and main() below are the only cross-axis touch points. Adding an axis
// means one new include plus one new uniform block in post_effect/vitals.json — existing axes
// stay untouched.
// GLSL is declare-before-use: shared declarations and the pass sampler must precede the imports
// because the axis includes reference InSampler.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

#moj_import <casualtiesbelow:vitals/common.glsl>
#moj_import <casualtiesbelow:vitals/consciousness.glsl>
#moj_import <casualtiesbelow:vitals/bloodloss.glsl>
#moj_import <casualtiesbelow:vitals/discomfort.glsl>
#moj_import <casualtiesbelow:vitals/shock.glsl>
#moj_import <casualtiesbelow:vitals/grime.glsl>
#moj_import <casualtiesbelow:vitals/temperature.glsl>

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
    // Heat haze re-samples the raw scene, so it must run before the color axes (same constraint
    // as the consciousness distortion above).
    if (HeatStrength > 0.0) {
        color = applyHeatHaze(color, texCoord, texelSize);
    }
    float edge = length(centered * 2.0);
    color = applyBloodLossDesaturation(color, DesaturationStrength);
    color = applyPainShock(color, vignette);
    color = applyDiscomfortVignette(color, vignette);
    color = applyGrimeVignette(color, grimeVignette, texCoord);
    color = applyHeatTint(color);
    color = applyFrost(color, edge, texCoord);
    color = applyDarkness(color, vignette);

    fragColor = vec4(color, scene.a);
}
