package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.input.HandleInputEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.KeybindSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.keybinding.KeyUtils;
import glen.euphoria.utils.math.TimerUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import glen.euphoria.utils.mc.ToolItems;
import org.lwjgl.glfw.GLFW;

public final class SwordHotSwap extends Module {

    private final KeybindSetting hotswapKey = new KeybindSetting("Hotswap Key", GLFW.GLFW_MOUSE_BUTTON_2, false);
    private final NumberSetting swapDelay = new NumberSetting("Swap Delay (MS)", 0, 1000, 150, 25);
    private final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);

    private final TimerUtil swapTimer = new TimerUtil();
    private boolean isSwapping = false;
    private boolean keyPressed = false;
    private int originalSlot = -1;

    public SwordHotSwap() {
        super("Sword Hot Swap", "Quickly swaps between sword and shield", -1, Category.COMBAT);
        this.addSettings(hotswapKey, swapDelay, switchBack);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(hotswapKey));
    }

    @EventHandler
    private void onTickEvent(HandleInputEvent event) {
        if (isNull() || mc.player == null) return;

        boolean currentKeyState = KeyUtils.isKeyPressed(hotswapKey.getKeyCode());

        if (currentKeyState && !keyPressed) {
            startSwap();
        }

        if (!currentKeyState && keyPressed) {
            if (switchBack.getValue() && originalSlot != -1 && mc.player.getInventory().getSelectedSlot() != originalSlot) {
                if (!SlotGuard.tryClaim()) return;
                mc.player.getInventory().setSelectedSlot(originalSlot);
                InventoryUtil.syncSelectedSlot();
            }
            originalSlot = -1;
            isSwapping = false;
        }

        keyPressed = currentKeyState;

        if (isSwapping && swapTimer.hasElapsedTime(swapDelay.getValueInt())) {
            if (performSwap()) {
                isSwapping = false;
            }
        }
    }

    private void startSwap() {
        ItemStack mainHand = mc.player.getMainHandStack();
        if (!isSword(mainHand)) return;

        if (!InventoryUtil.hasItem(Items.SHIELD)) return;

        originalSlot = mc.player.getInventory().getSelectedSlot();
        isSwapping = true;
        swapTimer.reset();
    }

    private boolean performSwap() {
        return InventoryUtil.swapToSlot(Items.SHIELD);
    }

    private boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return ToolItems.isSword(stack);
    }

    @Override
    public int getKey() {
        return -1;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        isSwapping = false;
        keyPressed = false;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (originalSlot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            if (SlotGuard.tryClaim()) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                InventoryUtil.syncSelectedSlot();
            }
        }
        isSwapping = false;
        keyPressed = false;
        originalSlot = -1;
    }
}
