package glen.euphoria.module.modules.misc;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class Teams extends Module {

    public Teams() {
        super("Teams", "Prevents targeting players on your team", Category.MISC);
    }

    public static boolean isTeammate(Entity entity) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return false;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) return false;
        var optional = EuphoriaClient.INSTANCE.getModuleManager().getModule(Teams.class);
        if (optional.isEmpty() || !optional.get().isEnabled()) {
            return false;
        }

        if (entity == null || entity.getName() == null || !(entity instanceof LivingEntity)) {
            return false;
        }

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.player.getScoreboardTeam() == null) {
                return false;
            }

            return mc.player.isTeammate(entity);
        } catch (IllegalStateException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
