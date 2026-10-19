package glen.euphoria.module;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.utils.IMinecraft;
import glen.euphoria.module.setting.KeybindSetting;
import glen.euphoria.module.setting.Setting;
import glen.euphoria.utils.notification.NotificationManager;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Setter
@Getter
public abstract class Module implements IMinecraft {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<Setting> settings = new ArrayList<>();
    private final Category moduleCategory;
    private final KeybindSetting keybindSetting;

    private final String name;
    private final String description;

    private String suffix;
    private boolean enabled;
    private int key;
    private boolean registered;

    public Module(String name, String description, int key, Category moduleCategory) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.moduleCategory = moduleCategory;
        this.enabled = false;

        this.keybindSetting = new KeybindSetting("Keybind", key, true);
        addSetting(keybindSetting);
    }

    public Module(String name, String description, Category moduleCategory) {
        this(name, description, -1, moduleCategory);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public boolean isNull() {
        return mc.player == null || mc.world == null;
    }

    public void addSetting(Setting setting) {
        settings.add(setting);
    }

    public void addSettings(Setting... settings) {
        this.settings.addAll(Arrays.asList(settings));
    }

    public int getKey() {
        return keybindSetting != null ? keybindSetting.getKeyCode() : key;
    }

    public void setKey(int key) {
        this.key = key;
        if (keybindSetting != null) {
            keybindSetting.setKeyCode(key);
        }
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;

        this.enabled = enabled;

        if (enabled) {
            onEnable();
            if (this.enabled) {
                EuphoriaClient.INSTANCE.getEuphoriaEventBus().subscribe(this);
                registered = true;
                NotificationManager.getInstance().addModuleNotification(this, true);
            }
        } else {
            if (registered) {
                EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(this);
                registered = false;
            }
            onDisable();
            NotificationManager.getInstance().addModuleNotification(this, false);
        }
        
        glen.euphoria.utils.AutoSaveManager.getInstance().scheduleSave();
    }

    public void saveConfig(com.google.gson.JsonObject json) {
    }

    public void loadConfig(com.google.gson.JsonObject json) {
    }
}
