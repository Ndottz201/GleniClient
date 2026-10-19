package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.input.HandleInputEvent;
import glen.euphoria.event.impl.network.DisconnectEvent;
import glen.euphoria.event.impl.player.DoAttackEvent;
import glen.euphoria.event.impl.player.ItemUseEvent;
import glen.euphoria.utils.IMinecraft;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.event.impl.world.WorldChangeEvent;
import glen.euphoria.profiles.ProfileManager;
import glen.euphoria.utils.rotation.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin implements IMinecraft {

    @Shadow
    public ClientWorld world;
    @Shadow
    public HitResult crosshairTarget;
    @Shadow
    public ClientPlayerEntity player;
    @Shadow
    public ClientPlayerInteractionManager interactionManager;
    @Shadow
    @Final
    private RenderTickCounter.Dynamic renderTickCounter;

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    public void setTitle(CallbackInfoReturnable<String> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.mc == null) return;
        cir.setReturnValue("Euphoria 1.21.4");
    }

    @Inject(method = "run", at = @At("HEAD"))
    public void runInject(CallbackInfo ci) {
        if (EuphoriaClient.INSTANCE != null) {
            ProfileManager profileManager = EuphoriaClient.INSTANCE.getProfileManager();
            profileManager.loadProfile("default");
        }
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    public void euphoria$handleInputEvents(CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE != null) {
            HandleInputEvent event = new HandleInputEvent();
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.mc == null) return;

        if (world != null) {
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(new TickEvent());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.mc == null) return;
        if (player != null && world != null) {
            RotationController.apply(player);
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    public final void doAttackInject(CallbackInfoReturnable<Boolean> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        try {
            var antiMissOpt = EuphoriaClient.INSTANCE.getModuleManager().getModule(glen.euphoria.module.modules.combat.AntiMiss.class);
            if (antiMissOpt.isPresent() && antiMissOpt.get().isEnabled()) {
                if (crosshairTarget == null || crosshairTarget.getType() == HitResult.Type.MISS) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        DoAttackEvent event = new DoAttackEvent();
        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    public void stopInject(CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        // Do not auto-save to default profile on exit, per user settings
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    public void onWorldChangeInject(ClientWorld newWorld, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE != null && EuphoriaClient.mc != null) {
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(new WorldChangeEvent(newWorld));
        }
    }
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    public final void onDisconnected(CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        RotationController.reset();
        DisconnectEvent event = new DisconnectEvent();
        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
    }
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    public final void doItemUseInject(CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        ItemUseEvent event = new ItemUseEvent();

        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
