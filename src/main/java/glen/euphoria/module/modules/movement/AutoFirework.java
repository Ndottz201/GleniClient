package glen.euphoria.module.modules.movement;

import glen.euphoria.event.impl.input.HandleInputEvent;
import glen.euphoria.event.impl.player.ItemUseEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.math.TimerUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class AutoFirework extends Module {
    private final BooleanSetting onlyWhenFlying = new BooleanSetting("Only When Flying", true);
    private final BooleanSetting respectGapples = new BooleanSetting("Respect Gapples", true);
    private final BooleanSetting respectArmor = new BooleanSetting("Respect Armor", true);
    private final BooleanSetting autoSwitchBack = new BooleanSetting("Auto Switch Back", true);
    private final NumberSetting switchBackDelay = new NumberSetting("Switch Back Delay", 25, 1000, 100, 25);

    private final TimerUtil switchBackTimer = new TimerUtil();
    private boolean wasRightClickPressed = false;
    private boolean pendingSwitchBack = false;
    private int originalSlot = -1;
    private long lastFireworkTime = 0;
    private boolean shouldCancelNextUse = false;

    public AutoFirework() {
        super("Auto Firework", "Uses fireworks automatically while elytra flying", -1, Category.MOVEMENT);
        this.addSettings(onlyWhenFlying, respectGapples, respectArmor, autoSwitchBack, switchBackDelay);
    }

    @EventHandler
    private void onItemUse(ItemUseEvent event) {
        if (shouldCancelNextUse) {
            event.cancel();
            shouldCancelNextUse = false;
        }
    }

    @EventHandler
    private void onTickEvent(HandleInputEvent event) {
        if (isNull())
            return;
        if (mc.currentScreen != null)
            return;

        boolean currentRightClick = mc.options.useKey.isPressed();

        if (!wasRightClickPressed && currentRightClick && shouldUseFirework() && canUseFirework()) {
            lastFireworkTime = System.currentTimeMillis();
            shouldCancelNextUse = true;
            useFirework();
        }

        if (pendingSwitchBack && switchBackTimer.hasElapsedTime(switchBackDelay.getValueInt())) {
            performSwitchBack();
        }

        wasRightClickPressed = currentRightClick;
    }

    private boolean canUseFirework() {
        return System.currentTimeMillis() - lastFireworkTime > 200;
    }

    private boolean shouldUseFirework() {
        if (!isWearingElytra())
            return false;
        if (onlyWhenFlying.getValue() && !isElytraFlying())
            return false;
        if (isHoldingImportantItem())
            return false;
        return InventoryUtil.hasItem(Items.FIREWORK_ROCKET);
    }

    private boolean isElytraFlying() {
        try {
            return (Boolean) mc.player.getClass().getMethod("isGliding").invoke(mc.player);
        } catch (Throwable ignored) {
            try {
                return (Boolean) mc.player.getClass().getMethod("isFallFlying").invoke(mc.player);
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }

    private boolean isWearingElytra() {
        ItemStack chestplate = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chestplate.isOf(Items.ELYTRA))
            return false;
        return chestplate.getMaxDamage() == 0 || chestplate.getDamage() < chestplate.getMaxDamage() - 1;
    }

    private boolean isHoldingImportantItem() {
        if (respectGapples.getValue()
                && (isGoldenApple(mc.player.getMainHandStack()) || isGoldenApple(mc.player.getOffHandStack()))) {
            return true;
        }
        return respectArmor.getValue()
                && (isArmor(mc.player.getMainHandStack()) || isArmor(mc.player.getOffHandStack()));
    }

    private boolean isGoldenApple(ItemStack stack) {
        return stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    private boolean isArmor(ItemStack stack) {
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("helmet") || name.contains("chestplate") || name.contains("leggings")
                || name.contains("boots");
    }

    private void useFirework() {
        if (!InventoryUtil.hasItem(Items.FIREWORK_ROCKET))
            return;

        originalSlot = mc.player.getInventory().getSelectedSlot();
        if (!InventoryUtil.swapToSlot(Items.FIREWORK_ROCKET))
            return;

        if (mc.player.getMainHandStack().getItem() instanceof FireworkRocketItem) {
            ((MinecraftClientAccessor) mc).invokeDoItemUse();
        }

        if (autoSwitchBack.getValue()) {
            pendingSwitchBack = true;
            switchBackTimer.reset();
        }
    }

    private void performSwitchBack() {
        if (originalSlot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            if (!SlotGuard.tryClaim()) return;
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
        }
        pendingSwitchBack = false;
        originalSlot = -1;
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (pendingSwitchBack && originalSlot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            if (SlotGuard.tryClaim()) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                InventoryUtil.syncSelectedSlot();
            }
        }
        pendingSwitchBack = false;
        originalSlot = -1;
    }
}
