#version 150
#moj_import <cabinmod:diviner_common.glsl>
// See-through crafting panel floating in space. Sampler0 = this frame's finished cosmos (the post chain's
// "final" target), sampled with a lens refraction, a frosted blur, chromatic edges and a pulsing rim.
// vLocal is 0..1 across the panel. Reveal 0..1 dissolves the panel in.

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform vec2 PanelSize;
uniform float Reveal;
uniform float iTime;

in vec2 vLocal;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec2 c = vLocal - 0.5;
    // rounded-rect distance in panel pixels
    vec2 px = c * PanelSize;
    vec2 q = abs(px) - (PanelSize * 0.5 - 6.0);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 6.0;
    if (d > 1.0) discard;

    float dissolve = dvNoise2(vLocal * vec2(18.0, 16.0)) * 0.6 + length(c) * 0.8;
    if (dissolve > Reveal * 1.4) discard;
    float burn = smoothstep(Reveal * 1.4 - 0.08, Reveal * 1.4, dissolve) * step(Reveal, 0.999);

    float edge = smoothstep(-14.0, 0.0, d);
    vec2 lens = c * (0.012 + 0.03 * edge)
            + (vec2(dvNoise2(vLocal * 6.0 + iTime * 0.05), dvNoise2(vLocal * 6.0 - 3.1)) - 0.5) * 0.004;
    vec3 blur = vec3(0.0);
    float rad = 5.0 / ScreenSize.y;
    float jitter = dvHash12(gl_FragCoord.xy) * 6.28;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 2.39996 + jitter;
        vec2 o = vec2(cos(a), sin(a)) * rad * sqrt(float(i + 1) / 12.0) * vec2(ScreenSize.y / ScreenSize.x, 1.0);
        vec2 s = uv - lens + o;
        blur += vec3(texture(Sampler0, s + c * 0.004 * edge).r, texture(Sampler0, s).g,
                     texture(Sampler0, s - c * 0.004 * edge).b);
    }
    blur /= 12.0;

    vec3 tint = vec3(0.05, 0.08, 0.16);
    vec3 col = mix(blur * 0.75, tint, 0.45);
    // a slow diagonal sheen
    col += vec3(0.35, 0.6, 1.0) * 0.06
            * smoothstep(0.6, 0.0, abs(vLocal.x + vLocal.y * 0.4 - 0.25 - fract(iTime * 0.05) * 1.6));
    float rim = exp(-abs(d + 1.0) * 0.9);
    float pulse = 0.65 + 0.35 * sin(iTime * 2.0 + vLocal.x * 6.0);
    col += mix(vec3(0.35, 0.85, 1.0), vec3(0.75, 0.45, 1.0), vLocal.x) * rim * pulse * 1.2;
    col += vec3(1.0, 0.85, 0.5) * burn * 0.7;
    fragColor = vec4(col, 0.92 * smoothstep(1.0, -1.0, d));
}
