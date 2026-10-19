package glen.euphoria.utils.mc;

import glen.euphoria.utils.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;

@UtilityClass
public final class CrystalUtil implements IMinecraft {

    public static boolean canPlaceCrystalClient(BlockPos block) {
        if (mc.world == null) return false;

        BlockState blockState = mc.world.getBlockState(block);
        if (!blockState.isOf(Blocks.OBSIDIAN) && !blockState.isOf(Blocks.BEDROCK))
            return false;

        return canPlaceCrystalClientAssumeObsidian(block);
    }

    public static boolean canPlaceCrystalClientAssumeObsidian(BlockPos pos) {
        if (mc.world == null) return false;

        BlockPos above = pos.up();
        if (!mc.world.getBlockState(above).isAir()) return false;

        Box crystalBox = new Box(
                pos.getX(), pos.getY() + 1.0, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 3.0, pos.getZ() + 1.0
        );

        return mc.world.getOtherEntities(null, crystalBox, Entity::isAlive).isEmpty();
    }

    public static boolean canPlaceCrystalServer(BlockPos pos) {
        if (mc.world == null) return false;

        BlockState blockState = mc.world.getBlockState(pos);
        if (!blockState.isOf(Blocks.OBSIDIAN) && !blockState.isOf(Blocks.BEDROCK))
            return false;

        BlockPos blockPos = pos.up();
        if (!mc.world.isAir(blockPos)) return false;

        double d = blockPos.getX();
        double e = blockPos.getY();
        double f = blockPos.getZ();

        List<Entity> list = mc.world.getOtherEntities(null, new Box(d, e, f, d + 1, e + 2, f + 1));
        return list.isEmpty();
    }
}