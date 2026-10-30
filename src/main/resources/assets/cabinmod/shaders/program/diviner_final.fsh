#version 150
// Transient Diviner cinematic, last pass: bloom, the impact (anime ink frames, speed lines, white flash,
// a refracting shockwave ring from the diviner's screen position), radial chromatic split, shake and grain.

// ---- copied from include/diviner_common.glsl: post-effect programs (EffectInstance) do not support
// #moj_import, only core shaders do. Keep the two in sync.
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
// ---- end copy

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;

uniform vec2 OutSize;
uniform float iTime;
uniform float Impact;         // 0..1 shockwave progress, < 0 when idle
uniform float ImpactFrame;    // 0 none, 1 inverted ink, 2 ink
uniform vec2 ImpactScreen;
uniform float Flash;
uniform float Chroma;
uniform vec2 Shake;
uniform float BloomStrength;
uniform float Crack;          // 0..1 the lens cracks from the impact
uniform float Rays;           // 0..1 radial god rays out of the impact point

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float aspect = OutSize.x / OutSize.y;
    vec2 uv = texCoord + Shake;
    vec2 v = (uv - ImpactScreen) * vec2(aspect, 1.0);
    float rv = length(v);
    float ring = 0.0;
    if (Impact >= 0.0) {
        float R = Impact * 1.6;
        ring = exp(-pow((rv - R) / (0.025 + 0.06 * Impact), 2.0)) * (1.0 - Impact);
        uv -= normalize(v + 1e-5) / vec2(aspect, 1.0) * ring * 0.035;
    }

    // the lens cracks: voronoi shards around the impact point, each refracting slightly differently
    float crackLine = 0.0;
    if (Crack > 0.001) {
        vec2 cp = v * 6.0 / (0.6 + rv);
        float edge = dvCracks(cp);
        crackLine = (1.0 - smoothstep(0.0, 0.05, edge)) * smoothstep(1.1, 0.05, rv) * Crack;
        vec2 cell = floor(cp);
        uv += (vec2(dvHash12(cell), dvHash12(cell + 7.7)) - 0.5) * 0.012 * Crack * smoothstep(1.0, 0.1, rv);
    }

    vec2 dir = uv - 0.5;
    float ca = Chroma * 0.006;
    vec3 col = vec3(texture(DiffuseSampler, uv + dir * ca).r, texture(DiffuseSampler, uv).g,
                    texture(DiffuseSampler, uv - dir * ca).b);
    col += texture(BloomSampler, uv).rgb * BloomStrength;
    col += vec3(0.6, 0.9, 1.0) * ring * 0.7;
    if (Rays > 0.001) {
        vec3 acc = vec3(0.0);
        vec2 toward = ImpactScreen - uv;
        for (int i = 0; i < 16; i++) {
            float s = float(i) / 16.0;
            vec3 b = texture(BloomSampler, uv + toward * s * 0.6).rgb;
            acc += b * (1.0 - s);
        }
        float ang = atan(v.y, v.x);
        float streak = 0.6 + 0.4 * dvNoise2(vec2(ang * 30.0, iTime * 0.5));
        col += acc / 8.0 * Rays * streak * vec3(1.0, 0.9, 0.75) * 1.4;
    }
    col = mix(col, vec3(1.0, 0.97, 0.9), crackLine * 0.8);

    if (ImpactFrame > 0.5) {
        float ang = atan(v.y, v.x);
        float lines = step(0.72, dvNoise2(vec2(ang * 48.0, floor(iTime * 30.0)))) * smoothstep(0.08, 0.45, rv);
        float ink = step(0.42 + 0.1 * dvNoise2(uv * 40.0), dvLuma(col));
        ink = max(ink, step(rv, 0.06 + 0.02 * dvNoise2(vec2(ang * 9.0, iTime))));
        float v1 = ImpactFrame < 1.5 ? 1.0 - ink : ink;
        v1 = mix(v1, 1.0 - v1, lines);
        // frame 3: blood-red / gold duotone
        col = ImpactFrame > 2.5 ? mix(vec3(0.55, 0.0, 0.05), vec3(1.0, 0.82, 0.3), v1) : vec3(v1);
    } else {
        col = mix(col, vec3(1.0, 0.98, 0.95), clamp(Flash, 0.0, 1.0));
    }

    float grain = (dvHash12(texCoord * OutSize + fract(iTime) * 100.0) - 0.5) * 0.035;
    vec2 vig = texCoord - 0.5;
    col *= 1.0 - dot(vig, vig) * 0.45;
    fragColor = vec4(col + grain, 1.0);
}
