package glen.euphoria.module.modules.combat;


import glen.euphoria.event.impl.network.PacketEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.mc.ChatUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

public final class Velocity extends Module {
    public static final NumberSetting chance = new NumberSetting("Chance (%)", 1, 100, 100, 1);
    public static final BooleanSetting ignoreWhenBackwards = new BooleanSetting("Ignore S press", true);
    public static final BooleanSetting ignoreOnFire = new BooleanSetting("Ignore on fire", true);

    public Velocity() {
        super("Velocity", "Reduces knockback received from attacks", -1, Category.COMBAT);
        this.addSettings(chance, ignoreWhenBackwards, ignoreOnFire);
    }

    private boolean receivedKnockback = false;

    @Override
    public void onDisable() {
        receivedKnockback = false;
    }

    @EventHandler
    private void onPacketEvent(PacketEvent event) {
        if (isNull()) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet && packet.getEntityId() == mc.player.getId()) {
            net.minecraft.util.math.Vec3d vel = packet.getVelocity();
            if (vel != null && (vel.x != 0 || vel.y > 0 || vel.z != 0)) {
                receivedKnockback = true;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull()) return;

        if (receivedKnockback) {
            receivedKnockback = false;
            if (mc.player.isOnGround()) {
                if (ignoreWhenBackwards.getValue() && mc.options.backKey.isPressed()) return;
                if (ignoreOnFire.getValue() && mc.player.isOnFire()) return;
                if (mc.currentScreen != null) return;
                if (chanceCheck()) {
                    mc.player.jump();
                }
            }
        }
    }

    private boolean chanceCheck() {
        return (Math.random() * 100 < chance.getValueFloat());
    }
}
