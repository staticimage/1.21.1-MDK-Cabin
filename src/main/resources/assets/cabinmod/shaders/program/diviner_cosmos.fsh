#version 150
// Transient Diviner cinematic, pass 1: the world (DiffuseSampler) with altitude haze and warp streaks,
// cross-faded (Takeover) into deep space: procedural stars, the Milky Way, nebula, constellations that draw
// themselves in (Reveal), and the Blender planet rendered by SpaceScene into PlanetSampler.
// Uniforms are set every frame by client/diviner/DivinerCinematic.java.

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
uniform sampler2D PlanetSampler;
uniform sampler2D ApparitionSampler;   // the diviner's GUI texture (its Cabin-in-Agartha art)

uniform vec2 OutSize;
uniform mat4 InvViewProj;     // inverse(projection * view rotation) of the space camera
uniform mat4 ConstFrame;      // right, up, forward of the camera when the panel appears
uniform float iTime;
uniform float Takeover;
uniform float Haze;
uniform float Warp;
uniform float Reveal;
uniform vec3 SunDir;
uniform vec3 CometScreen;     // uv + visibility
uniform vec2 CometTail;       // screen direction the tail streams toward
uniform float CometHeat;
uniform float Fade;
uniform vec4 ArtRect;         // uv rect of the art inside ApparitionSampler (u0, v0, u1, v1)
uniform float Apparition;     // 0..1 the divine image behind the panel
uniform float LaunchBeam;     // 0..1 a result just shot up out of the panel
uniform vec3 CometCore;       // the falling star's colours
uniform vec3 CometTrail;
uniform vec3 BeamColor;       // colour of the launched result's star

in vec2 texCoord;
out vec4 fragColor;

vec3 viewDir(vec2 uv) {
    vec4 a = InvViewProj * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
    vec4 b = InvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    return normalize(b.xyz / b.w - a.xyz / a.w);
}

vec3 starLayer(vec3 d, float scale, float density, float size, float bright) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    float h = dvHash13(cell);
    if (h > density) return vec3(0.0);
    vec3 sp = normalize(cell + 0.5 + (dvHash33(cell) - 0.5) * 0.7);
    float dist = length(d - sp) * scale;
    float b = exp(-dist * dist / size);
    float tw = 0.65 + 0.35 * sin(iTime * (1.5 + h * 9.0) + h * 70.0);
    float temp = dvHash13(cell + 7.3);
    vec3 tint = temp < 0.3 ? vec3(1.0, 0.75, 0.55) : (temp > 0.75 ? vec3(0.65, 0.78, 1.0) : vec3(1.0, 0.97, 0.92));
    return tint * b * tw * bright * (0.4 + 0.6 * h / density);
}

vec3 sky(vec3 d) {
    vec3 col = vec3(0.004, 0.005, 0.012);
    vec3 bandN = normalize(vec3(0.35, 0.55, 0.76));
    float band = exp(-pow(dot(d, bandN), 2.0) * 14.0);
    float dust = dvFbm3(d * 5.0);
    float lanes = smoothstep(0.45, 0.75, dvFbm3(d * 9.0 + 3.0));
    col += mix(vec3(0.08, 0.05, 0.16), vec3(0.22, 0.16, 0.12), dust) * band * dust * 0.9 * (1.0 - 0.6 * lanes);
    float neb = dvFbm3(d * 2.2 + vec3(0.0, iTime * 0.004, 0.0));
    col += vec3(0.25, 0.05, 0.35) * pow(neb, 3.0) * 0.35 + vec3(0.02, 0.18, 0.28) * pow(dvFbm3(d * 3.1 - 5.0), 3.0) * 0.3;
    col += starLayer(d, 70.0, 0.22, 0.05, 2.2);
    col += starLayer(d, 160.0, 0.16, 0.05, 1.0);
    col += starLayer(d, 330.0, 0.06 + band * 0.4, 0.06, 0.7);
    float sd = max(dot(d, normalize(SunDir)), 0.0);
    col += vec3(1.0, 0.92, 0.8) * (pow(sd, 4000.0) * 30.0 + pow(sd, 60.0) * 0.25);
    return col;
}

// six constellations of six stars, laid out around where the camera looks when the panel appears
vec3 constellationStar(int k, int i) {
    float fk = float(k), fi = float(i);
    vec2 centre = vec2(mod(fk, 3.0) - 1.0, fk < 3.0 ? 0.32 : -0.28) * vec2(0.62, 1.0)
            + (vec2(dvHash11(fk * 3.1), dvHash11(fk * 7.7)) - 0.5) * 0.16;
    vec2 off = vec2(0.0);
    for (int j = 1; j <= 5; j++) {
        if (j > i) break;
        float a = dvHash11(fk * 13.0 + float(j) * 1.7) * 6.2831;
        off += vec2(cos(a), sin(a)) * (0.06 + 0.05 * dvHash11(fk * 5.0 + float(j)));
    }
    vec2 s = centre + off;
    return normalize(ConstFrame[0].xyz * s.x + ConstFrame[1].xyz * s.y + ConstFrame[2].xyz);
}

float segDist(vec3 p, vec3 a, vec3 b, out float t) {
    vec3 ab = b - a;
    t = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
    return length(p - (a + ab * t));
}

vec3 constellations(vec3 d) {
    if (Reveal <= 0.0) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int k = 0; k < 6; k++) {
        float start = float(k) * 0.08;
        float prog = clamp((Reveal - start) / 0.5, 0.0, 1.0) * 5.0;
        vec3 prev = constellationStar(k, 0);
        for (int i = 0; i < 6; i++) {
            vec3 s = constellationStar(k, i);
            float lit = clamp(prog - float(i) + 1.0, 0.0, 1.0);
            float ds = length(d - s);
            col += vec3(0.75, 0.9, 1.0) * (exp(-ds * ds * 60000.0) * 3.0 + exp(-ds * 160.0) * 0.25) * lit;
            if (i > 0) {
                float t;
                float dl = segDist(d, prev, s, t);
                float drawn = step(t, clamp(prog - float(i - 1), 0.0, 1.0));
                col += vec3(0.4, 0.8, 1.0) * exp(-dl * 1400.0) * 0.55 * drawn;
            }
            prev = s;
        }
    }
    return col;
}

// The Cabin-in-Agartha art from the diviner's GUI, hung in the sky behind the glass panel like a divine
// apparition: torn, glowing edges, a heat shimmer, a golden double halo, god rays and a slow-turning sigil ring.
vec3 apparition(vec3 d, out float cover) {
    cover = 0.0;
    if (Apparition <= 0.001) return vec3(0.0);
    vec3 R = ConstFrame[0].xyz, U = ConstFrame[1].xyz, F = ConstFrame[2].xyz;
    float z = dot(d, F);
    if (z <= 0.05) return vec3(0.0);
    vec2 p = vec2(dot(d, R), dot(d, U)) / z;
    float rise = (1.0 - Apparition) * 0.12;
    vec2 c = vec2(0.0, 0.1 - rise + 0.012 * sin(iTime * 0.7));
    vec2 hsz = vec2(0.56, 0.61) * (0.9 + 0.1 * Apparition);
    vec2 q = (p - c) / hsz;
    vec3 col = vec3(0.0);

    // god rays and halo centred on the figure's head
    vec2 head = vec2(-0.04, 0.42);
    vec2 h = (q - head) * vec2(hsz.x / hsz.y, 1.0);
    float hr = length(h), ha = atan(h.y, h.x);
    float rays = pow(dvNoise2(vec2(ha * 7.0, iTime * 0.15)), 3.0) + 0.6 * pow(dvNoise2(vec2(ha * 19.0, -iTime * 0.22)), 5.0);
    col += vec3(1.0, 0.82, 0.45) * rays * exp(-hr * 0.9) * 0.55;
    float ring1 = exp(-pow((hr - 0.34) * 38.0, 2.0)), ring2 = exp(-pow((hr - 0.41) * 60.0, 2.0));
    float dash = step(0.35, fract(ha / 6.2831 * 48.0 + iTime * 0.05));
    col += vec3(1.0, 0.88, 0.55) * (ring1 * 1.2 + ring2 * dash * 0.9);
    // great sigil ring around the whole image
    vec2 g = q * vec2(hsz.x / hsz.y, 1.0);
    float gr = length(g), ga = atan(g.y, g.x);
    float ticks = step(0.8, fract(ga / 6.2831 * 72.0 - iTime * 0.02)) * exp(-pow((gr - 1.22) * 30.0, 2.0));
    float band = exp(-pow((gr - 1.3) * 70.0, 2.0)) + exp(-pow((gr - 1.15) * 90.0, 2.0)) * 0.6;
    float runes = step(0.55, dvNoise2(vec2(floor(ga / 6.2831 * 36.0 + iTime * 0.3), 3.0))) * exp(-pow((gr - 1.38) * 40.0, 2.0));
    col += vec3(0.75, 0.85, 1.0) * (ticks * 0.8 + band * 0.7 + runes * 0.6);

    // the image itself: torn feathered edges, shimmer, holy grade
    vec2 w = q + 0.012 * vec2(dvNoise2(q * 7.0 + iTime * 0.6) - 0.5, dvNoise2(q * 7.0 - iTime * 0.5) - 0.5);
    float edgeN = (dvFbm2(q * 4.0 + 1.7) - 0.5) * 0.35;
    float m = 1.0 - smoothstep(0.78, 1.0, max(abs(w.x), abs(w.y)) + edgeN);
    if (m > 0.0) {
        vec2 uv = vec2(mix(ArtRect.x, ArtRect.z, w.x * 0.5 + 0.5), mix(ArtRect.w, ArtRect.y, w.y * 0.5 + 0.5));
        vec3 art = texture(ApparitionSampler, uv).rgb;
        float l = dvLuma(art);
        art = mix(art, art * vec3(1.06, 1.0, 0.9) + vec3(0.03, 0.025, 0.01), 0.4);
        art += vec3(1.0, 0.9, 0.7) * pow(l, 4.0) * 0.2;
        float rim = smoothstep(0.55, 0.0, m) * m * 4.0;
        col = mix(col, art * 0.85, m * 0.92);
        col += vec3(1.0, 0.85, 0.5) * rim * 0.9;
        cover = m * 0.92;
    }
    // drifting motes of light
    vec2 cell = floor(q * 14.0);
    float mh = dvHash12(cell);
    vec2 mp = fract(q * 14.0) - 0.5 - (vec2(dvHash12(cell + 3.1), fract(mh * 7.0 + iTime * 0.08)) - 0.5) * 0.8;
    col += vec3(1.0, 0.9, 0.7) * step(0.86, mh) * exp(-dot(mp, mp) * 300.0) * (0.5 + 0.5 * sin(iTime * 3.0 + mh * 40.0));

    float appear = Apparition * (1.0 + 2.5 * exp(-pow((Apparition - 0.35) * 6.0, 2.0)));   // flares as it manifests
    cover *= Apparition;
    return col * appear;
}

void main() {
    vec2 uv = texCoord;
    float aspect = OutSize.x / OutSize.y;
    vec2 c = (uv - 0.5) * vec2(aspect, 1.0);
    float r = length(c);
    float ang = atan(c.y, c.x);

    vec3 world = texture(DiffuseSampler, uv).rgb;
    vec3 hazeCol = mix(vec3(0.62, 0.78, 1.0), vec3(0.95, 0.97, 1.0), smoothstep(0.2, 0.8, r));
    world = mix(world, hazeCol, Haze * (0.22 + 0.38 * smoothstep(0.0, 0.8, r)));

    vec3 col = world;
    if (Takeover > 0.001) {
        vec3 d = viewDir(uv);
        vec4 planet = texture(PlanetSampler, uv);
        float cover;
        vec3 holy = apparition(d, cover);
        vec3 back = (sky(d) + constellations(d)) * (1.0 - cover) + holy;
        vec3 space = back * (1.0 - planet.a) + planet.rgb;
        col = mix(world, space, Takeover);
    }

    // speed streaks rushing past during the climb and the fall
    if (Warp > 0.001) {
        float lane = floor(ang * 70.0);
        float seed = dvHash11(lane);
        float flow = fract(r * (1.2 + seed) - iTime * (1.5 + seed * 2.0) * Warp - seed);
        float streak = smoothstep(0.85, 1.0, seed) * smoothstep(0.0, 0.25, flow) * smoothstep(0.6, 0.25, flow);
        float edge = smoothstep(0.15, 0.75, r);
        col += vec3(0.7, 0.85, 1.0) * streak * edge * Warp * 0.9;
        col = mix(col, col * vec3(0.9, 0.95, 1.1), Warp * 0.3);
    }

    // the shooting star: hot core, halo, tail and an anamorphic lens streak
    if (CometScreen.z > 0.001) {
        vec2 v = (uv - CometScreen.xy) * vec2(aspect, 1.0);
        float dist = length(v);
        vec2 tail = normalize(CometTail + vec2(1e-5));
        float along = dot(v, tail);
        float perp = abs(dot(v, vec2(-tail.y, tail.x)));
        float ahead = max(along, 0.0);   // clamp first: a negative spread overflows exp() and 0 * inf = NaN (black)
        float tailGlow = step(0.0, along) * exp(-perp * perp * 900.0 / (0.2 + ahead * 6.0)) * exp(-ahead * 3.0);
        vec3 glow = vec3(1.0, 0.98, 0.95) * exp(-dist * 70.0) * 2.5
                + CometCore * exp(-dist * 9.0) * 0.55
                + mix(CometCore, CometTrail, clamp(along * 2.0, 0.0, 1.0)) * tailGlow * 1.3
                + mix(CometCore, vec3(1.0), 0.3) * exp(-abs(v.y) * 260.0) * exp(-abs(v.x) * 2.5) * 0.7;
        col += glow * CometScreen.z;
    }

    // atmospheric entry: plasma licking in from the screen edges
    if (CometHeat > 0.001) {
        float flames = dvFbm2(vec2(ang * 3.0, r * 5.0 - iTime * 9.0));
        float edge = smoothstep(0.5, 1.0, r + (flames - 0.5) * 0.3);
        vec3 plasma = mix(vec3(1.0, 0.38, 0.1), vec3(1.0, 0.25, 0.7), dvNoise2(vec2(ang * 5.0, iTime * 2.0)));
        col += plasma * edge * flames * CometHeat * 1.1;
        col = mix(col, col * vec3(1.12, 0.92, 0.85), CometHeat * 0.35);
    }

    // a result launched from the panel: a beam straight up from the panel into the sky
    if (LaunchBeam > 0.001) {
        vec2 bc = (uv - vec2(0.5, 0.55)) * vec2(aspect, 1.0);
        float up = step(0.0, bc.y);
        float beam = exp(-abs(bc.x) * (60.0 + 140.0 * (1.0 - LaunchBeam))) * up * (0.6 + 0.4 * dvNoise2(vec2(bc.y * 20.0 - iTime * 30.0, 0.0)));
        vec2 star = (uv - vec2(0.5, 0.96)) * vec2(aspect, 1.0);
        float ping = exp(-length(star) * 30.0) + exp(-abs(star.y) * 300.0) * exp(-abs(star.x) * 6.0) * 0.5;
        col += (BeamColor * beam * 3.0 + vec3(1.0, 0.95, 0.85) * ping * 2.5) * LaunchBeam;
    }

    col *= 1.0 - Fade;
    fragColor = vec4(col, 1.0);
}
