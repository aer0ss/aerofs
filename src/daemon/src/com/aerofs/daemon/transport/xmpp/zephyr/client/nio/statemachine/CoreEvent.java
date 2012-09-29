/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine;

/**
 * These are the only two events that are returned by an {@link IState} that a
 * {@link StateMachine} specifically understands. Can be used by an {@code IState} function to force
 * the state machine to wait ({@link CoreEvent.PARK}) for an external event or terminate
 * ({@link CoreEvent.HALT})
 */
public final class CoreEvent extends StateMachineEvent
{
    /**
     * Each event type here corresponds to the singleton {@code CoreEvent} object below
     */
    public static enum CoreEventType implements IStateEventType
    {
        EVENT_TYPE_PARK,
        EVENT_TYPE_HALT
    }

    /**
     * should be returned whenever you want to wait for an external IO or
     * asynchronous event. Basically parks the state machine until it is run again
     */
    public static final CoreEvent PARK = new CoreEvent(CoreEventType.EVENT_TYPE_PARK);

    /**
     * returned when you want to forcibly halt the state machine <em>regardless</em>
     * of what state you are currently in. this is the big "shutdown now" hammer
     */
    public static final CoreEvent HALT = new CoreEvent(CoreEventType.EVENT_TYPE_PARK);

    /**
     * Private so that no one can actually construct {@code CoreEvent} instances.
     *
     * @param type one of {@link CoreEventType.EVENT_TYPE_PARK} or {@link CoreEventType.EVENT_TYPE_HALT}
     */
    private CoreEvent(IStateEventType type)
    {
        super(type);
    }
}
