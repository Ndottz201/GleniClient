package io.github.racoondog.norbit;

import meteordevelopment.orbit.IEventBus;

/**
 * Compatibility shim for projects that used Norbit.
 *
 * Orbit's default EventBus implementation is thread-safe, so we can safely
 * expose a "threadSafe" constructor-style API here.
 */
public final class EventBus {
    private EventBus() {}

    public static IEventBus threadSafe() {
        return new meteordevelopment.orbit.EventBus();
    }
}

