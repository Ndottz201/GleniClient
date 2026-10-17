package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.DoAttackEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.mc.EnchantmentUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class BreachSwap extends Module {

    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 10, 100, 30, 1);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Only on ground", true);
    private final BooleanSetting silentSwap = new BooleanSetting("Silent Swap", true);

    private int originalSlot = -1;
    private int swappedToSlot = -1;
    private boolean shouldSwitchBack = false;
    private long switchTime = 0;

    public BreachSwap() {
        super("Breach Swap", "Switches to a Breach enchanted mace when attacking", Category.COMBAT);
        addSettings(switchDelay, onlyOnGround, silentSwap);
    }

    @EventHandler
    public void onDoAttack(DoAttackEvent event) {
        if (isNull())
            return;
        if (onlyOnGround.getValue() && !mc.player.isOnGround())
            return;
        if (ShieldBreaker.breakingShield)
            return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity target = ((EntityHitResult) hit).getEntity();
        if (!(target instanceof LivingEntity))
            return;

        if (shouldSwitchBack) {
            if (mc.player.getInventory().getSelectedSlot() == swappedToSlot) {
                // Already on the mace; refresh timer and let vanilla attack
                switchTime = System.currentTimeMillis();
                return;
            } else {
                originalSlot = -1;
                swappedToSlot = -1;
                shouldSwitchBack = false;
            }
        }

        int maceSlot = findBreachMaceSlot();
        if (maceSlot == -1 || maceSlot == mc.player.getInventory().getSelectedSlot())
            return;

        if (!SlotGuard.tryClaim()) return; // another module already swapped this tick

        if (originalSlot == -1) {
            originalSlot = mc.player.getInventory().getSelectedSlot();
        }

        // Swap slot then sync before vanilla attack packet
        mc.player.getInventory().setSelectedSlot(maceSlot);
        InventoryUtil.syncSelectedSlot();
        swappedToSlot = maceSlot;

        // NO attackEntity(), NO swingHand() here — vanilla doAttack sends both!
        shouldSwitchBack = true;
        switchTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (isNull())
            return;
        if (ShieldBreaker.breakingShield)
            return;

        if (shouldSwitchBack) {
            if (mc.player.getInventory().getSelectedSlot() != swappedToSlot) {
                originalSlot = -1;
                swappedToSlot = -1;
                shouldSwitchBack = false;
                return;
            }

            long delay = Math.max(50L, (long) switchDelay.getValue());
            if (System.currentTimeMillis() - switchTime >= delay) {
                if (originalSlot != -1) {
                    if (!SlotGuard.tryClaim()) return;
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                    InventoryUtil.syncSelectedSlot();
                    originalSlot = -1;
                }
                swappedToSlot = -1;
                shouldSwitchBack = false;
            }
        }
    }

    private int findBreachMaceSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Item item = stack.getItem();
            if (item instanceof MaceItem && hasBreach(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasBreach(ItemStack stack) {
        RegistryKey<net.minecraft.enchantment.Enchantment> breachKey = RegistryKey.of(RegistryKeys.ENCHANTMENT,
                Identifier.of("minecraft", "breach"));
        return EnchantmentUtil.hasEnchantment(stack, mc.world, breachKey);
    }

    @Override
    public void onDisable() {
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
            originalSlot = -1;
        }
        swappedToSlot = -1;
        shouldSwitchBack = false;
        super.onDisable();
    }
}
