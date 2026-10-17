package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.input.HandleInputEvent;
import glen.euphoria.event.impl.network.DisconnectEvent;
import glen.euphoria.event.impl.player.DoAttackEvent;
import glen.euphoria.event.impl.player.ItemUseEvent;
import glen.euphoria.utils.IMinecraft;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.event.impl.world.WorldChangeEvent;
import glen.euphoria.gui.ClickGui;
import glen.euphoria.module.modules.client.ClickGUIModule;
import glen.euphoria.module.modules.client.Client;
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
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.mc == null) return;

        var optionalClientModule = EuphoriaClient.INSTANCE.getModuleManager().getModule(Client.class);
        if (optionalClientModule.isPresent()) {
            Client client = optionalClientModule.get();
            if (client.isEnabled() && client.getTitle()) {
                cir.setReturnValue("Euphoria 1.21.4");
            }
        }
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
        if (EuphoriaClient.INSTANCE != null) {
            HandleInputEvent event = new HandleInputEvent();
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.mc == null) return;

        if (world != null) {
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(new TickEvent());
        }

        var optionalClickGuiModule = EuphoriaClient.INSTANCE.getModuleManager().getModule(ClickGUIModule.class);
        if (optionalClickGuiModule.isPresent()) {
            ClickGUIModule clickGuiModule = optionalClickGuiModule.get();
            if (clickGuiModule.isEnabled() && EuphoriaClient.mc.currentScreen == null && world != null) {
                EuphoriaClient.mc.setScreen(new ClickGui());
            }
            else if (!clickGuiModule.isEnabled() && EuphoriaClient.mc.currentScreen instanceof ClickGui) {
                EuphoriaClient.mc.setScreen(null);
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.mc == null) return;
        if (player != null && world != null) {
            RotationController.apply(player);
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    public final void doAttackInject(CallbackInfoReturnable<Boolean> cir) {
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
        if (EuphoriaClient.INSTANCE != null) {
            ProfileManager profileManager = EuphoriaClient.INSTANCE.getProfileManager();
            profileManager.saveProfile("default", true);
        }
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    public void onWorldChangeInject(ClientWorld newWorld, CallbackInfo ci) {
        if (EuphoriaClient.INSTANCE != null && EuphoriaClient.mc != null) {
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(new WorldChangeEvent(newWorld));
        }
    }
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    public final void onDisconnected(CallbackInfo ci) {
        RotationController.reset();
        DisconnectEvent event = new DisconnectEvent();
        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
    }
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    public final void doItemUseInject(CallbackInfo ci) {
        ItemUseEvent event = new ItemUseEvent();

        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
