#version 150
// Separable 13-tap gaussian. BlurDir (pixels per tap) is set per pass in post/diviner_cosmos.json.

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 BlurDir;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 stp = BlurDir / InSize;
    vec3 sum = texture(DiffuseSampler, texCoord).rgb * 0.1964;
    float w[6] = float[](0.1748, 0.1210, 0.0661, 0.0280, 0.0093, 0.0024);
    for (int i = 0; i < 6; i++) {
        vec2 o = stp * float(i + 1) * 1.5;
        sum += (texture(DiffuseSampler, texCoord + o).rgb + texture(DiffuseSampler, texCoord - o).rgb) * w[i];
    }
    fragColor = vec4(sum / 1.0, 1.0);
}
