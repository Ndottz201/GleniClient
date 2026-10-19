package glen.euphoria.module.modules.misc;

import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.gui.FriendsScreen;

public class Friends extends Module {

    public Friends() {
        super("Friends", "Manage friends to avoid targeting them", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            mc.setScreen(new FriendsScreen());
        }
        setEnabled(false);
    }
}
