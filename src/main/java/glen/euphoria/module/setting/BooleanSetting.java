package glen.euphoria.module.setting;

import glen.euphoria.EuphoriaClient;
import lombok.Setter;

@Setter
public class BooleanSetting extends Setting {
    private boolean value;

    public BooleanSetting(String name, boolean value) {
        super(name);
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
        triggerAutoSave();
    }

    public boolean getValue() {
        return value;
    }
    
    public void setValue(boolean value) {
        this.value = value;
        triggerAutoSave();
    }
    
    private void triggerAutoSave() {
        glen.euphoria.utils.AutoSaveManager.getInstance().scheduleSave();
    }
}
