package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.module.setting.BooleanSetting;
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
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public final class AutoPot extends Module {
    private static final NumberSetting healthThreshold = new NumberSetting("Health Threshold", 1, 20, 10, 0.5);
    private static final NumberSetting throwCooldown = new NumberSetting("Throw Cooldown", 50, 1000, 250, 50);
    private static final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 1, 20, 10, 0.5);
    private static final NumberSetting swapDelay = new NumberSetting("Swap Delay", 0, 200, 50, 10);
    private static final NumberSetting minPlayerDistance = new NumberSetting("Min Player Distance", 0, 10, 0, 0.5);
    private static final BooleanSetting requireOnGround = new BooleanSetting("Require On Ground", true);

    private final TimerUtil throwTimer = new TimerUtil();
    private final TimerUtil swapTimer = new TimerUtil();
    private final List<Integer> availablePotionSlots = new ArrayList<>();
    private int savedHotbarSlot = -1;
    private float savedPitch = 0;
    private boolean isRotating = false;
    private boolean isWaitingToThrow = false;
    private float targetPitch = 0;
    private float rotationProgress = 0;

    public AutoPot() {
        super("Auto Pot", "Throws health potions when low", -1, Category.COMBAT);
        this.addSettings(healthThreshold, throwCooldown, rotationSpeed, swapDelay, minPlayerDistance, requireOnGround);
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull() || mc.currentScreen != null || mc.player.isUsingItem()) return;

        if (isWaitingToThrow) {
            if (swapTimer.hasElapsedTime(swapDelay.getValueInt())) {
                executeThrow();
            }
            return;
        }

        if (isRotating) {
            handleRotation();
            return;
        }

        if (shouldThrowPotion()) {
            if (!canThrowPotion()) return;
            
            findAvailablePotions();
            if (availablePotionSlots.isEmpty()) return;

            if (savedHotbarSlot == -1) {
                savedHotbarSlot = mc.player.getInventory().getSelectedSlot();
                savedPitch = mc.player.getPitch();
            }

            startRotation(89.9f);
        }
    }

    private void startRotation(float targetPitch) {
        isRotating = true;
        this.targetPitch = targetPitch;
        rotationProgress = 0;
    }

    private void handleRotation() {
        if (rotationSpeed.getValueFloat() <= 1.0f) {
            RotationController.request(mc.player.getYaw(), targetPitch, 10);
            isRotating = false;
            if (targetPitch == 89.9f) {
                startThrowSequence();
            } else {
                if (savedHotbarSlot != -1 && mc.player.getInventory().getSelectedSlot() != savedHotbarSlot) {
                    if (!SlotGuard.tryClaim()) return;
                    mc.player.getInventory().setSelectedSlot(savedHotbarSlot);
                    InventoryUtil.syncSelectedSlot();
                }
                resetState();
            }
        } else {
            float speed = (rotationSpeed.getValueFloat() - 1.0f) * 0.2f;
            rotationProgress += speed;

            if (rotationProgress >= 1.0f) {
                RotationController.request(mc.player.getYaw(), targetPitch, 10);
                isRotating = false;
                if (targetPitch == 89.9f) {
                    startThrowSequence();
                } else {
                    if (savedHotbarSlot != -1 && mc.player.getInventory().getSelectedSlot() != savedHotbarSlot) {
                        if (!SlotGuard.tryClaim()) return;
                        mc.player.getInventory().setSelectedSlot(savedHotbarSlot);
                        InventoryUtil.syncSelectedSlot();
                    }
                    resetState();
                }
            } else {
                float interpPitch = MathHelper.lerp(rotationProgress, savedPitch, targetPitch);
                RotationController.request(mc.player.getYaw(), interpPitch, 10);
            }
        }
    }

    private void startThrowSequence() {
        if (swapDelay.getValueInt() <= 0) {
            executeThrow();
        } else {
            isWaitingToThrow = true;
            swapTimer.reset();
        }
    }

    private void executeThrow() {
        if (availablePotionSlots.isEmpty()) {
            isWaitingToThrow = false;
            return;
        }
        int potSlot = availablePotionSlots.get(0);
        if (mc.player.getInventory().getSelectedSlot() != potSlot) {
            if (!SlotGuard.tryClaim()) return;
            mc.player.getInventory().setSelectedSlot(potSlot);
            InventoryUtil.syncSelectedSlot();
        }
        ((MinecraftClientAccessor) mc).invokeDoItemUse();
        isWaitingToThrow = false;
        startRotation(savedPitch);
    }

    private void resetState() {
        savedHotbarSlot = -1;
        savedPitch = 0;
        rotationProgress = 0;
        isWaitingToThrow = false;
        availablePotionSlots.clear();
    }

    private void findAvailablePotions() {
        availablePotionSlots.clear();
        for (int i = 0; i < 9; i++) {
            if (isHealthPotion(mc.player.getInventory().getStack(i))) {
                availablePotionSlots.add(i);
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
        resetState();
        throwTimer.reset();
        swapTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (savedHotbarSlot != -1) {
            mc.player.getInventory().setSelectedSlot(savedHotbarSlot);
            RotationController.request(mc.player.getYaw(), savedPitch, 10);
        }
        resetState();
        super.onDisable();
    }

    private boolean shouldThrowPotion() {
        return mc.player.getHealth() <= healthThreshold.getValueFloat() && 
               throwTimer.hasElapsedTime(throwCooldown.getValueInt());
    }
    
    private boolean canThrowPotion() {
        if (minPlayerDistance.getValueFloat() > 0 && isPlayerTooClose()) return false;
        if (requireOnGround.getValue() && !mc.player.isOnGround()) return false;
        return true;
    }
    
    private boolean isPlayerTooClose() {
        if (mc.world == null) return false;
        
        double minDistance = minPlayerDistance.getValueFloat();
        return mc.world.getPlayers().stream()
                .anyMatch(player -> player != mc.player && 
                        mc.player.distanceTo(player) < minDistance);
    }

    @Override
    public int getKey() {
        return -1;
    }
}