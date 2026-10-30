package net.austin.cabinmod.entity.client.physics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Read already-animated parts; no replacement player rig or copied walk animation. */
public final class PlayerRobePose {
    private PlayerRobePose() {}

    public static ClothSheet.PlayerPose of(PlayerModel<?> model, ClothSheet.PlayerFrame frame, boolean worldCollision) {
        var torso=transform(model.body);
        var head=transform(model.head);
        var right=transform(model.rightArm);
        var left=transform(model.leftArm);
        var foreR=forearm(right);
        var foreL=forearm(left);
        List<ClothSheet.BodyBox> boxes=new ArrayList<>(6);
        for(ModelPart part:List.of(model.body,model.head,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg)) {
            var bone=transform(part);
            part.visit(new PoseStack(),(pose,path,index,cube) -> boxes.add(new ClothSheet.BodyBox(bone,
                    new Vector3f(cube.minX,cube.minY,cube.minZ),new Vector3f(cube.maxX,cube.maxY,cube.maxZ))));
        }
        return new ClothSheet.PlayerPose(new ClothSheet.Pose(torso,head,right,left,foreR,foreL),boxes,frame,worldCollision,
                leg(model.rightLeg),leg(model.leftLeg));
    }

    public static ModelSpace.Transform transform(ModelPart part) {
        return new ModelSpace.Transform(new Vector3f(part.x,part.y,part.z),
                new Matrix3f().rotationZYX(part.zRot,part.yRot,part.xRot).scale(part.xScale,part.yScale,part.zScale));
    }

    private static ModelSpace.Transform forearm(ModelSpace.Transform arm) {
        return new ModelSpace.Transform(arm.apply(new Vector3f(0,4,0)),new Matrix3f(arm.rotation()));
    }

    private static ClothSheet.LegPose leg(ModelPart part) {
        var bind=part.getInitialPose();
        return new ClothSheet.LegPose(transform(part),new Vector3f(bind.x,bind.y,bind.z));
    }
}
