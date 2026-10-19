package glen.euphoria.module.modules.player;


import glen.euphoria.event.impl.input.HandleInputEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.keybinding.KeyUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

public final class FastEXP extends Module {

    private static final NumberSetting chance = new NumberSetting("Chance %", 1, 100, 75, 1);

    public FastEXP() {
        super("Fast Exp", "Throws experience bottles much faster", -1, Category.COMBAT);
        this.addSettings(chance);
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull() || mc.currentScreen != null) return;

        ItemStack heldItem = mc.player.getMainHandStack();
        if (heldItem.isEmpty() || heldItem.getItem() != Items.EXPERIENCE_BOTTLE) return;

        boolean wasRightClicking = mc.options.useKey.isPressed() || KeyUtils.isKeyPressed(GLFW.GLFW_MOUSE_BUTTON_2);
        if (!wasRightClicking) return;

        MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
        if (accessor.getItemUseCooldown() > 0) {
            if (chance.getValue() >= Math.random() * 100) {
                accessor.setItemUseCooldown(0);
            }
        }
    }
} 