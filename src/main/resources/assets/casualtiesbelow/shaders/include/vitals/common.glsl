// Shared noise helpers for the vitals axis includes. post/vitals.fsh imports this file once
// before the per-axis includes; #moj_import only inlines text, so nothing here declares uniforms.

float staticNoise(vec2 position) {
    vec3 value = fract(vec3(position.xyx) * 0.1031);
    value += dot(value, value.yzx + 33.33);
    return fract((value.x + value.y) * value.z);
}

/* Smooth value noise over the staticNoise hash: interpolating the lattice turns the digital
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

// Texture-edge clamp shared by the axes that re-sample InSampler (consciousness distortion,
// heat haze): sampling outside [0,1] would wrap/smear the frame border.
vec2 clampToTexture(vec2 coordinates, vec2 texelSize) {
    return clamp(coordinates, texelSize * 0.5, 1.0 - texelSize * 0.5);
}
