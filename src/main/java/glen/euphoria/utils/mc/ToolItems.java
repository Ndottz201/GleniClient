package glen.euphoria.utils.mc;

import lombok.experimental.UtilityClass;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;

/**
 * Tag-based tool/weapon detection.
 *
 * <p>1.21.5 removed the dedicated item classes ({@code SwordItem},
 * {@code PickaxeItem}, {@code MiningToolItem}, etc.) in favour of a
 * component-driven item model. The robust, version-stable way to classify an
 * item is now via the vanilla item tags, which these helpers wrap.</p>
 */
@UtilityClass
public final class ToolItems {

    public static boolean isSword(ItemStack stack) {
        return stack != null && stack.isIn(ItemTags.SWORDS);
    }

    public static boolean isSword(Item item) {
        return item != null && item.getRegistryEntry().isIn(ItemTags.SWORDS);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && stack.isIn(ItemTags.AXES);
    }

    public static boolean isAxe(Item item) {
        return item != null && item.getRegistryEntry().isIn(ItemTags.AXES);
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && stack.isIn(ItemTags.PICKAXES);
    }

    public static boolean isPickaxe(Item item) {
        return item != null && item.getRegistryEntry().isIn(ItemTags.PICKAXES);
    }

    public static boolean isShovel(ItemStack stack) {
        return stack != null && stack.isIn(ItemTags.SHOVELS);
    }

    public static boolean isShovel(Item item) {
        return item != null && item.getRegistryEntry().isIn(ItemTags.SHOVELS);
    }

    public static boolean isHoe(ItemStack stack) {
        return stack != null && stack.isIn(ItemTags.HOES);
    }

    public static boolean isHoe(Item item) {
        return item != null && item.getRegistryEntry().isIn(ItemTags.HOES);
    }

    /** Equivalent to the old {@code instanceof MiningToolItem} (pickaxe/axe/shovel/hoe). */
    public static boolean isMiningTool(ItemStack stack) {
        return isPickaxe(stack) || isAxe(stack) || isShovel(stack) || isHoe(stack);
    }

    public static boolean isMiningTool(Item item) {
        return isPickaxe(item) || isAxe(item) || isShovel(item) || isHoe(item);
    }

    /** The tag matching a held item, or {@code null} if it is not a recognised weapon/tool. */
    public static TagKey<Item> swords() {
        return ItemTags.SWORDS;
    }
}
