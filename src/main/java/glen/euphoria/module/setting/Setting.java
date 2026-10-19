package glen.euphoria.module.setting;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class Setting {
    private String name;

    public Setting(String name) {
        this.name = name;
    }
}
