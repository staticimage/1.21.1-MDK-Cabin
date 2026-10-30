#version 150
#moj_import <cabinmod:diviner_common.glsl>
// Summoning circle drawn on the ground around the diviner while the star falls: concentric rings, a turning
// hexagram, a band of glyphs and inner spokes. Charge 0..1 builds it up before the landing; Flare 0..1 is the
// burst at the impact, after which it burns out.

uniform float Charge;
uniform float Flare;
uniform float iTime;
uniform vec3 StarCore;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

float ringAt(float r, float R, float w) {
    return exp(-pow((r - R) / w, 2.0));
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;
    float a = atan(p.y, p.x);
    float grow = smoothstep(0.0, 1.0, Charge);
    float reach = smoothstep(grow * 1.05 + 0.02, grow * 1.05 - 0.04, r);   // drawn outward from the centre

    float lines = ringAt(r, 0.95, 0.008) + ringAt(r, 0.88, 0.005) + ringAt(r, 0.52, 0.006) + ringAt(r, 0.46, 0.004)
            + ringAt(r, 0.18, 0.005);
    // glyph band between 0.88 and 0.95
    float slot = floor((a / 6.2831 + 0.5) * 40.0 + iTime * 0.4);
    float local = fract((a / 6.2831 + 0.5) * 40.0 + iTime * 0.4);
    float glyph = step(0.5, dvHash11(slot * 3.7 + floor(local * 3.0)))
            * step(0.2, local) * step(local, 0.8) * step(0.895, r) * step(r, 0.935);
    // rotating hexagram (two triangles) inscribed in r = 0.86
    float hex = 1e3;
    float rot = iTime * 0.25;
    for (int k = 0; k < 6; k++) {
        float a0 = rot + float(k) * 2.0944, a1 = a0 + 2.0944;
        if (k >= 3) {
            a0 = -rot + float(k) * 2.0944 + 1.0472;
            a1 = a0 + 2.0944;
        }
        hex = min(hex, segment(p, vec2(cos(a0), sin(a0)) * 0.86, vec2(cos(a1), sin(a1)) * 0.86));
    }
    float star = exp(-hex * hex * 40000.0);
    // spokes inside the inner ring
    float spokes = step(0.92, fract((a / 6.2831) * 12.0 - iTime * 0.1)) * step(0.18, r) * step(r, 0.46);
    float shape = lines + glyph * 0.9 + star + spokes * 0.6;

    float pulse = 0.75 + 0.25 * sin(iTime * 6.0 - r * 12.0);
    vec3 col = mix(StarCore, vec3(1.0, 0.85, 0.45), smoothstep(0.3, 1.0, Flare + r * 0.3)) * shape * pulse;
    col += StarCore * exp(-r * 4.0) * 0.25 * grow;           // soft floor glow
    col *= reach * (0.6 + 1.6 * Flare);
    fragColor = vec4(col * vColor.rgb * vColor.a, 0.0);
}
