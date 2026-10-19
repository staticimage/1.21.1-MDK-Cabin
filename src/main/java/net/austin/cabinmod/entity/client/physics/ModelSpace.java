package net.austin.cabinmod.entity.client.physics;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Vector3f;

// Forward kinematics for ModelParts in model space (pixels, Y down), matching ModelPart.translateAndRotate:
// translate(x, y, z), rotate ZYX(zRot, yRot, xRot), scale.
public final class ModelSpace {
    private ModelSpace() {
    }

    public record Transform(Vector3f origin, Matrix3f rotation) {
        public Vector3f apply(Vector3f local) {
            return new Matrix3f(rotation).transform(local).add(origin);
        }
    }

    public static Matrix3f rotationOf(ModelPart part) {
        return new Matrix3f().rotationZYX(part.zRot, part.yRot, part.xRot);
    }

    // Transform of the last part in `chain` (root first), e.g. of(root, torso).
    public static Transform of(ModelPart... chain) {
        Vector3f origin = new Vector3f();
        Matrix3f rot = new Matrix3f();
        for (ModelPart part : chain) {
            origin.add(new Matrix3f(rot).transform(new Vector3f(part.x, part.y, part.z)));
            rot.mul(rotationOf(part));
        }
        return new Transform(origin, rot);
    }
}
