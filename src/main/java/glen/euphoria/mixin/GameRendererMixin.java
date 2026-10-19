package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.render.Render3DEvent;
import glen.euphoria.utils.render.W2SUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    // RenderSystem.getProjectionMatrix() was removed in 1.21.5; capture the matrix
    // straight from getBasicProjectionMatrix's return value instead.
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"))
    private void captureProjection(CallbackInfoReturnable<Matrix4f> cir) {
        W2SUtil.matrixProject.set(cir.getReturnValue());
    }

    // In 1.21.5+ GameRenderer.renderHand became a method (renderHand(F,Z,Matrix4f)) instead
    // of a boolean field, so the old FIELD GETFIELD anchor no longer resolves. Anchor the
    // Render3DEvent right before the (single) renderHand(...) call in renderWorld instead.
    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V"))
    private void renderHand(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        MatrixStack matrixStack = new MatrixStack();

        RenderSystem.getModelViewStack().pushMatrix().mul(matrixStack.peek().getPositionMatrix());
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));

        W2SUtil.matrixModel.set(RenderSystem.getModelViewMatrix());
        W2SUtil.matrixWorldSpace.set(matrixStack.peek().getPositionMatrix());

        Camera blockCamera = mc.gameRenderer.getCamera();
        if (blockCamera != null) {
            matrixStack.push();
            Vec3d vec3d = blockCamera.getCameraPos();
            matrixStack.translate(-vec3d.x, -vec3d.y, -vec3d.z);
        }

        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(new Render3DEvent(matrixStack));

        RenderSystem.getModelViewStack().popMatrix();
    }
}
