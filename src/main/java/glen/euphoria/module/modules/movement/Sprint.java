package glen.euphoria.module.modules.movement;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import meteordevelopment.orbit.EventHandler;

public final class Sprint extends Module {

    public Sprint() {
        super("Sprint", "Sprints automatically whenever moving forward", -1, Category.MOVEMENT);
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull()) return;
        if (mc.options.getSprintToggled().getValue()) mc.options.getSprintToggled().setValue(false);

        mc.options.sprintKey.setPressed(true);
    }
}
