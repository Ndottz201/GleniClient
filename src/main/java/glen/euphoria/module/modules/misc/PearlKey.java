package glen.euphoria.module.modules.misc;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.KeybindSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.keybinding.KeyUtils;
import glen.euphoria.utils.math.TimerUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public final class PearlKey extends Module {
    private final KeybindSetting pearlKeybind = new KeybindSetting("Pearl Key", GLFW.GLFW_KEY_P, true);
    private final NumberSetting throwDelay = new NumberSetting("Throw Delay", 100, 5000, 1000, 50);
    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 0, 500, 50, 10);
    private final TimerUtil throwTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();
    private boolean keyPressed = false;
    private int originalSlot = -1;
    private boolean needsSwitchBack = false;

    public PearlKey() {
        super("Pearl Key", "Automatically throws ender pearls when hotkey is pressed", -1, Category.MISC);
        this.addSettings(pearlKeybind, throwDelay, switchDelay);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(pearlKeybind));
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull() || mc.currentScreen != null) return;

        boolean currentKeyState = KeyUtils.isKeyPressed(pearlKeybind.getKeyCode());

        if (currentKeyState && !keyPressed && throwTimer.hasElapsedTime(throwDelay.getValueInt(), false)) {
            throwPearl();
            throwTimer.reset();
        }

        if (needsSwitchBack && switchTimer.hasElapsedTime(switchDelay.getValueInt())) {
            if (originalSlot != -1 && mc.player.getInventory().getSelectedSlot() != originalSlot) {
                if (!SlotGuard.tryClaim()) return;
                mc.player.getInventory().setSelectedSlot(originalSlot);
                InventoryUtil.syncSelectedSlot();
            }
            needsSwitchBack = false;
            originalSlot = -1;
        }

        keyPressed = currentKeyState;
    }

    private void throwPearl() {
        if (!InventoryUtil.hasItem(Items.ENDER_PEARL)) return;

        if (mc.player.getItemCooldownManager().isCoolingDown(new net.minecraft.item.ItemStack(Items.ENDER_PEARL))) {
            return;
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        if (!InventoryUtil.swapToSlot(Items.ENDER_PEARL)) return;

        if (mc.interactionManager != null && mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
            ((MinecraftClientAccessor)mc).invokeDoItemUse();
        }

        needsSwitchBack = true;
        switchTimer.reset();
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onEnable() {
        keyPressed = false;
        originalSlot = -1;
        needsSwitchBack = false;
        throwTimer.reset();
        switchTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    public int getKey() {
        return -1;
    }
}