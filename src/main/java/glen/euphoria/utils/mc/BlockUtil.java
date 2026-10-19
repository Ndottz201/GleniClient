package glen.euphoria.utils.mc;

import glen.euphoria.utils.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.stream.Stream;

@UtilityClass
public final class BlockUtil implements IMinecraft {

    public static boolean isBlock(BlockPos pos, Block block) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).isOf(block);
    }

    public static boolean isAnchorCharged(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) return false;
        return state.get(RespawnAnchorBlock.CHARGES) > 0;
    }

    public static boolean isAnchorNotCharged(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) return false;
        return state.get(RespawnAnchorBlock.CHARGES) == 0;
    }

    public static boolean canPlaceBlockClient(BlockPos block) {
        if (mc.world == null) return false;

        BlockPos up = block.up();
        if (!mc.world.isAir(up)) return false;

        double x = up.getX();
        double y = up.getY();
        double z = up.getZ();

        List<Entity> list = mc.world.getOtherEntities(null, new Box(x, y, z, x + 1, y + 1, z + 1));
        list.removeIf(entity -> entity instanceof ItemEntity);

        return list.isEmpty();
    }

    public static Stream<BlockPos> getAllInBoxStream(BlockPos from, BlockPos to) {
        BlockPos min = new BlockPos(
                Math.min(from.getX(), to.getX()),
                Math.min(from.getY(), to.getY()),
                Math.min(from.getZ(), to.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(from.getX(), to.getX()),
                Math.max(from.getY(), to.getY()),
                Math.max(from.getZ(), to.getZ())
        );

        Stream<BlockPos> stream = Stream.iterate(min, pos -> {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            ++x;
            if (x > max.getX()) {
                x = min.getX();
                ++y;
            }
            if (y > max.getY()) {
                y = min.getY();
                ++z;
            }
            if (z > max.getZ())
                throw new IllegalStateException("Stream limit didn't work.");

            return new BlockPos(x, y, z);
        });

        int limit = (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);

        return stream.limit(limit);
    }
}