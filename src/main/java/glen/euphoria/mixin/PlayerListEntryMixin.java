package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.modules.misc.Impersonation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null) return;
        if (EuphoriaClient.INSTANCE.getModuleManager() == null) return;

        try {
            Optional<Impersonation> optional = EuphoriaClient.INSTANCE.getModuleManager().getModule(Impersonation.class);
            if (optional.isEmpty()) return;
            
            Impersonation module = optional.get();
            if (!module.isEnabled()) return;

            UUID uuid = this.profile.id();
            Text overridden = module.getOverriddenTabName(uuid, cir.getReturnValue());
            if (overridden != cir.getReturnValue()) {
                cir.setReturnValue(overridden);
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null) return;
        if (EuphoriaClient.INSTANCE.getModuleManager() == null) return;

        try {
            Optional<Impersonation> optional = EuphoriaClient.INSTANCE.getModuleManager().getModule(Impersonation.class);
            if (optional.isEmpty()) return;
            
            Impersonation module = optional.get();
            if (!module.isEnabled()) return;

            UUID uuid = this.profile.id();
            SkinTextures overridden = module.getOverriddenSkinTextures(uuid, cir.getReturnValue());
            if (overridden != null && overridden != cir.getReturnValue()) {
                cir.setReturnValue(overridden);
            }
        } catch (Exception ignored) {}
    }
}