/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio.statemachine;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.newHashMap;

/**
 * Describes the specification for a simple state machine
 *
 * This state machine is described using a set of states (S), a set of events (E) and
 * a set of transitions (T). Events are both 1) internally generated and 2) externally generated.
 * <p/>
 * For each state S1 we specify both:
 * <ul>
 *     <li>A transition map</li> FIXME (AG): it appears I'm confused about this
 *     <li>A set of deferred, or ignored events for that state</li>
 * </ul>
 *
 * @param <T> Type-parameter for the {@link IStateContext }context object that
 * instances of {@link StateMachine} use to store transient data
 */
public final class StateMachineSpec<T extends IStateContext>
{
    /**
     * @return a {@link StateMachineSpecBuilder} that can be used to incrementally create a
     * {@link StateMachineSpec}. NOTE: The resulting specification is immutable!
     */
    public static<T extends IStateContext> StateMachineSpecBuilder<T> builder()
    {
        return new StateMachineSpecBuilder<T>();
    }

    /**
     * Builder that <strong>must</strong> be used to create a {@link StateMachineSpec}
     *
     *  @param <T> Type-parameter for the {@link IStateContext }context object that
     * instances of {@link StateMachine} use to store transient data
     */
    public static class StateMachineSpecBuilder<T extends IStateContext>
    {
        /**
         * Private, so the static factory function {@link StateMachineSpec.builder()} must be used
         * to generate builders.
         */
        private StateMachineSpecBuilder()
        {
            _defer = newHashMap();
            _trans = newHashMap();
        }

        /**
         * Add a state along with a set of allowed transitions and explicitly deferred events to the
         * state-machine specification.
         * <p/>
         * <strong>IMPORTANT:</strong> asserts that the state does not already exist!
         *
         * @param state {@link IState} state to add to the state machine sepcification
         * @param deferred {@link IStateEventType} event types that are explicitly deferred
         * (i.e. ignored) in this state
         * @param transitions a transition map (Event type -> next state) for this state
         */
        public void add_(IState<T> state, Set<IStateEventType> deferred, Map<IStateEventType, IState<T>> transitions)
        {
            assert !_defer.containsKey(state) && !_trans.containsKey(state) :
                ("re-add state:" + state + " deferred:" + _defer.get(state) + " transitions:" + _trans.get(state));

            _defer.put(state, deferred);
            _trans.put(state, transitions);
        }

        /**
         * @return an immutable {@code StateMachineSpec} describing the state machine
         */
        public final StateMachineSpec<T> build_()
        {
            return new StateMachineSpec<T>(ImmutableMap.copyOf(_defer), ImmutableMap.copyOf(_trans));
        }

        private final HashMap<IState<T>, Set<IStateEventType>> _defer;
        private final HashMap<IState<T>, Map<IStateEventType, IState<T>>> _trans;
    }

    /**
     * Private, to enforce use of the builder pattern to construct this object!
     */
    private StateMachineSpec(
        Map<IState<T>, Set<IStateEventType>> deferred,
        Map<IState<T>, Map<IStateEventType, IState<T>>> transitions)
    {
        _defer = deferred;
        _trans = transitions;
    }

    /**
     * @param state {@code IState} state for which transitions should be fetched
     * @return the set of transitions that are valid in a state
     */
    public final Map<IStateEventType, IState<T>> transitions_(IState<T> state)
    {
        return _trans.get(state);
    }

    /**
     * @param state {@code IState} state for which deferred event-types should be fetched
     * @return the set of events that should be ignored in a state
     */
    public final Set<IStateEventType> defer_(IState<T> state)
    {
        return _defer.get(state);
    }

    /** Map of states to the set of events that are ignored by that state */
    private final Map<IState<T>, Set<IStateEventType>> _defer;

    /** Map of states and the transitions that are allowed from that state */
    private final Map<IState<T>, Map<IStateEventType, IState<T>>> _trans;
}
