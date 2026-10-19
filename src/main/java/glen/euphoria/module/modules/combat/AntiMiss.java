package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.AttackEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.hit.HitResult;

public final class AntiMiss extends Module {
    public AntiMiss() {
        super("Anti Miss", "Prevents swinging when missing targets", -1, Category.COMBAT);
    }

    @EventHandler
    private void onAttackEvent(AttackEvent event) {
        if (isNull()) return;

        assert mc.crosshairTarget != null;
        if (mc.crosshairTarget.getType().equals(HitResult.Type.MISS)) {
            event.cancel();
        }
    }
}
