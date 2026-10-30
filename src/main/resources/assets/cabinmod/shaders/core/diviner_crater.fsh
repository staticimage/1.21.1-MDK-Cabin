#version 150
#moj_import <cabinmod:diviner_common.glsl>
// Molten-glass splash crown on the diviner after the starfall lands. Heat 1 = white hot, 0 = cooled glass.
// Vertex colour R = position along the spike (0 base, 1 tip) painted in Blender.

uniform vec3 CameraPos;
uniform float Heat;
uniform float Fade;
uniform float iTime;
uniform vec3 StarCore;

in vec3 vWorld;
in vec3 vObj;
in vec3 vNormal;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(CameraPos - vWorld);
    float fres = pow(1.0 - abs(dot(N, V)), 2.0);
    float along = vColor.r;
    float n = dvNoise3(vObj * 9.0 + vec3(0.0, -iTime * 0.8, 0.0));
    float heat = clamp(Heat * (1.25 - along * 0.6) + (n - 0.5) * 0.25, 0.0, 1.0);
    vec3 molten = mix(vec3(1.0, 0.35, 0.08), vec3(1.0, 0.95, 0.8), smoothstep(0.55, 1.0, heat));
    vec3 glass = mix(StarCore * 0.45, mix(StarCore, vec3(1.0), 0.6), fres);
    vec3 col = mix(glass * (0.35 + fres), molten * 2.0, smoothstep(0.15, 0.6, heat));
    fragColor = vec4(col * Fade, 0.0);
}
