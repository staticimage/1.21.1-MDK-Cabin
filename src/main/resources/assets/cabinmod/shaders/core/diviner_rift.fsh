#version 150
#moj_import <cabinmod:diviner_common.glsl>
// The heavens opening above a diviner while someone is up there, as everyone else sees it from the ground:
// a slow swirling ring of light around a hole into deep space, and far away inside it, faint and small,
// the Cabin in Agartha (the diviner's own art, the same image the crafter sees behind the panel).
// UV0 spans the camera-facing disc 0..1. Open 0..1 opens and closes it. Premultiplied alpha: the ring only adds
// light, the hole also covers the sky behind it (so it reads as space by day).

uniform sampler2D Sampler0;
uniform vec4 ArtRect;     // uv rect of the art inside Sampler0 (u0, v0, u1, v1)
uniform float Open;
uniform float Voice;      // 0..1 the Cabin is speaking: the image brightens
uniform float iTime;
uniform vec3 StarCore;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;
    float a = atan(p.y, p.x);
    float R = 0.62 * Open;

    // swirling ring: noise streaked around the rim, turning slowly
    float swirl = dvFbm2(vec2(a * 2.0 + r * 6.0 - iTime * 0.35, r * 5.0 - iTime * 0.2));
    float ring = exp(-pow((r - R) / (0.05 + 0.12 * swirl), 2.0)) * (0.6 + 0.8 * swirl);
    float outer = exp(-max(r - R, 0.0) * 7.0) * smoothstep(R * 0.7, R, r) * 0.35 * swirl;
    vec3 col = mix(StarCore, vec3(1.0, 0.95, 0.85), 0.35) * (ring + outer);

    // inside the hole: deep space and its stars
    float inside = smoothstep(R, R * 0.85, r);
    vec2 sp = p * 40.0;
    vec2 cell = floor(sp);
    float h = dvHash12(cell);
    float star = step(0.93, h) * exp(-length(fract(sp) - 0.5) * 9.0) * (0.6 + 0.4 * sin(iTime * (2.0 + h * 6.0) + h * 40.0));
    col += (vec3(0.02, 0.03, 0.08) + vec3(0.9, 0.95, 1.0) * star) * inside;

    // the Cabin in Agartha, very far away: small, soft, shimmering in and out
    vec2 q = p / max(R * 0.55, 1e-3);
    if (abs(q.x) < 1.0 && abs(q.y) < 1.0) {
        vec2 uv = mix(ArtRect.xy, ArtRect.zw, vec2(q.x * 0.5 + 0.5, 0.5 - q.y * 0.5));
        uv += vec2(dvNoise2(q * 3.0 + iTime * 0.3) - 0.5, dvNoise2(q * 3.0 - iTime * 0.25) - 0.5) * 0.004;
        vec3 art = texture(Sampler0, uv).rgb;
        float vignette = smoothstep(1.0, 0.4, length(q));
        float flicker = 0.75 + 0.25 * dvNoise2(vec2(iTime * 0.7, 3.1));
        col += art * vignette * inside * flicker * (0.32 + 0.4 * Voice);
    }

    col *= Open;
    float cover = inside * 0.92 * Open;
    fragColor = vec4(col * vColor.rgb * vColor.a, cover * vColor.a);
}
