package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.modules.misc.Impersonation;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Team.class)
public abstract class TeamMixin {

    @Inject(method = "decorateName", at = @At("RETURN"), cancellable = true)
    private void onDecorateName(Text name, CallbackInfoReturnable<Text> cir) {
        if (EuphoriaClient.INSTANCE == null) return;
        if (EuphoriaClient.INSTANCE.getModuleManager() == null) return;

        try {
            Optional<Impersonation> optional = EuphoriaClient.INSTANCE.getModuleManager().getModule(Impersonation.class);
            if (optional.isEmpty()) return;
            
            Impersonation module = optional.get();
            if (!module.isEnabled()) return;

            String playerName = name.getString();

            Text overriddenPrefix = module.getOverriddenTeamPrefix(playerName, null);
            Text overriddenSuffix = module.getOverriddenTeamSuffix(playerName, null);

            if (overriddenPrefix != null || overriddenSuffix != null) {
                for (var entry : module.getActiveImpersonations().entrySet()) {
                    Impersonation.ImpersonationData data = entry.getValue();
                    if (data.targetName.equalsIgnoreCase(playerName) && data.dataLoaded) {
                        Text displayName = Text.literal(data.impersonateName);
                        Text prefix = overriddenPrefix != null ? overriddenPrefix : Text.empty();
                        Text suffix = overriddenSuffix != null ? overriddenSuffix : Text.empty();

                        Text result = Text.empty()
                                .append(prefix)
                                .append(displayName)
                                .append(suffix);

                        cir.setReturnValue(result);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}