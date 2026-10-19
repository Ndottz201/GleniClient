package glen.euphoria.module.modules.combat;

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
import glen.euphoria.utils.rotation.RotationController;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.entry.RegistryEntry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ThrowPot extends Module {
    private static final KeybindSetting throwKey = new KeybindSetting("Throw Key", GLFW.GLFW_KEY_G, false);
    private static final NumberSetting throwDelay = new NumberSetting("Throw Delay", 50, 1000, 250, 50);
    private static final NumberSetting healthThreshold = new NumberSetting("Health Threshold", 1, 20, 10, 0.5);
    private static final BooleanSetting multiThrow = new BooleanSetting("Multi Throw", true);
    private static final NumberSetting potDelay = new NumberSetting("Pot Delay", 50, 500, 150, 25);
    private static final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch Back", true);
    private static final BooleanSetting lookDown = new BooleanSetting("Look Down", true);

    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil potTimer = new TimerUtil();
    private final List<Integer> potionSlots = new ArrayList<>();
    private int originalSlot = -1;
    private float originalPitch = 0;
    private boolean keyPressed = false;
    private boolean isThrowing = false;
    private int potsToThrow = 0;
    private int potsThrown = 0;

    public ThrowPot() {
        super("Throw Pot", "Throws health potions on keybind", -1, Category.COMBAT);
        this.addSettings(throwKey, throwDelay, healthThreshold, multiThrow, potDelay, autoSwitch, lookDown);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(throwKey));
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull()) return;

        boolean currentKeyState = KeyUtils.isKeyPressed(throwKey.getKeyCode());

        if (currentKeyState && !keyPressed && !isThrowing) {
            if (timer.hasElapsedTime(throwDelay.getValueInt()) && mc.player.getHealth() <= healthThreshold.getValueFloat()) {
                startThrow();
                timer.reset();
            }
        }

        keyPressed = currentKeyState;

        if (isThrowing && potTimer.hasElapsedTime(potDelay.getValueInt()) && potsThrown < potsToThrow) {
            throwNextPotion();
        }

        if (isThrowing && potsThrown >= potsToThrow && timer.hasElapsedTime(100)) {
            finishThrow();
        }
    }

    private void startThrow() {
        findPotionSlots();
        if (potionSlots.isEmpty()) return;

        if (originalSlot == -1) originalSlot = mc.player.getInventory().getSelectedSlot();
        if (lookDown.getValue()) {
            originalPitch = mc.player.getPitch();
            RotationController.request(mc.player.getYaw(), 89.9f, 10);
        }

        potsToThrow = multiThrow.getValue() ? Math.min(3, potionSlots.size()) : 1;
        potsThrown = 0;
        isThrowing = true;
        potTimer.reset();
    }

    private void throwNextPotion() {
        int slot = potionSlots.get(potsThrown % potionSlots.size());
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            if (!SlotGuard.tryClaim()) return;
            mc.player.getInventory().setSelectedSlot(slot);
            InventoryUtil.syncSelectedSlot();
        }
        ((MinecraftClientAccessor) mc).invokeDoItemUse();
        potsThrown++;
        potTimer.reset();
    }

    private void finishThrow() {
        if (autoSwitch.getValue() && originalSlot != -1 && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            if (!SlotGuard.tryClaim()) return;
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
        }
        if (lookDown.getValue()) {
            RotationController.request(mc.player.getYaw(), originalPitch, 10);
        }
        isThrowing = false;
        potsToThrow = 0;
        potsThrown = 0;
    }

    private void findPotionSlots() {
        potionSlots.clear();
        for (int i = 0; i < 9; i++) {
            if (isHealthPotion(mc.player.getInventory().getStack(i))) {
                potionSlots.add(i);
            }
        }
    }

    private boolean isHealthPotion(ItemStack stack) {
        if (stack.getItem() != Items.SPLASH_POTION) return false;
        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        if (potionContents.potion().isPresent()) {
            RegistryEntry<Potion> potionEntry = potionContents.potion().get();
            return potionEntry.value().getEffects().stream()
                    .anyMatch(effect -> effect.getEffectType().equals(StatusEffects.INSTANT_HEALTH));
        }

        return potionContents.customEffects().stream()
                .anyMatch(effect -> effect.getEffectType().equals(StatusEffects.INSTANT_HEALTH));
    }

    @Override
    public void onEnable() {
        keyPressed = false;
        originalSlot = -1;
        originalPitch = 0;
        isThrowing = false;
        potsToThrow = 0;
        potsThrown = 0;
        potionSlots.clear();
        timer.reset();
        potTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (autoSwitch.getValue() && originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        if (lookDown.getValue()) {
            RotationController.request(mc.player.getYaw(), originalPitch, 10);
        }
        potionSlots.clear();
        originalSlot = -1;
        originalPitch = 0;
        keyPressed = false;
        isThrowing = false;
        potsToThrow = 0;
        potsThrown = 0;
        super.onDisable();
    }

    @Override
    public int getKey() {
        return -1;
    }
}