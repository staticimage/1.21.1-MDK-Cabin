#version 150
// Single-scattering Rayleigh + Mie through the Blender atmosphere shell (planet radius 1, shell 1.06).
// Drawn additively with depth test off; the planet is occluded analytically.

uniform vec3 SunDir;
uniform vec3 CameraPos;
uniform float Intensity;

in vec3 vWorld;

out vec4 fragColor;

const float RP = 1.0;
const float RA = 1.06;
// Earth's vertical optical depth, spread over a taller (more visible) shell: tau = beta * H.
const float HR = 0.008;
const float HM = 0.003;
const vec3 BETA_R = vec3(5.8, 13.5, 33.1);
const float BETA_M = 8.5;
const float SUN = 20.0;

vec2 raySphere(vec3 ro, vec3 rd, float r) {
    float b = dot(ro, rd);
    float c = dot(ro, ro) - r * r;
    float h = b * b - c;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

void main() {
    if (gl_FrontFacing) discard;
    vec3 ro = CameraPos;
    vec3 rd = normalize(vWorld - CameraPos);
    vec2 ta = raySphere(ro, rd, RA);
    if (ta.y < 0.0) discard;
    float t0 = max(ta.x, 0.0), t1 = ta.y;
    vec2 tp = raySphere(ro, rd, RP);
    if (tp.x > 0.0) t1 = min(t1, tp.x);
    vec3 L = normalize(SunDir);
    float mu = dot(rd, L);
    float phaseR = 3.0 / (16.0 * 3.14159265) * (1.0 + mu * mu);
    float g = 0.76;
    float phaseM = 3.0 / (8.0 * 3.14159265) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));

    const int STEPS = 12;
    float ds = (t1 - t0) / float(STEPS);
    vec3 sumR = vec3(0.0), sumM = vec3(0.0);
    float odR = 0.0, odM = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * (t0 + ds * (float(i) + 0.5));
        float hgt = max(length(p) - RP, 0.0);
        float dR = exp(-hgt / HR) * ds, dM = exp(-hgt / HM) * ds;
        odR += dR;
        odM += dM;
        vec2 tlp = raySphere(p, L, RP);
        if (tlp.x > 0.0) continue;    // in the planet's shadow
        vec2 tl = raySphere(p, L, RA);
        float dl = max(tl.y, 0.0) / 4.0, lR = 0.0, lM = 0.0;
        for (int j = 0; j < 4; j++) {
            float hl = max(length(p + L * dl * (float(j) + 0.5)) - RP, 0.0);
            lR += exp(-hl / HR) * dl;
            lM += exp(-hl / HM) * dl;
        }
        vec3 tau = BETA_R * (odR + lR) + BETA_M * 1.1 * (odM + lM);
        vec3 att = exp(-tau);
        sumR += dR * att;
        sumM += dM * att;
    }
    vec3 col = (sumR * BETA_R * phaseR + sumM * BETA_M * phaseM) * SUN * Intensity;
    fragColor = vec4(1.0 - exp(-col), 0.0);
}
