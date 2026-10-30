// Shared helpers for the Transient Diviner shaders (core + post). Hash/noise are value-noise style,
// cheap enough for full-screen use.

float dvHash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float dvHash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float dvHash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 dvHash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float dvNoise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(dvHash12(i), dvHash12(i + vec2(1, 0)), u.x),
               mix(dvHash12(i + vec2(0, 1)), dvHash12(i + vec2(1, 1)), u.x), u.y);
}

float dvNoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = dvHash13(i), b = dvHash13(i + vec3(1, 0, 0));
    float c = dvHash13(i + vec3(0, 1, 0)), d = dvHash13(i + vec3(1, 1, 0));
    float e = dvHash13(i + vec3(0, 0, 1)), f1 = dvHash13(i + vec3(1, 0, 1));
    float g = dvHash13(i + vec3(0, 1, 1)), h = dvHash13(i + vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, f1, u.x), mix(g, h, u.x), u.y), u.z);
}

float dvFbm2(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * dvNoise2(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return s;
}

float dvFbm3(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * dvNoise3(p);
        p = p * 2.02 + vec3(17.1, 9.2, 4.7);
        a *= 0.5;
    }
    return s;
}

// Voronoi edge distance (F2 - F1) for glowing cracks.
float dvCracks(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float f1 = 8.0, f2 = 8.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 g = vec2(x, y);
        vec2 o = vec2(dvHash12(i + g), dvHash12(i + g + 19.7));
        float d = length(g + o - f);
        if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) { f2 = d; }
    }
    return f2 - f1;
}

vec3 dvTonemap(vec3 c) {
    return vec3(1.0) - exp(-c);
}

float dvLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}
