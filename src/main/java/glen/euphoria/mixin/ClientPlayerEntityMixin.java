package glen.euphoria.mixin;

import glen.euphoria.utils.rotation.RotationUtils;
import glen.euphoria.utils.rotation.SilentRotation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Unique
    private float euphoria$prevPitch;
    @Unique
    private float euphoria$prevYaw;
    @Unique
    private boolean euphoria$wasSilentActive;
    @Unique
    private Runnable euphoria$pendingPostAction;

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void euphoria$onSendMovementPacketsHead(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (SilentRotation.isActive()) {
            euphoria$prevPitch = player.getPitch();
            euphoria$prevYaw = player.getYaw();
            euphoria$wasSilentActive = true;
            euphoria$pendingPostAction = SilentRotation.getPostAction();

            float gcd = RotationUtils.mouseGcd();
            Float targetPitch = SilentRotation.getPitch();
            Float targetYaw = SilentRotation.getYaw();

            if (targetPitch != null) {
                float snappedPitch = MathHelper.clamp(
                        RotationUtils.snap(targetPitch, player.getPitch(), gcd),
                        -89.9f, 89.9f
                );
                player.setPitch(snappedPitch);
            }
            if (targetYaw != null) {
                float snappedYaw = RotationUtils.snapYaw(targetYaw, player.getYaw(), gcd);
                player.setYaw(snappedYaw);
            }
        } else {
            euphoria$wasSilentActive = false;
            euphoria$pendingPostAction = null;
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void euphoria$onSendMovementPacketsTail(CallbackInfo ci) {
        if (euphoria$wasSilentActive) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            player.setPitch(euphoria$prevPitch);
            player.setYaw(euphoria$prevYaw);
            euphoria$wasSilentActive = false;

            Runnable action = euphoria$pendingPostAction;
            euphoria$pendingPostAction = null;
            SilentRotation.clear();

            if (action != null) {
                try {
                    action.run();
                } catch (Exception ignored) {}
            }
        }
    }
}
