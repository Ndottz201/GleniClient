package glen.euphoria.module.modules.misc;

import glen.euphoria.event.impl.input.HandleInputEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.KeybindSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.keybinding.KeyUtils;
import glen.euphoria.utils.math.TimerUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

public final class WindChargeKey extends Module {
    private final KeybindSetting windChargeKeybind = new KeybindSetting("Wind Charge Key", GLFW.GLFW_KEY_G, true);
    private final NumberSetting throwDelay = new NumberSetting("Throw Delay", 50, 1000, 200, 25);
    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 0, 500, 50, 10);
    private final TimerUtil throwTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();
    private boolean keyPressed = false;
    private int originalSlot = -1;
    private boolean needsSwitchBack = false;

    public WindChargeKey() {
        super("Wind Charge Key", "Throws wind charge on hotkey", -1, Category.COMBAT);
        this.addSettings(windChargeKeybind, throwDelay, switchDelay);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(windChargeKeybind));
    }

    @EventHandler
    private void onTickEvent(HandleInputEvent event) {
        if (isNull() || mc.currentScreen != null) return;

        boolean currentKeyState = KeyUtils.isKeyPressed(windChargeKeybind.getKeyCode());

        if (currentKeyState && !keyPressed && throwTimer.hasElapsedTime(throwDelay.getValueInt(), false)) {
            throwWindCharge();
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

    private void throwWindCharge() {
        if (!InventoryUtil.hasItem(Items.WIND_CHARGE)) return;

        if (mc.player.getItemCooldownManager().isCoolingDown(new net.minecraft.item.ItemStack(Items.WIND_CHARGE))) {
            return;
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        if (!InventoryUtil.swapToSlot(Items.WIND_CHARGE)) return;

        if (mc.player.getMainHandStack().isOf(Items.WIND_CHARGE)) {
            ((MinecraftClientAccessor) mc).invokeDoItemUse();
        }
        needsSwitchBack = true;
        switchTimer.reset();
    }

    private int findWindChargeSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.WIND_CHARGE) {
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