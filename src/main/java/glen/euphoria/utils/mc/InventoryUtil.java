package glen.euphoria.utils.mc;

import glen.euphoria.utils.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;

import java.util.Objects;

@UtilityClass
public final class InventoryUtil implements IMinecraft {

    public static void syncSelectedSlot() {
        if (mc.interactionManager != null) {
            ((glen.euphoria.mixin.ClientPlayerInteractionManagerAccessor) mc.interactionManager).invokeSyncSelectedSlot();
        }
    }

    public static int findHotbarSlot(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem().equals(item)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean swapToSlot(Item item) {
        if (mc.player == null) return false;
        int slot = findHotbarSlot(item);
        if (slot == -1) return false;
        if (mc.player.getInventory().getSelectedSlot() == slot) return true; // already held, no packet

        if (!SlotGuard.tryClaim()) return false;   // defer to next tick, tell caller

        mc.player.getInventory().setSelectedSlot(slot);
        syncSelectedSlot();                         // the ONLY packet, and only when we changed
        return true;
    }

    public static boolean hasItem(Item item) {
        if (mc.player == null) return false;
        for (byte i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasWeapon(Class<? extends Item> weaponClass) {
        if (mc.player == null) return false;
        for (byte i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (weaponClass.isInstance(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    public static boolean swapToWeapon(Class<? extends Item> weaponClass) {
        if (mc.player == null) return false;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (weaponClass.isInstance(stack.getItem())) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return false;
        if (mc.player.getInventory().getSelectedSlot() == slot) return true;

        if (!SlotGuard.tryClaim()) return false;

        mc.player.getInventory().setSelectedSlot(slot);
        syncSelectedSlot();
        return true;
    }

    public static boolean hasWeapon(TagKey<Item> weaponTag) {
        if (mc.player == null) return false;
        for (byte i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isIn(weaponTag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean swapToWeapon(TagKey<Item> weaponTag) {
        if (mc.player == null) return false;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isIn(weaponTag)) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return false;
        if (mc.player.getInventory().getSelectedSlot() == slot) return true;

        if (!SlotGuard.tryClaim()) return false;

        mc.player.getInventory().setSelectedSlot(slot);
        syncSelectedSlot();
        return true;
    }
}
