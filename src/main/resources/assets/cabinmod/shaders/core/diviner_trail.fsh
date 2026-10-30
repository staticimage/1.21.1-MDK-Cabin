#version 150
#moj_import <cabinmod:diviner_common.glsl>
// The starfall's plasma ribbon. UV0.x across (0..1), UV0.y along the trail (0 at the comet head).
// White core -> StarCore -> StarTrail ionised banding, scrolling toward the tail.

uniform float iTime;
uniform vec3 StarCore;
uniform vec3 StarTrail;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

void main() {
    float across = abs(vUv.x * 2.0 - 1.0);
    float along = vUv.y;
    float n = dvFbm2(vec2(vUv.x * 3.0, along * 6.0 - iTime * 4.0));
    float width = mix(0.25, 1.0, smoothstep(0.0, 0.6, along)) * (0.75 + 0.5 * n);
    float body = smoothstep(width, width * 0.2, across);
    float core = exp(-across * across * 40.0) * (1.0 - along);
    float fade = pow(1.0 - along, 1.6);
    vec3 band = mix(StarCore, StarTrail, smoothstep(0.15, 0.8, along + (n - 0.5) * 0.3));
    vec3 col = band * body * fade * 1.4 + vec3(1.0, 0.97, 0.9) * core * 2.0;
    fragColor = vec4(col * vColor.rgb * vColor.a, 0.0);
}
