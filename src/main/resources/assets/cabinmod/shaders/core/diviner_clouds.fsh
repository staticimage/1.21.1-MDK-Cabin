#version 150

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
    float c = texture(Sampler2, vec2(vUv.x + CloudSpin, vUv.y)).r;
    if (c < 0.01) discard;
    float ndl = dot(N, normalize(SunDir));
    float lit = smoothstep(-0.15, 0.35, ndl);
    vec3 col = mix(vec3(0.015, 0.02, 0.035), vec3(1.0, 0.98, 0.95), lit);
    col = mix(col, vec3(1.0, 0.55, 0.3), exp(-abs(ndl) * 10.0) * 0.5);   // pink-orange at the terminator
    vec3 V = normalize(CameraPos - vWorld);
    float edge = 1.0 - pow(1.0 - max(dot(N, V), 0.0), 3.0);
    fragColor = vec4(col, clamp(c * 1.25, 0.0, 0.95) * edge);
}
