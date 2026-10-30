#version 150
#moj_import <cabinmod:diviner_common.glsl>
// Emissive crystal: the comet, impact shards and the impact dome. Additive.
// Vertex colour alpha fades the comet's tail fins out (painted in Blender).

uniform vec3 CameraPos;
uniform vec4 Tint;
uniform float Glow;
uniform vec3 StarCore;    // the star's colours (recipe "star" or the items' own, client/diviner/StarColors)
uniform vec3 StarTrail;
uniform float iTime;

in vec3 vWorld;
in vec3 vObj;
in vec3 vNormal;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(CameraPos - vWorld);
    float facing = abs(dot(N, V));
    float fres = pow(1.0 - facing, 2.2);
    float shimmer = 0.75 + 0.25 * sin(iTime * 6.0 + dvNoise3(vObj * 6.0) * 12.0);
    vec3 core = vec3(1.0, 0.97, 0.92);
    vec3 edge = mix(StarCore, StarTrail, fres);
    vec3 col = mix(core * (0.6 + 0.4 * facing), edge, clamp(fres * 1.4, 0.0, 1.0)) * vColor.rgb * Tint.rgb;
    col *= Glow * shimmer * (0.45 + 1.4 * fres);
    fragColor = vec4(col * vColor.a * Tint.a, 0.0);
}
