#version 150
// Bloom extraction: keep what is brighter than the threshold.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = max(c.r, max(c.g, c.b));
    fragColor = vec4(c * smoothstep(0.55, 1.0, l), 1.0);
}
