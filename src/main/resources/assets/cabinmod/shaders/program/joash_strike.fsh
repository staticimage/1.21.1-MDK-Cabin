#version 150
// Ported from Orbital Railgun 1.4.0 (strike.fsh) by Mudcrow and Gw Brother, MIT License -
// see shaders/LICENSE_orbital_railgun.txt. Changes for Cabin Mod: GLSL 150 for Minecraft 1.21.1,
// the std140 RailgunConfig block split into plain uniforms (set from OrbitalStrikeEffect.java).


uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform float iTime;
uniform float IsBlockHit;
uniform float DestructionRange;
uniform vec3 CameraPosition;
uniform vec3 BlockPosition;
uniform float ShockwaveSpeed;
uniform float ShockwaveDuration;
uniform float ScreenShake;

in vec2 texCoord;
out vec4 fragColor;

const vec3 COLOR_CYAN = vec3(0.15, 0.92, 1.0);
const vec3 COLOR_CYAN_HOT = vec3(0.85, 1.0, 1.0);
const vec3 COLOR_FIRE_CORE = vec3(1.0, 0.85, 0.35);
const vec3 COLOR_FIRE_GLOW = vec3(1.0, 0.28, 0.03);
const vec3 COLOR_SPACE_VOID = vec3(0.003, 0.003, 0.008);

const float SUN_FALL_START = 0.80;
const float PLANET_DROP_START = 0.34;
const float PLANET_DROP_STEP = 0.048;
const float PLANET_DROP_LEN = 0.068;

vec2 rotate(vec2 p, float r) {
    float c = cos(r);
    float s = sin(r);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

vec3 worldPos(vec2 uv, float depth) {
    vec3 ndc = vec3(uv, depth) * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    float w = (abs(homPos.w) > 1e-4) ? homPos.w : 1e-4;
    return (homPos.xyz / w) + CameraPosition;
}

float rayToSegmentDist(vec3 rayStart, vec3 rayDir, float maxT, vec3 segA, vec3 segB) {
    vec3 u = rayDir;
    vec3 v = segB - segA;
    vec3 w0 = rayStart - segA;
    float a = dot(u, u);
    float b = dot(u, v);
    float c = dot(v, v);
    float d = dot(u, w0);
    float e = dot(v, w0);
    float D = a * c - b * b;

    float s = (D < 1e-5) ? 0.0 : clamp((b * d - a * e) / D, 0.0, 1.0);
    float t = clamp((b * s - d) / max(a, 1e-5), 0.0, maxT);
    vec3 pRay = rayStart + u * t;
    vec3 pSeg = segA + v * s;
    return length(pRay - pSeg);
}

float raySphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - r * r);
    if (h < 0.0) return -1.0;
    h = sqrt(h);
    float t = -b - h;
    return (t > 0.0) ? t : -1.0;
}

float rayPointDist(vec3 ro, vec3 rd, vec3 p, out float tClosest) {
    tClosest = max(dot(p - ro, rd), 0.0);
    return length(ro + rd * tClosest - p);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973) + vec3(12.34, 56.78, 90.12));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float noise1(float t) {
    float i = floor(t);
    float f = fract(t);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), f);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 5; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

float craters(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float res = 0.0;
    for (int z = -1; z <= 1; z++)
    for (int y = -1; y <= 1; y++)
    for (int x = -1; x <= 1; x++) {
        vec3 o = vec3(float(x), float(y), float(z));
        vec3 cell = i + o;
        vec3 r = vec3(hash13(cell), hash13(cell + 17.1), hash13(cell + 41.7));
        float rad = 0.18 + 0.22 * hash13(cell + 5.3);
        float d = length(o + r - f) / rad;
        res += -0.30 * smoothstep(1.0, 0.55, d) + 0.22 * exp(-(d - 1.0) * (d - 1.0) * 30.0);
    }
    return res;
}

vec3 fireRamp(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 c = mix(vec3(0.06, 0.03, 0.03), vec3(0.75, 0.08, 0.02), smoothstep(0.0, 0.3, t));
    c = mix(c, vec3(1.0, 0.45, 0.05), smoothstep(0.25, 0.55, t));
    c = mix(c, vec3(1.0, 0.85, 0.35), smoothstep(0.5, 0.8, t));
    c = mix(c, vec3(1.0, 0.98, 0.9), smoothstep(0.8, 1.0, t));
    return c * (0.4 + 2.6 * t);
}

const float ringAngularR[8] = float[8](0.12, 0.18, 0.26, 0.35, 0.46, 0.58, 0.72, 0.86);
const float planetVisRadius[8] = float[8](0.026, 0.035, 0.042, 0.034, 0.078, 0.068, 0.048, 0.046);

vec3 getPlanetGlow(int k) {
    if (k == 0) return vec3(0.75, 0.70, 0.65);
    if (k == 1) return vec3(1.00, 0.85, 0.50);
    if (k == 2) return vec3(0.35, 0.65, 1.00);
    if (k == 3) return vec3(1.00, 0.50, 0.30);
    if (k == 4) return vec3(0.95, 0.80, 0.60);
    if (k == 5) return vec3(0.98, 0.88, 0.62);
    if (k == 6) return vec3(0.55, 0.92, 0.95);
    return vec3(0.35, 0.55, 1.00);
}

vec3 planetAlbedo(int k, vec3 q, float t, out float spec, out vec3 atmo, out float atmoAmt, out float clouds) {
    spec = 0.0;
    clouds = 0.0;
    atmo = getPlanetGlow(k);
    atmoAmt = 0.25;
    float lat = q.y;
    float lon = atan(q.z, q.x);

    if (k == 0) {
        float c = fbm(q * 3.0 + 4.0);
        vec3 col = mix(vec3(0.36, 0.34, 0.32), vec3(0.62, 0.59, 0.55), c);
        col *= 1.0 + craters(q * 5.0) + 0.6 * craters(q * 11.0 + 3.0);
        atmoAmt = 0.0;
        return col;
    }

    if (k == 1) {
        vec3 w = q + vec3(fbm(q * 2.0 + t * 0.03), fbm(q * 2.0 + 5.2), fbm(q * 2.0 + 9.1)) * 1.3;
        float c = fbm(w * 2.2 + vec3(0.0, 0.0, t * 0.04));
        float bands = 0.5 + 0.5 * sin(lat * 9.0 + c * 4.0);
        vec3 col = mix(vec3(0.70, 0.52, 0.28), vec3(0.96, 0.86, 0.60), c * 0.7 + bands * 0.3);
        atmo = vec3(1.0, 0.85, 0.5);
        atmoAmt = 0.9;
        return col;
    }

    if (k == 2) {
        float c = fbm(q * 1.7 + vec3(3.1, 1.7, 0.4));
        float land = smoothstep(0.515, 0.535, c);
        float coast = smoothstep(0.42, 0.515, c);
        vec3 ocean = mix(vec3(0.01, 0.05, 0.20), vec3(0.03, 0.22, 0.45), coast);
        float dry = fbm(q * 3.3 + 7.0);
        vec3 green = mix(vec3(0.07, 0.24, 0.05), vec3(0.22, 0.36, 0.10), dry);
        float desertAmt = smoothstep(0.52, 0.66, dry + (0.18 - abs(abs(lat) - 0.33)) * 0.8);
        vec3 landCol = mix(green, vec3(0.66, 0.52, 0.30), desertAmt);
        landCol = mix(landCol, vec3(0.38, 0.32, 0.27), smoothstep(0.62, 0.74, c));
        vec3 col = mix(ocean, landCol, land);
        float ice = smoothstep(0.76, 0.82, abs(lat) + (fbm(q * 4.0) - 0.5) * 0.18);
        col = mix(col, vec3(0.92, 0.95, 1.0), ice);

        vec3 cq = q;
        cq.xz = rotate(cq.xz, t * 0.05);
        float cl = fbm(cq * 2.6 + vec3(0.0, t * 0.03, 0.0) + fbm(cq * 1.7 + 2.0) * 1.6);
        clouds = smoothstep(0.50, 0.68, cl);
        col = mix(col, vec3(0.96, 0.97, 1.0), clouds * 0.88);

        spec = (1.0 - land) * (1.0 - clouds) * (1.0 - ice);
        atmo = vec3(0.30, 0.62, 1.0);
        atmoAmt = 1.3;
        return col;
    }

    if (k == 3) {
        float c = fbm(q * 2.2 + 11.0);
        vec3 col = mix(vec3(0.62, 0.26, 0.11), vec3(0.84, 0.50, 0.28), smoothstep(0.30, 0.70, c));
        float dark = smoothstep(0.50, 0.62, fbm(q * 1.6 + 4.0));
        col = mix(col, vec3(0.30, 0.14, 0.08), dark * 0.75);
        float canyon = abs(lat + 0.05 + (vnoise(q * 7.0) - 0.5) * 0.08);
        float canyonMask = smoothstep(-1.2, -0.5, lon) * smoothstep(0.9, 0.2, lon);
        col = mix(col, vec3(0.26, 0.11, 0.06), smoothstep(0.03, 0.0, canyon) * canyonMask * 0.85);
        col *= 1.0 + 0.8 * craters(q * 6.0 + 1.0);
        float cap = smoothstep(0.84, 0.88, lat + (vnoise(q * 8.0) - 0.5) * 0.06)
                  + 0.7 * smoothstep(0.90, 0.94, -lat + (vnoise(q * 8.0 + 3.0) - 0.5) * 0.06);
        col = mix(col, vec3(0.95, 0.93, 0.92), clamp(cap, 0.0, 1.0));
        atmo = vec3(1.0, 0.58, 0.38);
        atmoAmt = 0.45;
        return col;
    }

    if (k == 4) {
        float turb = fbm(vec3(q.x * 3.0, q.y * 7.0, q.z * 3.0) + vec3(t * 0.03, 0.0, 0.0)) - 0.5;
        float b = sin((lat * 9.0 + turb * 1.2) * 3.14159);
        vec3 zone = vec3(0.93, 0.87, 0.76);
        vec3 belt = vec3(0.60, 0.38, 0.22);
        vec3 col = mix(zone, belt, smoothstep(-0.2, 0.6, b));
        col *= 0.93 + 0.07 * sin(lat * 70.0 + turb * 10.0);
        col = mix(col, vec3(0.75, 0.45, 0.30), smoothstep(0.55, 0.75, fbm(q * 5.0 + 2.0)) * 0.3);
        vec2 spot = vec2((lon - 1.0) * 0.9, (lat + 0.36) * 2.6);
        float sd = length(spot);
        float swirl = 0.5 + 0.5 * sin(sd * 40.0 - atan(spot.y, spot.x) * 2.0);
        col = mix(col, mix(vec3(0.72, 0.28, 0.14), vec3(0.85, 0.45, 0.25), swirl), smoothstep(0.16, 0.10, sd));
        atmo = vec3(0.95, 0.85, 0.7);
        atmoAmt = 0.35;
        return col;
    }

    if (k == 5) {
        float turb = fbm(vec3(q.x * 2.0, q.y * 6.0, q.z * 2.0)) - 0.5;
        float b = 0.5 + 0.5 * sin(lat * 22.0 + turb * 2.0);
        vec3 col = mix(vec3(0.80, 0.68, 0.45), vec3(0.95, 0.88, 0.66), b);
        col = mix(col, vec3(0.55, 0.60, 0.62), smoothstep(0.80, 0.95, abs(lat)) * 0.6);
        atmo = vec3(1.0, 0.9, 0.7);
        atmoAmt = 0.3;
        return col;
    }

    if (k == 6) {
        vec3 col = vec3(0.60, 0.86, 0.90) * (0.96 + 0.04 * sin(lat * 12.0 + fbm(q * 3.0) * 2.0));
        atmo = vec3(0.6, 0.95, 1.0);
        atmoAmt = 0.6;
        return col;
    }

    vec3 col = vec3(0.16, 0.30, 0.82) * (0.9 + 0.1 * sin(lat * 14.0 + fbm(q * 3.0) * 3.0));
    float ds = length(vec2((lon + 0.8) * 0.8, (lat + 0.30) * 2.4));
    col = mix(col, vec3(0.07, 0.14, 0.45), smoothstep(0.16, 0.10, ds));
    float cirrus = smoothstep(0.72, 0.85, vnoise(vec3(lon * 2.0 + t * 0.05, lat * 30.0, 1.0)));
    col = mix(col, vec3(0.92, 0.95, 1.0), cirrus * 0.7);
    atmo = vec3(0.35, 0.55, 1.0);
    atmoAmt = 0.7;
    return col;
}

vec3 getPlanetColor(int k) {
    if (k == 0) return vec3(0.85, 0.65, 0.50);
    if (k == 1) return vec3(1.00, 0.86, 0.42);
    if (k == 2) return vec3(0.20, 0.65, 1.00);
    if (k == 3) return vec3(1.00, 0.35, 0.15);
    if (k == 4) return vec3(0.95, 0.70, 0.40);
    if (k == 5) return vec3(0.98, 0.88, 0.55);
    if (k == 6) return vec3(0.38, 0.94, 0.90);
    return vec3(0.22, 0.52, 1.00);
}

vec4 renderPlanet3D(int k, vec2 cUV, vec2 pPos2D, float pRadius, float iTime) {
    vec2 pOffset = (cUV - pPos2D) / pRadius;
    float d2 = dot(pOffset, pOffset);
    float distToPlanetCenter = length(cUV - pPos2D);
    float halo = (0.0012 * pRadius) / (distToPlanetCenter * distToPlanetCenter + 0.0006);
    vec3 pBase = getPlanetColor(k);

    if (d2 > 1.0) {
        return vec4(pBase * halo * 0.80, clamp(halo * 0.45, 0.0, 0.50));
    }

    vec3 N = vec3(pOffset.x, pOffset.y, sqrt(max(0.0, 1.0 - d2)));
    vec3 L_sun = normalize(vec3(-pPos2D.x, -pPos2D.y, 0.35));
    float NdotL = dot(N, L_sun);
    float shadowTerm = smoothstep(-0.20, 0.30, NdotL);
    float ambient = 0.18 + 0.82 * shadowTerm;
    float rim = pow(1.0 - N.z, 2.5);

    float lat = N.y;
    float rotSpeed = 0.50 / sqrt(float(k + 1));
    float lon = atan(N.x, N.z) + iTime * rotSpeed;

    vec3 col = pBase;

    if (k == 0) {
        vec3 baseM = vec3(0.64, 0.58, 0.52);
        float craters = sin(lon * 24.0) * sin(lat * 24.0) + sin(lon * 12.0 + lat * 14.0) * 0.5;
        float maria = sin(lon * 6.0) * cos(lat * 7.0);
        col = mix(baseM * 0.70, baseM * 1.15, 0.5 + 0.3 * craters + 0.2 * maria) * ambient;
    }

    else if (k == 1) {
        float vSwirl = sin(lat * 16.0 + sin(lon * 4.0 - iTime * 1.2) * 1.5 + lon * 2.0);
        vec3 vBase = mix(vec3(0.96, 0.82, 0.50), vec3(0.85, 0.65, 0.32), 0.5 + 0.5 * vSwirl);
        col = vBase * (0.22 + 0.78 * shadowTerm) + vec3(1.0, 0.85, 0.45) * rim * 0.65;
    }

    else if (k == 2) {
        float continent = sin(lon * 4.0) * cos(lat * 5.0) + sin(lon * 7.0 + lat * 3.0) * 0.4;
        bool isLand = (continent > 0.05);
        vec3 earthOcean = vec3(0.04, 0.24, 0.68);
        vec3 earthLand = mix(vec3(0.18, 0.48, 0.16), vec3(0.55, 0.45, 0.22), clamp(lat * 1.5, 0.0, 1.0));
        vec3 surface = isLand ? earthLand : earthOcean;
        if (abs(lat) > 0.72) surface = vec3(0.95, 0.98, 1.0);
        float clouds = smoothstep(0.40, 0.85, sin(lat * 10.0 + lon * 6.0 + iTime * 0.5) * cos(lat * 5.0 - lon * 3.0));
        surface = mix(surface, vec3(1.0, 1.0, 1.0), clouds * 0.88);
        vec3 H = normalize(L_sun + vec3(0.0, 0.0, 1.0));
        float spec = pow(max(dot(N, H), 0.0), 16.0) * (isLand ? 0.0 : 1.0) * shadowTerm;
        float atmo = pow(1.0 - N.z, 2.2);
        col = surface * (0.22 + 0.78 * shadowTerm) + vec3(0.9, 0.95, 1.0) * spec * 0.75 + vec3(0.25, 0.65, 1.0) * atmo * 0.60;
    }

    else if (k == 3) {
        vec3 marsRed = vec3(0.86, 0.34, 0.14);
        float basalt = sin(lon * 5.0) * cos(lat * 6.0) + sin(lon * 11.0) * 0.3;
        vec3 surface = mix(marsRed, vec3(0.38, 0.18, 0.12), smoothstep(0.1, 0.5, basalt));
        if (lat > 0.68) surface = vec3(0.98, 0.96, 0.95);
        col = surface * (0.20 + 0.80 * shadowTerm) + vec3(0.9, 0.45, 0.2) * rim * 0.40;
    }

    else if (k == 4) {
        float jBands = sin(lat * 30.0 + sin(lon * 6.0) * 0.25);
        vec3 jCream = vec3(0.92, 0.84, 0.72);
        vec3 jBelt = vec3(0.65, 0.36, 0.16);
        vec3 surface = mix(jCream, jBelt, smoothstep(-0.3, 0.4, jBands));
        float spotDist = length(vec2(lon - 1.5, (lat + 0.32) * 2.8));
        if (spotDist < 0.24) {
            surface = mix(vec3(0.88, 0.24, 0.10), surface, smoothstep(0.12, 0.24, spotDist));
        }
        col = surface * (0.22 + 0.78 * shadowTerm) + vec3(0.9, 0.7, 0.5) * rim * 0.35;
    }

    else if (k == 5) {
        float sBands = sin(lat * 24.0);
        vec3 surface = mix(vec3(0.92, 0.84, 0.58), vec3(0.78, 0.66, 0.42), 0.5 + 0.5 * sBands);
        col = surface * (0.22 + 0.78 * shadowTerm) + vec3(0.95, 0.85, 0.6) * rim * 0.35;
    }

    else if (k == 6) {
        vec3 surface = vec3(0.42, 0.88, 0.86);
        float uBands = 0.95 + 0.05 * sin(lat * 16.0);
        col = surface * uBands * (0.22 + 0.78 * shadowTerm) + vec3(0.6, 0.95, 0.95) * pow(1.0 - N.z, 2.0) * 0.45;
    }

    else {
        vec3 surface = vec3(0.14, 0.38, 0.92);
        float cirrus = smoothstep(0.65, 0.95, sin(lat * 22.0 + lon * 7.0 + iTime * 0.8));
        surface = mix(surface, vec3(0.95, 0.98, 1.0), cirrus * 0.75);
        col = surface * (0.22 + 0.78 * shadowTerm) + vec3(0.3, 0.6, 1.0) * pow(1.0 - N.z, 2.0) * 0.50;
    }

    col += pBase * halo * 0.45;
    return vec4(col * 1.35, 1.0);
}

vec3 earthLightning(vec3 q, float clouds, float t) {
    vec3 cell = floor(q * 5.0);
    float r = hash13(cell);
    float phase = fract(t * (0.35 + r * 0.8) + r * 7.0);
    float flash = step(phase, 0.09) * (0.55 + 0.45 * sin(t * 95.0 + r * 20.0));
    if (flash <= 0.0) return vec3(0.0);
    float b = 1.0 - abs(vnoise(q * 26.0 + r * 13.0) * 2.0 - 1.0);
    float bolt = pow(b, 16.0);
    float glow = smoothstep(0.55, 0.0, length(fract(q * 5.0) - 0.5));
    return vec3(0.72, 0.82, 1.0) * flash * (bolt * 4.0 + glow * 0.6) * (0.35 + clouds);
}

vec3 shadePlanet(int k, vec3 n, vec3 q, vec3 L, vec3 V, float t) {
    float spec;
    vec3 atmo;
    float atmoAmt;
    float clouds;
    vec3 alb = planetAlbedo(k, q, t, spec, atmo, atmoAmt, clouds);

    float ndl = dot(n, L);
    float day = smoothstep(-0.12, 0.25, ndl);
    vec3 col = alb * (0.10 + 1.15 * max(ndl, 0.0) + 0.08 * day);

    vec3 H = normalize(L + V);
    col += vec3(1.0, 0.95, 0.85) * spec * pow(max(dot(n, H), 0.0), 40.0) * 0.9 * day;

    float fres = pow(1.0 - max(dot(n, V), 0.0), 3.0);
    col += atmo * fres * atmoAmt * (0.25 + 0.75 * smoothstep(-0.3, 0.4, ndl));

    if (k == 2) {
        float night = 1.0 - day;
        float c = fbm(q * 1.7 + vec3(3.1, 1.7, 0.4));
        float cities = smoothstep(0.525, 0.54, c) * step(0.74, vnoise(q * 45.0)) * (1.0 - clouds);
        col += vec3(1.0, 0.72, 0.38) * cities * night * 0.8;
        col += earthLightning(q, clouds, t) * (0.6 + 0.9 * night);
    }
    return col;
}

vec3 planetLocal(int k, vec3 n, float t) {
    float tilt = (k == 2) ? 0.41 : ((k == 6) ? 1.2 : 0.15);
    vec3 q = n;
    q.xy = rotate(q.xy, tilt);
    q.xz = rotate(q.xz, t * (0.35 / sqrt(float(k + 1))) + float(k) * 1.3);
    return q;
}

vec3 moonAlbedo(vec3 q) {
    float maria = smoothstep(0.50, 0.62, fbm(q * 1.8 + 30.0));
    vec3 col = mix(vec3(0.68, 0.67, 0.66), vec3(0.34, 0.34, 0.36), maria);
    return col * (1.0 + craters(q * 6.0 + 9.0));
}

vec3 sunSurface(vec3 n, float mu, float t) {
    float g = fbm(n * 8.0 + vec3(0.0, t * 0.2, t * 0.1));
    float g2 = vnoise(n * 32.0 + vec3(t * 0.6));
    float gran = 0.72 + 0.38 * g + 0.15 * g2;
    float spots = smoothstep(0.64, 0.70, fbm(n * 2.6 + 20.0));
    float limb = pow(clamp(mu, 0.0, 1.0), 0.45);
    vec3 col = mix(vec3(1.0, 0.42, 0.06), vec3(1.0, 0.93, 0.72), limb) * gran;
    col = mix(col, vec3(0.30, 0.06, 0.0), spots * 0.85);
    return col * (1.6 + 1.6 * limb);
}

vec3 renderStars(vec3 d, float t) {
    vec3 ad = abs(d);
    vec2 uv;
    float faceId;
    if (ad.x >= ad.y && ad.x >= ad.z) {
        uv = d.yz / ad.x;
        faceId = (d.x > 0.0) ? 0.0 : 1.0;
    } else if (ad.y >= ad.x && ad.y >= ad.z) {
        uv = d.xz / ad.y;
        faceId = (d.y > 0.0) ? 2.0 : 3.0;
    } else {
        uv = d.xy / ad.z;
        faceId = (d.z > 0.0) ? 4.0 : 5.0;
    }

    vec3 starAccum = vec3(0.0);

    float scale1 = 52.0;
    vec2 st1 = (uv + faceId * 19.37) * scale1;
    vec2 id1 = floor(st1);
    vec2 gv1 = fract(st1) - 0.5;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y));
            vec2 rnd = hash22(id1 + offset);
            if (rnd.x > 0.38) {
                vec2 starPos = offset + (rnd - 0.5) * 0.72;
                float dist = length(gv1 - starPos);
                float size = 0.07 + rnd.y * 0.05;
                float starCore = smoothstep(size, 0.0, dist);
                float starGlow = (0.0028 * size) / (dist * dist + 0.0035);
                float twinkle = 0.72 + 0.28 * sin(rnd.x * 62.83 + t * (1.5 + rnd.y * 2.5));
                vec3 starCol;
                float cTemp = fract(rnd.x * 7.31 + rnd.y * 3.19);
                if (cTemp < 0.25) starCol = vec3(0.72, 0.85, 1.0);
                else if (cTemp < 0.65) starCol = vec3(0.98, 0.99, 1.0);
                else if (cTemp < 0.85) starCol = vec3(1.0, 0.92, 0.78);
                else starCol = vec3(1.0, 0.76, 0.62);
                starAccum += starCol * (starCore * 1.5 + starGlow * 0.8) * twinkle;
            }
        }
    }

    float scale2 = 24.0;
    vec2 st2 = (uv + faceId * 37.13 + 5.71) * scale2;
    vec2 id2 = floor(st2);
    vec2 gv2 = fract(st2) - 0.5;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y));
            vec2 rnd = hash22(id2 + offset);
            if (rnd.x > 0.80) {
                vec2 starPos = offset + (rnd - 0.5) * 0.68;
                vec2 dStar = gv2 - starPos;
                float dist = length(dStar);
                float starCore = smoothstep(0.11, 0.0, dist);
                float starHalo = 0.009 / (dist * dist + 0.007);
                float spikes = max(smoothstep(0.028, 0.0, abs(dStar.x)) * smoothstep(0.28, 0.0, abs(dStar.y)),
                                   smoothstep(0.028, 0.0, abs(dStar.y)) * smoothstep(0.28, 0.0, abs(dStar.x)));
                float twinkle = 0.68 + 0.32 * sin(rnd.y * 79.13 + t * (2.0 + rnd.x * 2.2));
                vec3 starCol = mix(vec3(0.85, 0.95, 1.0), vec3(1.0, 0.88, 0.72), rnd.y);
                starAccum += starCol * (starCore * 2.0 + starHalo * 1.2 + spikes * 0.55) * twinkle;
            }
        }
    }
    return starAccum;
}

vec3 softTonemap(vec3 c) {
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    if (luma <= 0.72) return c;
    float softLuma = 0.72 + 0.28 * (1.0 - exp(-(luma - 0.72) / 0.55));
    return c * (softLuma / max(luma, 0.0001));
}

void main() {
    float totalDuration = (IsBlockHit > 1.0) ? IsBlockHit : 35.0;
    float tau = iTime / totalDuration;
    float visualScale = max(DestructionRange / 24.0, 0.35);
    float shockSpeed = max(ShockwaveSpeed, 1.0);
    float shockDur = max(ShockwaveDuration, 0.5);

    float tailLen = max(6.0, shockDur + 3.5);
    float blastTime = max(iTime - totalDuration, 0.0);
    bool exploded = tau >= 1.0;

    float shake = 0.9 * exp(-iTime * 5.0);
    for (int k = 0; k < 8; k++) {
        float impactSec = (PLANET_DROP_START + float(k) * PLANET_DROP_STEP + PLANET_DROP_LEN) * totalDuration;
        float since = iTime - impactSec;
        if (since > 0.0) shake += (0.7 + 0.08 * float(k)) * exp(-since * 3.2);
    }
    float sunFall = clamp((tau - SUN_FALL_START) / (1.0 - SUN_FALL_START), 0.0, 1.0);
    if (!exploded) shake += 0.15 * sunFall * sunFall;
    if (exploded) shake += 1.9 * exp(-blastTime * 1.4);

    vec3 camRel = worldPos(texCoord, 0.0) - BlockPosition;
    float shockRadius = shockSpeed * blastTime;
    float shockLife = exploded ? (1.0 - smoothstep(shockDur * 0.55, shockDur, blastTime)) : 0.0;
    if (exploded) shake += 0.8 * shockLife * exp(-abs(length(camRel) - shockRadius) / (6.0 * visualScale));
    shake = min(shake, 2.2) * ScreenShake;

    vec2 uv = texCoord;
    if (shake > 0.001) {
        float a = shake * 0.008;
        vec2 jitter = vec2(noise1(iTime * 24.0), noise1(iTime * 27.0 + 41.0)) * 2.0 - 1.0;
        float roll = (noise1(iTime * 15.0 + 7.0) * 2.0 - 1.0) * shake * 0.012;
        uv = 0.5 + rotate((uv - 0.5) * (1.0 - 2.5 * a), roll) + jitter * a;
    }

    float depth = texture(DepthSampler, uv).r;
    vec3 pNear = worldPos(uv, 0.0);
    vec3 pMid = worldPos(uv, 0.5);
    vec3 dir = normalize(pMid - pNear);
    vec3 start_point = pNear - BlockPosition;

    float farDist = length(worldPos(uv, 1.0) - pNear);
    vec2 texel = 2.0 / vec2(textureSize(DepthSampler, 0));
    float skyW = 0.0;
    for (int i = 0; i < 9; i++) {
        vec2 o = vec2(float(i % 3 - 1), float(i / 3 - 1)) * texel;
        vec2 sp = clamp(uv + o, 0.0, 1.0);
        float d = (i == 4) ? depth : texture(DepthSampler, sp).r;
        float w = (d >= 1.0) ? 1.0 : smoothstep(farDist * 0.3, farDist * 0.45, length(worldPos(sp, d) - pNear));
        skyW = max(skyW, w);
    }
    bool isSky = skyW > 0.5;
    vec3 end_point;
    float terrainDist;
    if (!isSky) {
        end_point = worldPos(uv, depth) - BlockPosition;
        terrainDist = length(end_point - start_point);
    } else {
        terrainDist = 2000.0;
        end_point = start_point + dir * 2000.0;
    }

    float tc;
    float dc = rayPointDist(start_point, dir, vec3(0.0), tc);
    float shellW = (1.5 + shockRadius * 0.04) * visualScale;
    float shellHit = exploded ? exp(-pow((dc - shockRadius) / shellW, 2.0)) * shockLife * step(tc, terrainDist + shellW) : 0.0;
    vec2 grad = vec2(dFdx(dc), dFdy(dc));
    vec2 sampleUV = uv;
    if (shellHit > 0.001 && dot(grad, grad) > 1e-10) {
        sampleUV += normalize(grad) * shellHit * 0.010;
    }
    vec3 original;
    float ca = exploded ? (shake * 0.0018 + shellHit * 0.004) * (1.0 - smoothstep(0.4, 1.2, blastTime)) : 0.0;
    ca += 0.004 * exp(-iTime * 6.0);
    if (ca > 0.0003) {
        vec2 caDir = (uv - 0.5) * ca * 2.0;
        original = vec3(texture(DiffuseSampler, sampleUV + caDir).r,
                        texture(DiffuseSampler, sampleUV).g,
                        texture(DiffuseSampler, sampleUV - caDir).b);
    } else {
        original = texture(DiffuseSampler, sampleUV).rgb;
    }

    vec3 col = original;

    float release = 1.0 - smoothstep(0.5, tailLen - 0.3, blastTime);
    float stormIn = smoothstep(0.0, 0.06, tau);
    float cloudH = 110.0 * visualScale;
    float spreadR = 30.0 + 2600.0 * pow(smoothstep(0.004, 0.12, tau), 1.5);
    float holeP = smoothstep(0.09, 0.27, tau);
    float holeThr = 1.0 - 1.3 * holeP;
    float holeNoise = (fbm(vec3(atan(dir.z, dir.x) * 2.5, holeP * 3.0, iTime * 0.1)) - 0.5) * 0.25;
    float inHoleA = smoothstep(holeThr, holeThr + 0.07, dir.y + holeNoise) * step(0.001, holeP);

    float flash = 0.0;
    vec2 flashPos = vec2(0.0);
    for (int i = 0; i < 8; i++) {
        float fi = float(i);
        float te = 0.012 + fi * 0.034 + hash11(fi * 9.1 + 1.0) * 0.02;
        float age = (tau - te) * totalDuration;
        if (age > 0.0 && age < 0.7) {
            float f = exp(-age * 8.0) * (0.55 + 0.45 * step(0.45, fract(age * 21.0 + fi)));
            if (f > flash) {
                flash = f;
                flashPos = (vec2(hash11(fi * 3.3 + 2.0), hash11(fi * 5.7 + 3.0)) - 0.5) * 300.0 * visualScale;
            }
        }
    }
    flash *= 1.0 - smoothstep(0.24, 0.32, tau);

    float cloudA = 0.0;
    vec3 cloudCol = vec3(0.0);
    float spaceMask = inHoleA;
    if (abs(dir.y) > 0.002) {
        float tP = (cloudH - start_point.y) / dir.y;
        if (tP > 0.0) {
            vec3 pc = start_point + dir * tP;
            float r = length(pc.xz);
            float inHole = inHoleA;
            if (tP < terrainDist || isSky) {
                vec2 q = rotate(pc.xz, iTime * 0.04 + 1.2 / (r * 0.01 + 1.0));
                float d1 = fbm(vec3(q * 0.008, iTime * 0.04));
                float d2 = fbm(vec3(q * 0.03 + 7.0, iTime * 0.09));
                float cov = smoothstep(0.28, 0.58, d1 * 0.8 + d2 * 0.3 + 0.1);
                float spread = smoothstep(spreadR, spreadR * 0.72, r);
                cloudA = cov * spread * (1.0 - inHole) * stormIn * release * 0.97 * exp(-tP / 9000.0);
                cloudCol = mix(vec3(0.035, 0.037, 0.045), vec3(0.15, 0.16, 0.19), d2);
                cloudCol += vec3(0.55, 0.62, 0.90) * flash * (2.2 * exp(-length(pc.xz - flashPos) / (140.0 * visualScale)) + 0.18);
                float rim = exp(-abs(dir.y + holeNoise - holeThr - 0.035) / 0.03) * step(0.001, holeP) * (1.0 - smoothstep(0.85, 1.0, holeP));
                cloudCol += mix(vec3(0.25, 0.55, 1.0), vec3(0.6, 0.25, 1.0), d2) * rim * 1.4;
                cloudA = max(cloudA, rim * 0.8 * stormIn * release * spread);
            }
        }
    }
    spaceMask *= release;
    float skyOpen = skyW * spaceMask;

    if (isSky) {
        col = mix(col, vec3(0.045, 0.05, 0.062), 0.85 * stormIn * release);
        col = mix(col, COLOR_SPACE_VOID, skyOpen * 0.98);

        col += vec3(0.55, 0.14, 0.03) * release * (1.0 - release) * 1.6 * float(exploded);
    } else {
        float under = smoothstep(spreadR, spreadR * 0.72, length(end_point.xz)) * stormIn;
        float spaceDark = smoothstep(0.10, 0.22, tau);
        float fogDark = smoothstep(40.0, 170.0, terrainDist) * 0.35 * spaceDark;
        col *= 1.0 - (0.5 * max(under, spaceDark) + fogDark) * release;
    }
    col += col * flash * 1.3 + vec3(0.45, 0.55, 0.85) * flash * 0.06;

    float cloudLife = smoothstep(0.0, 0.06, tau) * (1.0 - smoothstep(0.28, 0.44, tau));
    if (cloudLife > 0.01) {
        float tAxis = clamp(-dot(start_point.xz, dir.xz) / max(dot(dir.xz, dir.xz), 0.0001), 0.0, terrainDist);
        vec3 pAxis = start_point + dir * tAxis;
        float riseFront = mix(0.0, cloudH + 10.0, smoothstep(0.02, 0.2, tau));
        float groundLift = mix(-10.0, 220.0 * visualScale, smoothstep(0.12, 0.32, tau));

        if (pAxis.y >= groundLift && pAxis.y <= riseFront + 25.0) {
            float distAxis = length(pAxis.xz);
            float vortexR = (12.0 + pAxis.y * 0.45) * visualScale;
            float vortexAngle = atan(pAxis.z, pAxis.x);
            float upwardFlow = pAxis.y * 0.15 - iTime * 8.0;
            float spiral = 0.5 + 0.5 * sin(vortexAngle * 4.0 + upwardFlow);
            float puffs = 0.5 + 0.5 * cos(pAxis.y * 0.08 - iTime * 6.0 + vortexAngle * 2.0);
            float cloudVortex = smoothstep(vortexR + 15.0, vortexR * 0.5, distAxis)
                              * smoothstep(groundLift, groundLift + 18.0, pAxis.y)
                              * smoothstep(riseFront + 25.0, riseFront - 20.0, pAxis.y)
                              * (0.6 + 0.4 * spiral * puffs) * cloudLife;
            col = mix(col, vec3(0.012, 0.012, 0.020), cloudVortex * 0.94);
        }

        float rGround = length(end_point.xz);
        float hGround = end_point.y;
        float maxStormR = DestructionRange * 2.6;
        float groundFade = 1.0 - smoothstep(0.10, 0.24, tau);
        if (rGround < maxStormR && hGround > -15.0 && hGround < 35.0 && groundFade > 0.01) {
            vec2 swirlXZ = rotate(end_point.xz, tau * 8.0 - rGround * 0.12);
            float groundNoise = 0.5 + 0.5 * sin(swirlXZ.x * 0.35 + swirlXZ.y * 0.35 + iTime * 3.0);
            float groundMask = smoothstep(maxStormR, 2.0, rGround)
                             * smoothstep(-15.0, 2.0, hGround)
                             * smoothstep(35.0, 10.0, hGround)
                             * (0.6 + 0.4 * groundNoise) * cloudLife * groundFade;
            col = mix(col, vec3(0.010, 0.010, 0.016), groundMask * 0.88);
        }
    }

    float starFade = smoothstep(0.08, 0.20, tau) * (1.0 - smoothstep(0.0, tailLen * 0.7, blastTime));
    starFade *= skyOpen;
    if (starFade > 0.005 && skyW > 0.0) {
        float horizonFade = smoothstep(-0.05, 0.10, dir.y);
        col += renderStars(dir, iTime) * starFade * horizonFade;
    }

    vec3 toTarget = -start_point;
    float distXZ = length(toTarget.xz);
    vec3 forwardH = (distXZ > 0.5) ? vec3(toTarget.x / distXZ, 0.0, toTarget.z / distXZ) : vec3(0.0, 0.0, -1.0);
    vec3 celestialDir = normalize(forwardH * 0.18 + vec3(0.0, 0.98, 0.0));
    vec3 cTangX = normalize(cross(forwardH, celestialDir));
    vec3 cTangY = cross(celestialDir, cTangX);

    float solarSystemFade = smoothstep(0.10, 0.22, tau) * (1.0 - smoothstep(SUN_FALL_START, SUN_FALL_START + 0.08, tau)) * skyOpen;
    if (solarSystemFade > 0.01 && skyW > 0.0) {
        float facing = dot(dir, celestialDir);
        if (facing > 0.08) {
            vec2 cUV = vec2(dot(dir, cTangX), dot(dir, cTangY)) / max(facing, 0.08);

            float nebAngle = atan(cUV.y, cUV.x);
            float nebDist = length(cUV);
            float nebSwirl = sin(nebDist * 3.5 + nebAngle * 2.0 + iTime * 0.25);
            vec3 nebCol = mix(vec3(0.35, 0.10, 0.55), vec3(0.08, 0.42, 0.75), 0.5 + 0.5 * nebSwirl);
            col += nebCol * smoothstep(1.6, 0.12, nebDist) * 0.22 * solarSystemFade;

            float sunDist = length(cUV);
            float sunR = 0.095;
            if (tau < SUN_FALL_START) {
                if (sunDist < sunR) {
                    vec2 sOff = cUV / sunR;
                    float sZ = sqrt(max(0.0, 1.0 - dot(sOff, sOff)));
                    vec3 sN = vec3(sOff, sZ);
                    sN.xz = rotate(sN.xz, iTime * 0.08);
                    col = mix(col, sunSurface(sN, sZ, iTime), solarSystemFade);
                }
                float sunCorona = 0.016 / (sunDist * sunDist * 18.0 + 0.012);
                float flareAngle = atan(cUV.y, cUV.x);
                float flares = 1.0 + 0.25 * sin(flareAngle * 7.0 + iTime * 1.8) + 0.15 * cos(flareAngle * 4.0 - iTime * 2.4);
                col += vec3(1.0, 0.62, 0.18) * sunCorona * flares * 1.1 * solarSystemFade;
                float streak = 0.0032 / (cUV.y * cUV.y * 95.0 + abs(cUV.y) + 0.0035) * exp(-abs(cUV.x) * 2.0);
                col += vec3(0.18, 0.85, 1.0) * streak * 0.85 * solarSystemFade;
            }

            for (int k = 0; k < 8; k++) {
                float ringDist = abs(sunDist - ringAngularR[k]);
                float ringLine = smoothstep(0.005, 0.0, ringDist);
                float ringGlow = 0.0010 / (ringDist + 0.006);
                col += vec3(0.30, 0.72, 0.95) * (ringLine * 0.85 + ringGlow * 0.60) * solarSystemFade * 0.8;
            }

            for (int k = 0; k < 8; k++) {
                float tDrop = PLANET_DROP_START + float(k) * PLANET_DROP_STEP;
                if (tau >= tDrop) continue;
                float orbAngle = iTime * (0.60 / sqrt(float(k + 1))) + float(k) * 1.5708;
                vec2 pPos2D = vec2(cos(orbAngle), sin(orbAngle)) * ringAngularR[k];
                float pRadius = planetVisRadius[k];
                vec4 planetRender = renderPlanet3D(k, cUV, pPos2D, pRadius, iTime);
                col = mix(col, planetRender.rgb, planetRender.a * solarSystemFade);

                if (k == 5) {
                    vec2 ringLocal = rotate(cUV - pPos2D, 0.45);
                    float rR = length(vec2(ringLocal.x, ringLocal.y * 3.2)) / pRadius;
                    if (rR >= 1.25 && rR <= 2.65) {
                        float ringDensity = (rR > 1.95 && rR < 2.10) ? 0.08 : (rR > 1.55 && rR < 1.95 ? 1.0 : 0.65);
                        float ringShadow = (ringLocal.y < 0.0 && abs(ringLocal.x) < pRadius * 1.1) ? 0.25 : 1.0;
                        float ringLines = 0.88 + 0.12 * sin(rR * 60.0);
                        vec3 ringCol = vec3(0.94, 0.86, 0.60) * ringDensity * ringLines * ringShadow * 1.8;
                        col = mix(col, col + ringCol, solarSystemFade);
                    }
                }

                if (k == 2) {
                    vec2 moonPos = pPos2D + vec2(cos(iTime * 3.0), sin(iTime * 3.0)) * (pRadius * 2.8);
                    vec2 mOffset = (cUV - moonPos) / 0.012;
                    float mD2 = dot(mOffset, mOffset);
                    if (mD2 <= 1.0) {
                        vec3 mN = vec3(mOffset.x, mOffset.y, sqrt(max(0.0, 1.0 - mD2)));
                        vec3 L_sun = normalize(vec3(-moonPos.x, -moonPos.y, 0.35));
                        float mShadow = smoothstep(-0.1, 0.25, dot(mN, L_sun));
                        vec3 mCol = vec3(0.72, 0.72, 0.75) * (0.22 + 0.78 * mShadow) * 1.35;
                        col = mix(col, mCol, solarSystemFade);
                    }
                }
            }
        }
    }

    col = mix(col, cloudCol, cloudA);

    if (iTime < 1.8) {
        float ft = iTime;
        float beamI = exp(-ft * 2.0) * smoothstep(0.0, 0.04, ft + 0.02);
        float bw = mix(2.2, 0.6, ft / 1.8) * visualScale;
        float bd = rayToSegmentDist(start_point, dir, terrainDist, vec3(0.0), vec3(0.0, 900.0, 0.0));
        float core = smoothstep(bw, 0.0, bd);
        col += mix(COLOR_CYAN, COLOR_CYAN_HOT, core) * (core * 3.0 + (bw * bw * 2.0) / (bd * bd + bw * bw)) * beamI;
        float tcg;
        float dg = rayPointDist(start_point, dir, vec3(0.0, 0.5, 0.0), tcg);
        if (tcg < terrainDist + 4.0) col += COLOR_CYAN_HOT * (9.0 * visualScale * visualScale) / (dg * dg + 3.0 * visualScale * visualScale) * exp(-ft * 3.0);
        if (!isSky) {
            float rr = abs(length(end_point.xz) - ft * 34.0 * visualScale);
            col += COLOR_CYAN * (0.6 * visualScale) / (rr + 0.4) * exp(-ft * 2.2);
        }
        col += vec3(0.55, 0.85, 1.0) * 0.45 * exp(-ft * 7.0);
    }

    if (iTime < 3.2 && skyW > 0.0) {
        vec3 topDir = normalize(vec3(0.0, 600.0, 0.0) - start_point);
        float facingTop = dot(dir, topDir);
        if (facingTop > 0.2) {
            vec3 bx = normalize(cross(topDir, vec3(0.0001, 0.0, 1.0)));
            vec3 by = cross(topDir, bx);
            vec2 q = vec2(dot(dir, bx), dot(dir, by)) / facingTop;
            float r = length(q);
            float th = atan(q.y, q.x);
            float grow = smoothstep(0.05, 0.9, iTime) * 0.9;
            float life = 1.0 - smoothstep(1.8, 3.2, iTime);
            float crack = 0.0;
            for (int i = 0; i < 2; i++) {
                float n = (i == 0) ? 7.0 : 13.0;
                float jag = (fbm(vec3(r * 9.0, float(i) * 4.0, 1.0)) - 0.5) * (0.9 + float(i) * 0.6);
                float a = fract(th * n / 6.2832 + jag + float(i) * 0.37);
                float dist = abs(a - 0.5) * 6.2832 / n * r;
                float reach = grow * (i == 0 ? 1.0 : 0.55) * (0.6 + 0.4 * hash11(floor(th * n / 6.2832 + jag) + float(i) * 11.0));
                crack += smoothstep(0.004 + 0.003 * float(1 - i), 0.0, dist) * smoothstep(reach, reach * 0.8, r);
            }
            float core = exp(-r * 18.0) * smoothstep(0.0, 0.2, iTime);
            col += (vec3(0.6, 0.9, 1.0) * crack * 2.2 + vec3(0.8, 0.95, 1.0) * core * 1.5) * life * skyW;
        }
    }

    for (int k = 0; k < 8; k++) {
        float tStart = PLANET_DROP_START + float(k) * PLANET_DROP_STEP;
        float tEnd = tStart + PLANET_DROP_LEN;
        if (tau < tStart || tau > tEnd + 0.08) continue;

        float pProg = clamp((tau - tStart) / PLANET_DROP_LEN, 0.0, 1.0);
        float landed = clamp((tau - tEnd) / 0.03, 0.0, 1.0);

        float dropTime = tStart * totalDuration;
        float orbAngle = dropTime * (0.60 / sqrt(float(k + 1))) + float(k) * 1.5708;
        vec2 p2 = vec2(cos(orbAngle), sin(orbAngle)) * ringAngularR[k];
        float spawnDist = 220.0 * visualScale;
        vec3 skySpawn = start_point + normalize(celestialDir + cTangX * p2.x + cTangY * p2.y) * spawnDist;
        float pR = planetVisRadius[k] * spawnDist / (1.0 + dot(p2, p2));

        float ga = float(k) * 2.1 + 0.6;
        vec3 groundHit = vec3(cos(ga) * (3.0 + float(k) * 0.6), pR * 0.35, sin(ga) * (3.0 + float(k) * 0.6)) * vec3(visualScale, 1.0, visualScale);
        vec3 planetPos = mix(skySpawn, groundHit, pow(pProg, 2.2));
        planetPos.y -= landed * pR * 2.2;
        vec3 vel = normalize(groundHit - skySpawn);
        vec3 glowCol = getPlanetGlow(k);

        if (landed < 1.0) {
            float trailLen = (18.0 + 70.0 * pProg) * visualScale;
            vec3 tailTop = planetPos - vel * trailLen;
            float trailDist = rayToSegmentDist(start_point, dir, terrainDist, planetPos, tailTop);
            float trailWidth = pR * 0.75;
            float trailCore = smoothstep(trailWidth, 0.0, trailDist);
            float trailAura = (pR * 0.5) / (trailDist * trailDist * 0.05 + pR * 0.6);

            float tq = clamp(trailDist / max(trailWidth, 0.1), 0.0, 1.0);
            float tflick = 0.75 + 0.5 * fbm(vec3(trailDist * 0.4, iTime * 7.0, float(k)));
            vec3 trailCol = fireRamp((1.0 - tq) * 0.9 * tflick + 0.1);
            float burnIn = smoothstep(0.0, 0.12, pProg) * (1.0 - landed);
            col += trailCol * (trailCore * 1.4 + trailAura * 0.6) * burnIn;
            float smokeD = smoothstep(trailWidth * 2.6, trailWidth, trailDist) * (1.0 - trailCore);
            col = mix(col, vec3(0.05, 0.04, 0.04), smokeD * 0.35 * burnIn);
        }

        float tHit = raySphere(start_point, dir, planetPos, pR);
        if (tHit > 0.0 && tHit < terrainDist) {
            vec3 n = normalize(start_point + dir * tHit - planetPos);
            vec3 L = normalize(celestialDir * 0.45 - dir + cTangX * 0.35);
            vec3 pc = shadePlanet(k, n, planetLocal(k, n, iTime), L, -dir, iTime);

            float burnAmt = smoothstep(0.08, 0.45, pProg);
            float lead = max(dot(n, vel), 0.0);
            float fl = fbm(n * 5.0 + vel * iTime * 3.0 + float(k));
            float heat = clamp(pow(lead, 1.3) * (0.6 + 0.8 * fl), 0.0, 1.0) * burnAmt;
            pc = mix(pc, fireRamp(0.45 + 0.55 * heat), smoothstep(0.1, 0.8, heat));
            float rimGlow = pow(1.0 - max(dot(n, -dir), 0.0), 2.2);
            col = pc * 1.25 + mix(glowCol, vec3(1.0, 0.45, 0.08), burnAmt) * rimGlow * 1.3;
        } else {
            float tc2;
            float dp = rayPointDist(start_point, dir, planetPos, tc2);
            float occl = smoothstep(-pR, pR, terrainDist - tc2);
            float edge = max(dp - pR, 0.0);
            float burnAmt = smoothstep(0.08, 0.45, pProg) * (1.0 - landed);

            vec3 w = (start_point + dir * tc2) - planetPos;
            float back = dot(normalize(w + vec3(1e-4)), -vel);
            float thick = pR * (0.25 + 1.1 * smoothstep(-0.2, 1.0, back));
            float fAng = atan(dot(w, cross(dir, vel)), dot(w, vel));
            float tongues = fbm(vec3(fAng * 2.5, edge / pR * 2.0 - iTime * 5.0, float(k) * 3.1));
            float flame = smoothstep(thick * (0.5 + tongues), 0.0, edge) * burnAmt;
            col += fireRamp(clamp(1.0 - edge / max(thick, 0.01), 0.0, 1.0) * (0.6 + 0.5 * tongues)) * flame * 0.9 * occl;
            float halo = (pR * pR * 0.5) / (edge * edge + pR * pR * 0.25);
            col += mix(glowCol, vec3(1.0, 0.42, 0.08), burnAmt) * halo * 0.35 * (1.0 - landed) * occl * smoothstep(0.0, 0.05, pProg);
        }

        if (k == 5 && landed < 1.0) {
            vec3 ringN = normalize(vec3(0.25, 1.0, 0.15));
            float denom = dot(dir, ringN);
            if (abs(denom) > 1e-4) {
                float tr = dot(planetPos - start_point, ringN) / denom;
                if (tr > 0.0 && tr < terrainDist && (tHit < 0.0 || tr < tHit)) {
                    float rr = length(start_point + dir * tr - planetPos) / pR;
                    if (rr > 1.25 && rr < 2.4) {
                        float dens = (rr > 1.9 && rr < 2.02) ? 0.1 : (rr > 1.5 && rr < 1.9 ? 1.0 : 0.6);
                        col = mix(col, vec3(0.90, 0.82, 0.62) * (0.85 + 0.15 * sin(rr * 60.0)), 0.8 * dens);
                    }
                }
            }
        }

        if (tau > tEnd) {
            float impactAge = (tau - tEnd) * totalDuration;
            vec3 hitG = vec3(groundHit.x, 0.0, groundHit.z);
            float waveR = impactAge * 32.0 * visualScale;
            float rWave = abs(length(end_point.xz - hitG.xz) - waveR);
            float fade = exp(-impactAge * 1.4);
            if (!isSky) {
                col += mix(glowCol, vec3(1.0, 0.8, 0.5), 0.5) * (0.45 * visualScale) / (rWave + 0.4) * fade * 1.5;
                float behind = waveR - length(end_point.xz - hitG.xz);
                float dust = smoothstep(0.0, 1.5, behind) * smoothstep(8.0 * visualScale, 2.0, behind) * fade;
                col = mix(col, vec3(0.40, 0.31, 0.22), dust * 0.5);
            }

            float fbR = pR * 0.8;
            vec3 fbC = hitG + vec3(0.0, 1.0, 0.0);
            if (!isSky) {

                float gd = length(end_point.xz - hitG.xz);
                float flames = fbm(vec3(end_point.xz * 0.35, impactAge * 2.5 + float(k)));
                float burn = smoothstep(pR * 1.4, 0.0, gd) * exp(-impactAge * 0.8) * smoothstep(0.35, 0.7, flames);
                col = mix(col, fireRamp(0.55 + 0.4 * flames), burn * 0.8);
            }
            float tcF;
            float dF = rayPointDist(start_point, dir, fbC, tcF);
            float occl = smoothstep(-fbR, fbR, terrainDist - tcF);
            col += mix(COLOR_FIRE_CORE, glowCol, 0.35) * (pR * pR * 0.35) / (dF * dF + pR * pR * 0.12) * exp(-impactAge * 7.0) * 1.6 * occl;

            if (impactAge < 2.5) {
                float g = 20.0 * visualScale;
                for (int i = 0; i < 10; i++) {
                    float fi = float(i) + float(k) * 13.0;
                    float a = hash11(fi * 3.7 + 1.0) * 6.2832;
                    vec3 v = normalize(vec3(cos(a), 0.6 + 0.8 * hash11(fi * 7.1), sin(a))) * (12.0 + 22.0 * hash11(fi * 5.3)) * visualScale;
                    vec3 p0 = hitG + vec3(0.0, 1.0, 0.0) + v * impactAge - vec3(0.0, 0.5 * g * impactAge * impactAge, 0.0);
                    if (p0.y < -3.0) continue;
                    float t2 = max(impactAge - 0.06, 0.0);
                    vec3 p1 = hitG + vec3(0.0, 1.0, 0.0) + v * t2 - vec3(0.0, 0.5 * g * t2 * t2, 0.0);
                    float dd = rayToSegmentDist(start_point, dir, terrainDist, p0, p1);
                    float w = 0.28 * visualScale;
                    col += fireRamp(1.0 - impactAge / 2.5 * 0.8) * (w * w) / (dd * dd + w * w) * (1.0 - impactAge / 2.5);
                }
            }
        }
    }

    float sunDistStart = 260.0 * visualScale;
    float sunRw = 0.0947 * sunDistStart;
    vec3 sunGround = vec3(0.0, sunRw * 0.15, 0.0);
    if (tau >= SUN_FALL_START && !exploded) {
        vec3 sunStart = start_point + celestialDir * sunDistStart;
        float ease = pow(sunFall, 2.2);
        vec3 sunPos = mix(sunStart, sunGround, ease);
        vec3 vel = normalize(sunGround - sunStart);

        float near = smoothstep(0.0, 1.0, sunFall);
        if (isSky) {
            float toSun = max(dot(dir, normalize(sunPos - start_point)), 0.0);
            col += vec3(1.0, 0.40, 0.08) * (0.05 + 0.25 * pow(toSun, 6.0)) * near;
        } else {
            float dT = length(end_point - sunPos);
            col += vec3(1.0, 0.55, 0.2) * (sunRw * sunRw * 1.6) / (dT * dT + sunRw * sunRw * 0.5) * (0.15 + 0.85 * near) * 0.6;
            col = mix(col, col * vec3(1.15, 0.85, 0.65), near * 0.5);
        }

        float trailLen = (40.0 + 180.0 * ease) * visualScale;
        vec3 tailTop = sunPos - vel * trailLen;
        float trailDist = rayToSegmentDist(start_point, dir, terrainDist, sunPos, tailTop);
        float tw = sunRw * 0.9;
        float trailCore = smoothstep(tw, 0.0, trailDist);
        float flick = 0.8 + 0.2 * sin(iTime * 30.0 + trailDist * 0.3);
        col += mix(vec3(1.0, 0.8, 0.3), COLOR_FIRE_GLOW, clamp(trailDist / tw, 0.0, 1.0)) * (trailCore * 1.4 + (tw * 0.4) / (trailDist * trailDist * 0.02 + tw * 0.4) * 0.4) * flick * smoothstep(0.0, 0.1, sunFall);

        float tHit = raySphere(start_point, dir, sunPos, sunRw);
        if (tHit > 0.0 && tHit < terrainDist) {
            vec3 n = normalize(start_point + dir * tHit - sunPos);
            vec3 q = n;
            q.xz = rotate(q.xz, iTime * 0.08);
            vec3 sc = sunSurface(q, dot(n, -dir), iTime);
            float heat = pow(max(dot(n, vel), 0.0), 2.0) * sunFall;
            col = mix(sc, vec3(1.0, 0.97, 0.9) * 3.0, heat * 0.5);
        } else {
            float tcS;
            float dS = rayPointDist(start_point, dir, sunPos, tcS);
            if (tcS < terrainDist + sunRw) {

                vec3 w = (start_point + dir * tcS) - sunPos;
                vec3 bx = normalize(cross(dir, vec3(0.0, 1.0, 0.0001)));
                vec3 by = cross(dir, bx);
                float ang = atan(dot(w, by), dot(w, bx));
                float edge = max(dS - sunRw, 0.0) / sunRw;
                float flares = 1.0 + 0.45 * sin(ang * 9.0 + iTime * 3.0) * sin(ang * 5.0 - iTime * 2.0)
                                   + 0.3 * fbm(vec3(ang * 3.0, iTime * 0.8, 1.0));
                float corona = 0.30 / (edge * edge * 30.0 + edge * 6.0 + 0.3);
                col += vec3(1.0, 0.55, 0.15) * corona * flares * (1.0 + sunFall);
            }
        }

        if (!isSky) {
            float rG = length(end_point.xz);
            float ang = atan(end_point.z, end_point.x);
            float R0 = sunRw * (1.2 - 0.4 * sunFall);
            float ring = smoothstep(0.6, 0.0, abs(rG - R0)) * step(0.5, fract(ang * 4.0 / 3.14159 + iTime * 0.8));
            float ring2 = smoothstep(0.4, 0.0, abs(rG - R0 * 0.5));
            col += COLOR_CYAN * (ring * 1.6 + ring2 * 0.8) * (0.5 + 0.5 * sin(iTime * (8.0 + 30.0 * sunFall))) * step(abs(end_point.y), 12.0);
        }
    }

    if (exploded) {
        float bt = blastTime;

        float flash = exp(-bt * 2.4);
        col = mix(col, vec3(1.0, 0.92, 0.78), clamp(flash * 0.85, 0.0, 0.92));

        {
            float tcF;
            float dF = rayPointDist(start_point, dir, vec3(0.0, 2.0, 0.0), tcF);
            float occl = smoothstep(-sunRw, sunRw, terrainDist - tcF);
            float gR = sunRw * 0.5;
            col += vec3(1.0, 0.55, 0.15) * (gR * gR) / (dF * dF + gR * gR * 0.25) * exp(-bt * 2.2) * occl;
            if (!isSky) {
                float gd = length(end_point.xz);
                float ang = atan(end_point.z, end_point.x);
                float heatLife = 1.0 - smoothstep(tailLen * 0.5, tailLen - 0.5, bt);
                float flames = fbm(vec3(end_point.xz * 0.12, bt * 1.8));
                float burnR = DestructionRange * (1.2 + 0.6 * smoothstep(0.0, 2.0, bt));
                float burn = smoothstep(burnR, burnR * 0.4, gd) * smoothstep(0.4, 0.72, flames) * heatLife;
                col = mix(col, fireRamp(0.5 + 0.45 * flames * (0.6 + 0.4 * sin(bt * 7.0 + gd))), burn * 0.85);

                float crackLen = DestructionRange * (1.0 + 3.0 * smoothstep(0.0, 2.5, bt));
                float wob = (fbm(vec3(gd * 0.08, ang * 3.0, 3.0)) - 0.5) * 1.6;
                float crack = pow(1.0 - abs(sin(ang * 9.0 + wob)), 40.0) * smoothstep(crackLen, crackLen * 0.6, gd) * smoothstep(DestructionRange * 0.6, DestructionRange, gd);
                col += vec3(1.0, 0.38, 0.05) * crack * (1.4 + 0.6 * sin(bt * 5.0 + gd * 0.2)) * heatLife;

                float after = bt - 2.4;
                if (after > 0.0) {
                    float ar = abs(gd - after * shockSpeed * 0.7);
                    col += vec3(1.0, 0.45, 0.1) * (0.8 * visualScale) / (ar * ar * 0.3 + 0.6) * exp(-after * 0.9);
                }
            }
        }

        if (shockLife > 0.001) {
            vec3 shockCol = mix(vec3(1.0, 0.95, 0.85), mix(vec3(1.0, 0.55, 0.2), COLOR_CYAN, 0.35), clamp(bt / shockDur, 0.0, 1.0));
            col += shockCol * shellHit * 1.3;
            vec3 oc = start_point;
            float b = dot(oc, dir);
            float h = b * b - (dot(oc, oc) - shockRadius * shockRadius);
            if (h > 0.0) {
                h = sqrt(h);
                float t0 = -b - h;
                float t1 = -b + h;
                for (int j = 0; j < 2; j++) {
                    float ts = (j == 0) ? t0 : t1;
                    if (ts > 0.0 && ts < terrainDist) {
                        vec3 ps = start_point + dir * ts;
                        if (ps.y > -2.0) {
                            vec3 ns = ps / max(shockRadius, 0.001);
                            float fres = pow(1.0 - abs(dot(ns, dir)), 2.5);
                            float ripple = 0.75 + 0.25 * sin(ps.y * 0.6 - bt * 20.0);
                            col += shockCol * (0.12 + 0.9 * fres) * ripple * shockLife * smoothstep(-2.0, 6.0, ps.y) * 0.7;
                        }
                    }
                }
            }

            float endDist = length(end_point);
            float groundShell = abs(endDist - shockRadius);
            if (!isSky) {
                col += shockCol * (0.9 * visualScale) / (groundShell * groundShell * 0.4 + 0.6) * shockLife;
                float behind = shockRadius - length(end_point.xz);
                float dust = smoothstep(0.0, 2.0 * visualScale, behind) * smoothstep(14.0 * visualScale, 3.0 * visualScale, behind)
                           * (0.6 + 0.4 * fbm(vec3(end_point.xz * 0.15, bt * 2.0)));
                col = mix(col, vec3(0.42, 0.33, 0.24), dust * 0.55 * shockLife);
            }
        }

        float rGround = length(end_point.xz);
        float lenDirXZ = length(dir.xz);
        vec2 dXZ = (lenDirXZ > 0.001) ? dir.xz / lenDirXZ : vec2(1.0, 0.0);
        float tCyl = -dot(start_point.xz, dXZ);
        float speedScale = shockSpeed / 48.0;
        float lifeScale = shockDur / 3.0;
        for (int i = 0; i < 3; i++) {
            float tS = float(i) * 0.15;
            if (bt < tS) continue;
            float ringAge = bt - tS;
            float ringR = (ringAge * 42.0 + ringAge * ringAge * 8.0) * speedScale * visualScale;
            float dRing = abs(rGround - ringR);
            float laserCore = smoothstep(0.48 * visualScale, 0.0, dRing);
            float laserGlow = (0.24 * visualScale) / (dRing * dRing + 0.12);
            float ringLife = exp(-ringAge * 0.95 / lifeScale);
            vec3 laserCol = (i == 0) ? vec3(1.0, 0.9, 0.7) : ((i == 1) ? vec3(0.08, 0.94, 1.0) : vec3(1.0, 0.5, 0.15));
            float beam = 0.88 + 0.12 * sin(atan(end_point.z, end_point.x) * 28.0 + ringAge * 18.0);
            if (!isSky) col += laserCol * (laserCore * 2.0 + laserGlow * 1.0) * beam * ringLife;
        }

        if (bt < 4.0) {
            float g = 22.0 * visualScale;
            for (int i = 0; i < 36; i++) {
                float fi = float(i);
                float a = hash11(fi * 3.7 + 1.0) * 6.2832;
                float up = 0.35 + 0.9 * hash11(fi * 7.1 + 2.0);
                float spd = (18.0 + 40.0 * hash11(fi * 5.3 + 3.0)) * visualScale;
                vec3 v = normalize(vec3(cos(a), up, sin(a))) * spd;
                float life = 2.2 + 1.6 * hash11(fi * 1.9 + 4.0);
                if (bt > life) continue;
                vec3 p0 = vec3(0.0, 2.0, 0.0) + v * bt + vec3(0.0, -0.5 * g * bt * bt, 0.0);
                if (p0.y < -3.0) continue;
                float bt2 = max(bt - 0.07, 0.0);
                vec3 p1 = vec3(0.0, 2.0, 0.0) + v * bt2 + vec3(0.0, -0.5 * g * bt2 * bt2, 0.0);
                float dd = rayToSegmentDist(start_point, dir, terrainDist, p0, p1);
                float w = 0.35 * visualScale;
                float cool = bt / life;
                col += fireRamp(1.0 - cool * 0.8) * (w * w) / (dd * dd + w * w) * (1.0 - cool) * 1.2;
            }
        }

        float residualHeat = exp(-bt * 0.25) * smoothstep(DestructionRange * 2.0, 0.0, length(end_point)) * (0.8 + 0.2 * sin(bt * 3.0));
        if (!isSky) col += vec3(1.0, 0.35, 0.08) * residualHeat * 0.55;
        col *= mix(vec3(1.0), vec3(1.18, 0.95, 0.78), exp(-bt * 0.8) * 0.7);
    }

    if (exploded) {

        float on = blastTime / 3.5;
        if (on < 1.0) {
            float a = smoothstep(0.0, 0.02, on) * pow(1.0 - on, 1.35);
            float uvd = length(texCoord - 0.5);
            vec3 orange = mix(mix(vec3(1.0, 0.50, 0.06), vec3(1.0, 0.74, 0.22), 0.5 + 0.5 * sin(blastTime * 4.0)), vec3(0.95, 0.26, 0.02), uvd * 0.9);
            orange += vec3(0.35, 0.25, 0.10) * smoothstep(0.45, 0.0, blastTime) * 0.4;
            col = mix(col, col * vec3(1.2, 0.65, 0.22) + orange * 0.55, clamp(a * 0.85, 0.0, 0.9));
            col += vec3(1.0, 0.35, 0.05) * smoothstep(0.35, 0.75, uvd) * a * 0.35;
        }
    }

    if (exploded) {
        float emberLife = smoothstep(0.2, 1.0, blastTime) * (1.0 - smoothstep(tailLen - 3.0, tailLen - 0.4, blastTime));
        if (emberLife > 0.0) {
            for (int layer = 0; layer < 3; layer++) {
                float fl = float(layer);
                float sc = 14.0 + fl * 12.0;
                vec2 st = vec2(texCoord.x * sc * 1.8, texCoord.y * sc - blastTime * (0.9 + fl * 0.7));
                vec2 id = floor(st);
                vec2 f = fract(st) - 0.5;
                vec2 rnd = hash22(id + fl * 31.7);
                if (rnd.x > 0.72) {
                    vec2 pp = (rnd - 0.5) * 0.6 + vec2(sin(blastTime * 2.0 + rnd.y * 6.3) * 0.18, 0.0);
                    float d = length((f - pp) * vec2(1.0, 0.7));
                    float flick = 0.6 + 0.4 * sin(blastTime * (8.0 + rnd.y * 9.0) + rnd.x * 30.0);
                    col += mix(vec3(1.0, 0.35, 0.05), vec3(1.0, 0.8, 0.4), rnd.y) * smoothstep(0.07 + fl * 0.02, 0.0, d) * flick * emberLife * (0.7 + fl * 0.25);
                }
            }
        }
    }

    col = softTonemap(col);

    if (exploded) {
        float endFade = smoothstep(tailLen - 2.2, tailLen - 0.1, blastTime);
        col = mix(col, original, endFade);
    }

    float vig = 1.0 - shake * 0.12 * smoothstep(0.3, 0.9, length(texCoord - 0.5) * 1.4);
    fragColor = vec4(col * vig, 1.0);
}
