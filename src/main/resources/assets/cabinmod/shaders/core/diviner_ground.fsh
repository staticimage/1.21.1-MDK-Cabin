#version 150
#moj_import <cabinmod:diviner_common.glsl>
// Ground shockwave decal: an expanding heat ring plus voronoi cracks glowing out from the diviner.
// UV0 spans the decal 0..1; Progress 0..1 over the shockwave's life.

uniform float Progress;
uniform float iTime;
uniform vec3 StarCore;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;
    float R = 0.08 + Progress * 0.95;
    float ring = exp(-pow((r - R) / (0.035 + 0.05 * Progress), 2.0)) * (1.0 - Progress);
    float cracks = 1.0 - smoothstep(0.0, 0.06, dvCracks(p * 7.0));
    float reach = smoothstep(R + 0.05, R - 0.15, r) * smoothstep(1.0, 0.25, r);
    float crackGlow = cracks * reach * pow(1.0 - Progress, 1.5);
    vec3 col = mix(StarCore, vec3(1.0), 0.25) * ring * 1.6 + vec3(1.0, 0.55, 0.2) * crackGlow * 1.3;
    col += vec3(1.0, 0.9, 0.75) * exp(-r * 12.0) * (1.0 - Progress) * 2.0;
    fragColor = vec4(col * vColor.rgb * vColor.a, 0.0);
}
