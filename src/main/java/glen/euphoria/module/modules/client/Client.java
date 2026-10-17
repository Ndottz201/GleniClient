package glen.euphoria.module.modules.client;

import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;

public final class Client extends Module {

    public static final BooleanSetting title = new BooleanSetting("Title", true);
    public Client() {
        super("Client", "Settings for the client", -1, Category.CLIENT);

        this.addSettings(
                title
        );
    }

    public boolean getTitle() {
        return title.getValue();
    }

}
