package glen.euphoria.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseHandlerAccessor {

    @Invoker("onMouseButton")
    void press(long window, net.minecraft.client.input.MouseInput mouseInput, int action);

    @Accessor("x")
    double getX();

    @Accessor("x")
    void setX(double x);

    @Accessor("y")
    double getY();

    @Accessor("y")
    void setY(double y);

    @Accessor("cursorDeltaX")
    void setCursorDeltaX(double deltaX);

    @Accessor("cursorDeltaY")
    void setCursorDeltaY(double deltaY);
}