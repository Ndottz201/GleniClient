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

public final class PearlCatch extends Module {
    private final KeybindSetting pearlChargeKeybind = new KeybindSetting("Pearl Charge Key", GLFW.GLFW_KEY_H, true);
    private final NumberSetting windDelay = new NumberSetting("Wind Delay", 0, 2000, 200, 1);
    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 0, 500, 50, 10);

    private final TimerUtil pearlDelayTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();
    private boolean keyPressed = false;
    private boolean pearlThrown = false;
    private int originalSlot = -1;
    private boolean needsSwitchBack = false;

    public PearlCatch() {
        super("Pearl Catch", "Throws pearl then windcharge", -1, Category.MISC);
        this.addSettings(pearlChargeKeybind, windDelay, switchDelay);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(pearlChargeKeybind));
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull() || mc.currentScreen != null) return;

        boolean currentKeyState = KeyUtils.isKeyPressed(pearlChargeKeybind.getKeyCode());

        if (currentKeyState && !keyPressed) {
            throwPearl();
        }

        if (pearlThrown && pearlDelayTimer.hasElapsedTime(windDelay.getValueInt())) {
            throwWindCharge();
            pearlThrown = false;
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

        if (mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
            ((MinecraftClientAccessor) mc).invokeDoItemUse();
        }
        needsSwitchBack = true;
        switchTimer.reset();

        pearlThrown = true;
        pearlDelayTimer.reset();
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

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
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
        pearlThrown = false;
        originalSlot = -1;
        needsSwitchBack = false;
        pearlDelayTimer.reset();
        switchTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        pearlThrown = false;
        super.onDisable();
    }

    @Override
    public int getKey() {
        return -1;
    }
}