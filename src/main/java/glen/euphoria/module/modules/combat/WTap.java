package glen.euphoria.module.modules.combat;


import glen.euphoria.event.impl.player.AttackEvent;
import glen.euphoria.event.impl.player.DoAttackEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.keybinding.KeyUtils;
import glen.euphoria.utils.math.TimerUtil;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;

public class WTap extends Module {
    public static final NumberSetting chance = new NumberSetting("Chance (%)", 1, 100, 100, 1);
    private final NumberSetting msDelay = new NumberSetting("Ms", 1, 500, 60, 1);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Only on ground", true);
    private boolean tapping = false;
    private int tapTicks = 0;
    private final TimerUtil timer = new TimerUtil();

    public WTap() {
        super("W Tap", "Automatically W-taps after hitting targets", -1, Category.COMBAT);
        this.addSettings(msDelay, chance, onlyOnGround);
    }

    @Override
    public void onDisable() {
        if (tapping && mc.options != null) {
            mc.options.forwardKey.setPressed(KeyUtils.isKeyPressed(GLFW.GLFW_KEY_W));
        }
        tapping = false;
        tapTicks = 0;
    }

    @EventHandler
    private void onAttackEvent(DoAttackEvent event) {
        if (isNull()) return;
        if (tapping) return;

        if (Math.random() * 100 > chance.getValueFloat()) return;
        var target = mc.targetedEntity;
        if (!mc.player.isOnGround() && onlyOnGround.getValue()) return;
        if (target == null || !target.isAlive()) return;
        if (!KeyUtils.isKeyPressed(GLFW.GLFW_KEY_W)) return;
        if (mc.player.isSprinting()) {
            tapping = true;
            tapTicks = 0;
            timer.reset();
            mc.options.forwardKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull()) return;

        if (tapping) {
            tapTicks++;
            if (tapTicks >= 1 && timer.hasElapsedTime(msDelay.getValueInt(), false)) {
                mc.options.forwardKey.setPressed(KeyUtils.isKeyPressed(GLFW.GLFW_KEY_W));
                tapping = false;
                tapTicks = 0;
            }
        }
    }
}
