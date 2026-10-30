#version 150
// Shared vertex stage for the diviner's Blender meshes (client/mesh/CabinMesh).
// ModelMat places the mesh in its scene (space scene: planet frame; world: identity, camera-relative).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ModelMat;

out vec3 vWorld;
out vec3 vObj;
out vec3 vNormal;
out vec2 vUv;
out vec4 vColor;

void main() {
    vec4 world = ModelMat * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * world;
    vWorld = world.xyz;
    vObj = Position;
    vNormal = normalize(mat3(ModelMat) * Normal);
    vUv = UV0;
    vColor = Color;
}
