package glen.euphoria.module.events;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.input.MouseClickEvent;
import glen.euphoria.module.Module;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;

public class MouseModuleHandler {

    @EventHandler
    public void onMouseClick(MouseClickEvent event) {
        if (event.action() == GLFW.GLFW_PRESS) {
            int button = event.button();

            for (Module module : EuphoriaClient.INSTANCE.getModuleManager().getModules()) {
                int moduleKey = module.getKey();

                if (moduleKey == -1 || moduleKey == 0) continue;

                boolean matches = moduleKey == button ||
                        moduleKey == -(button + 1) ||
                        moduleKey == (-100 - button);

                if (matches) {
                    module.toggle();
                    break;
                }
            }
        }
    }
}