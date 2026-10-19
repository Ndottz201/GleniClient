package glen.euphoria.event.impl.player;

import glen.euphoria.event.types.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.Entity;

@AllArgsConstructor
@Getter
public class AttackEvent extends CancellableEvent {
    Entity target;
}
