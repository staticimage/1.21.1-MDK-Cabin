#version 150
// Blender-rendered flipbook frame, drawn additively and tinted by the vertex colour.

uniform sampler2D Sampler0;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec4 t = texture(Sampler0, vUv);
    vec3 col = t.rgb * t.a * vColor.rgb * vColor.a * 1.6;
    if (dot(col, vec3(1.0)) < 0.002) discard;
    fragColor = vec4(col, 0.0);
}
