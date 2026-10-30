#version 150
#moj_import <cabinmod:diviner_common.glsl>
// The Blender-authored planet (design/blender/diviner_space.blend, baked by tools/blender/build_diviner_space.py).
// Sampler0 albedo (sRGB), Sampler1 data (R ocean mask, G height, B city lights), Sampler2 clouds (R density).

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform vec3 SunDir;
uniform vec3 CameraPos;
uniform float CloudSpin;

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 albedo = pow(texture(Sampler0, vUv).rgb, vec3(2.2));
    vec4 data = texture(Sampler1, vUv);
    float ocean = data.r;

    // relief from the baked height: highlands a touch brighter, valleys a touch darker (land only).
    // (A derivative bump was tried; it breaks down at the low altitudes the fall passes through.)
    float relief = mix(1.0, 0.8 + 0.5 * smoothstep(0.52, 0.8, data.g), 1.0 - ocean);
    vec3 Nb = N;

    vec3 V = normalize(CameraPos - vWorld);
    vec3 L = normalize(SunDir);
    float ndl = dot(Nb, L);
    float day = smoothstep(-0.12, 0.2, dot(N, L));

    vec2 cloudUv = vec2(vUv.x + CloudSpin, vUv.y);
    float shadow = texture(Sampler2, cloudUv + vec2(0.0015, 0.001)).r;
    float cloud = texture(Sampler2, cloudUv).r;

    vec3 col = albedo * max(ndl, 0.0) * 2.1 * relief * (1.0 - 0.55 * shadow);
    col += albedo * 0.012;
    // sun glint on water
    vec3 H = normalize(L + V);
    col += ocean * pow(max(dot(N, H), 0.0), 60.0) * 0.7 * day * vec3(1.0, 0.92, 0.8);
    // twilight band at the terminator
    col += albedo * exp(-abs(dot(N, L)) * 9.0) * vec3(1.0, 0.38, 0.12) * 0.5;
    // city lights on the night side, dimmed under cloud
    float night = 1.0 - smoothstep(-0.2, 0.06, dot(N, L));
    // the baked mask marks where cities may be; break it into sparse clusters and pin-point lights
    float speck = smoothstep(0.55, 0.9, dvNoise2(vUv * vec2(3200.0, 1600.0))) * (0.5 + 0.5 * dvNoise2(vUv * 400.0));
    float city = smoothstep(0.45, 1.0, data.b) * speck;
    col += city * night * (1.0 - 0.75 * cloud) * vec3(1.0, 0.68, 0.32) * 2.2;
    // thin blue rim where the view grazes the surface (the atmosphere shell adds the real glow)
    float fres = pow(1.0 - max(dot(N, V), 0.0), 4.0);
    col += vec3(0.25, 0.5, 1.0) * fres * day * 0.5;

    fragColor = vec4(dvTonemap(col * 1.1), 1.0);
}
