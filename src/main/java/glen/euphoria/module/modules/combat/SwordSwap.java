package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.DoAttackEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import glen.euphoria.utils.mc.ToolItems;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * SwordSwap — rewritten for correctness and reliability.
 *
 * Key fixes vs original:
 *  - Swap-back timing: original used getValue() * 50ms which silently produced
 *    wrong results because getValue() returns a double and the multiply was
 *    integer-truncated inconsistently. Now uses getValueInt() * 50L explicitly.
 *  - Swap-back abort condition: original cancelled the swap-back if the player
 *    manually changed slots, but left swappedToSlot stale. resetState() is now
 *    called unconditionally in that path so nothing leaks.
 *  - findBestSwordSlot() early-return bug: when requireKB=false, the original
 *    returned on the first non-selected sword slot it found — skipping the rest
 *    of the hotbar. Rewritten to always scan the full hotbar and pick the sword
 *    with the highest KB level when requireKB=true, or highest sharpness
 *    otherwise (giving a consistent, deterministic best-sword choice).
 *  - getKnockbackLevel() is now getEnchantLevel() — generalized and shared for
 *  - onDisable swap-back: original only swapped back if shouldSwitchBack was
 *    true, but if the module was disabled mid-swap (after slot changed, before
 *    swap-back triggered) the slot would be stuck. Now we always restore
 *    originalSlot on disable if it is valid.
 *  - Attack detection: moved from rising-edge boolean to a cleaner pattern
 *    that correctly handles the case where the input event fires multiple times
 *    per tick (which HandleInputEvent can do on some loaders).
 */
public final class SwordSwap extends Module {

    // ==================== SETTINGS ====================

    /** Only swap when the target is a player (ignore mobs). */
    private final BooleanSetting onlyPlayers = new BooleanSetting(
            "Only Players", true
    );

    /**
     * Require the chosen sword to have Knockback ≥ 1.
     * When false, picks the sword with the highest Sharpness instead.
     */
    private final BooleanSetting requireKB = new BooleanSetting(
            "Require KB Enchant", true
    );

    /** Automatically swap back to the original slot after hitting. */
    private final BooleanSetting swapBack = new BooleanSetting(
            "Swap Back", true
    );

    /**
     * How long (in ticks, 1 tick = 50 ms) to wait before swapping back.
     * 0 = swap back immediately next input poll.
     */
    private final NumberSetting swapBackDelay = new NumberSetting(
            "Swap Back Delay (ticks)", 0, 10, 1, 1
    );

    // ==================== STATE ====================

    /** The hotbar slot we were on before swapping. -1 = no active swap. */
    private int originalSlot = -1;

    /** The hotbar slot we swapped to. Used to detect manual slot changes. */
    private int swappedToSlot = -1;

    /** True when we've swapped and are waiting to swap back. */
    private boolean pendingSwapBack = false;

    /** System.currentTimeMillis() when the swap happened. */
    private long swapTime = 0L;

    // ==================== CONSTRUCTOR ====================

    public SwordSwap() {
        super("SwordSwap", "Swaps to KB sword on attack for knockback, then swaps back", -1, Category.COMBAT);
        addSettings(onlyPlayers, requireKB, swapBack, swapBackDelay);
    }

    // ==================== LIFECYCLE ====================

    @Override
    public void onEnable() {
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        // Always restore slot on disable if we're mid-swap, regardless of pendingSwapBack.
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
        }
        resetState();
        super.onDisable();
    }

    // ==================== ATTACK & SWAP HANDLER ====================

    @EventHandler
    public void onDoAttack(DoAttackEvent event) {
        if (mc.player == null || mc.world == null) return;

        Entity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            target = hit.getEntity();
        }
        if (target == null && mc.targetedEntity != null) {
            target = mc.targetedEntity;
        }
        if (target == null) {
            return;
        }

        // Filter by entity type
        if (onlyPlayers.getValue() && !(target instanceof PlayerEntity)) {
            return;
        }

        if (pendingSwapBack) {
            if (mc.player.getInventory().getSelectedSlot() == swappedToSlot) {
                // Already on the KB sword; just let vanilla attack and refresh swap timer
                swapTime = System.currentTimeMillis();
                return;
            } else {
                // Player manually switched slots mid-swap; abort previous swap
                resetState();
            }
        }

        // Find the best sword slot
        int bestSlot = findBestSwordSlot();
        if (bestSlot == -1 || bestSlot == mc.player.getInventory().getSelectedSlot()) {
            return;
        }

        if (!SlotGuard.tryClaim()) return; // another module already swapped this tick

        // Perform slot swap and sync BEFORE vanilla attack packet
        originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(bestSlot);
        InventoryUtil.syncSelectedSlot();
        swappedToSlot = bestSlot;
        swapTime = System.currentTimeMillis();

        if (swapBack.getValue()) {
            pendingSwapBack = true;
        } else {
            originalSlot = -1;
            swappedToSlot = -1;
        }

        // NO attackEntity(), NO swingHand() here — vanilla doAttack sends both!
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (pendingSwapBack) {
            if (mc.player.getInventory().getSelectedSlot() != swappedToSlot) {
                resetState();
                return;
            }

            long delayMs = Math.max(50L, swapBackDelay.getValueInt() * 50L);
            if (System.currentTimeMillis() - swapTime >= delayMs) {
                if (!SlotGuard.tryClaim()) return;
                mc.player.getInventory().setSelectedSlot(originalSlot);
                InventoryUtil.syncSelectedSlot();
                resetState();
            }
        }
    }

    // ==================== SWORD SELECTION ====================

    /**
     * Scans the hotbar and returns the slot of the best sword to swap to.
     *
     * When requireKB=true : picks the sword with the highest Knockback level (must be ≥ 1).
     * When requireKB=false: picks the sword with the highest Sharpness level (any sword qualifies).
     *
     * Never returns the currently selected slot (no-op swap).
     * Returns -1 if no qualifying sword is found.
     */
    private int findBestSwordSlot() {
        int bestSlot  = -1;
        int bestLevel = requireKB.getValue() ? 0 : -1; // 0 means "must have KB ≥ 1"

        for (int i = 0; i < 9; i++) {
            if (i == mc.player.getInventory().getSelectedSlot()) continue;

            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !ToolItems.isSword(stack)) continue;

            if (requireKB.getValue()) {
                int kb = getEnchantLevel(stack, Enchantments.KNOCKBACK);
                if (kb > bestLevel) {
                    bestLevel = kb;
                    bestSlot  = i;
                }
            } else {
                // Pick sword with highest sharpness; any sword (even unenchanted) is valid.
                int sharp = getEnchantLevel(stack, Enchantments.SHARPNESS);
                if (sharp > bestLevel || bestSlot == -1) {
                    bestLevel = sharp;
                    bestSlot  = i;
                }
            }
        }

        // If requireKB is on and the best sword has no KB, reject it.
        if (requireKB.getValue() && bestLevel <= 0) return -1;

        return bestSlot;
    }



    // ==================== ENCHANTMENT LOOKUP ====================

    /**
     * Returns the level of the given enchantment on the stack, or 0 if absent.
     * Silently returns 0 on any registry/null issue.
     */
    private int getEnchantLevel(ItemStack stack, net.minecraft.registry.RegistryKey<Enchantment> enchantKey) {
        if (mc.world == null) return 0;

        try {
            ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (enchantments == null) return 0;

            Registry<Enchantment> registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            // getEntry(Identifier) is the available overload in this MC version.
            // RegistryKey.getValue() returns the enchantment's Identifier.
            java.util.Optional<net.minecraft.registry.entry.RegistryEntry.Reference<Enchantment>> entryOpt =
                    registry.getEntry(enchantKey.getValue());
            if (entryOpt.isEmpty()) return 0;

            return enchantments.getLevel(entryOpt.get());
        } catch (Exception ignored) {
            return 0;
        }
    }

    // ==================== STATE ====================

    private void resetState() {
        originalSlot   = -1;
        swappedToSlot  = -1;
        pendingSwapBack = false;
        swapTime       = 0L;
    }
}