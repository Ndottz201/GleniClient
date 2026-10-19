package glen.euphoria.event.impl.input;

import glen.euphoria.event.types.Event;

public record MouseClickEvent(int button, int action, int mods) implements Event {

}