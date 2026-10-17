package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.AttackEvent;
import glen.euphoria.mixin.ClientPlayerEntityAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.ModeSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class Criticals extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Vanilla", "Vanilla");

    public Criticals() {
        super("Criticals", "Makes you hit every crit (BLATANT)", -1, Category.COMBAT);
        this.addSetting(mode);
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (isNull()) return;
        ClientPlayerEntity p = mc.player;
        if (!(e.getTarget() instanceof LivingEntity)) return;

        boolean willCritLegit = p.fallDistance > 0.0F && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle();
        if (willCritLegit) return;

        if (!p.isOnGround() || p.isClimbing() || p.isTouchingWater()) return;
        boolean col = p.horizontalCollision;
        double x = p.getX(), y = p.getY(), z = p.getZ();

        // up 0.0625 airborne, then back to y airborne -> server: fallDistance=0.0625, onGround=false, position unchanged
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, col));
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, col));

        // keep vanilla from emitting a conflicting packet this tick
        ((ClientPlayerEntityAccessor) p).setLastOnGround(false);
    }
}
