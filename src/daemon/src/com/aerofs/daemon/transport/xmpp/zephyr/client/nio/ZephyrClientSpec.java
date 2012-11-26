/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IState;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateEventType;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.StateMachineSpec;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.StateMachineSpec.StateMachineSpecBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.BOUND;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.CONNECTED;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.HANDSHAKE_COMPLETE;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PENDING_OUT_PACKET;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PREPARED_FOR_BINDING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PREPARED_FOR_CONNECT;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PREPARED_FOR_REGISTRATION;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.RECVD_ACK;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.RECVD_SYN;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.RECVD_SYNACK;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.REGISTERED;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SEL_CONNECT;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SEL_READ;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SEL_WRITE;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SENT_SYN;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SENT_SYNACK;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.WRITE_COMPLETE;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.BINDING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.CONNECTING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.HANDSHAKE;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.PREP_FOR_BINDING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.PREP_FOR_CONNECT;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.PREP_FOR_REGISTRATION;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.RECVING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.REGISTERING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.SENDING_AND_RECVING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.WAIT_FOR_HANDSHAKE_ACK;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.WAIT_FOR_HANDSHAKE_SYNACK;

/**
 * State machine specification for a ZephyrClient
 */
class ZephyrClientSpec
{
    //
    // IMPORTANT : keep these definitions before the static initializer so that references to them
    // inside the transition definitions resolve properly
    //

    /** convenience set of events that most states would like to defer */
    private static final ImmutableSet<IStateEventType> COMMON_DEFERRED_ASYNC_EVENTS =
        ImmutableSet.<IStateEventType>of(RECVD_SYN, RECVD_SYNACK, RECVD_ACK, PENDING_OUT_PACKET);

    static {

        //
        // in all cases, returning HALT from a state terminates the state machine
        // and PARK simply waits for an external physical event in the current state
        // hmm...is this pretty much an hsm?
        //

        StateMachineSpecBuilder<ZephyrClientContext> spec = StateMachineSpec.builder();

        //
        // PREP_FOR_CONNECT state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(PREPARED_FOR_CONNECT, CONNECTING);

            spec.add_(PREP_FOR_CONNECT, COMMON_DEFERRED_ASYNC_EVENTS, transitions.build());
        }

        //
        // CONNECTING state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(SEL_CONNECT, CONNECTING);
            transitions.put(CONNECTED, PREP_FOR_REGISTRATION);

            spec.add_(CONNECTING, COMMON_DEFERRED_ASYNC_EVENTS, transitions.build());
        }

        //
        // PREP_FOR_REGISTRATION state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(PREPARED_FOR_REGISTRATION, REGISTERING);

            spec.add_(PREP_FOR_REGISTRATION, COMMON_DEFERRED_ASYNC_EVENTS, transitions.build());
        }

        //
        // REGISTERING state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(SEL_READ, REGISTERING);
            transitions.put(REGISTERED, HANDSHAKE); // FIXME (AG): may be processed after incoming syn...

            spec.add_(REGISTERING, COMMON_DEFERRED_ASYNC_EVENTS, transitions.build());
        }

        //
        // HANDSHAKE state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(RECVD_SYN, HANDSHAKE);
            transitions.put(SENT_SYN, WAIT_FOR_HANDSHAKE_SYNACK);
            transitions.put(SENT_SYNACK, WAIT_FOR_HANDSHAKE_ACK);

            ImmutableSet.Builder<IStateEventType> deferred = ImmutableSet.builder();
            deferred.add(RECVD_SYNACK);
            deferred.add(RECVD_ACK);
            deferred.add(PENDING_OUT_PACKET);

            spec.add_(HANDSHAKE, deferred.build(), transitions.build());
        }

        //
        // WAIT_FOR_HANDSHAKE_SYNACK
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(RECVD_SYN, HANDSHAKE);
            transitions.put(RECVD_SYNACK, WAIT_FOR_HANDSHAKE_SYNACK);
            transitions.put(RECVD_ACK, WAIT_FOR_HANDSHAKE_SYNACK);
            transitions.put(HANDSHAKE_COMPLETE, PREP_FOR_BINDING);

            ImmutableSet.Builder<IStateEventType> deferred = ImmutableSet.builder();
            deferred.add(PENDING_OUT_PACKET);

            spec.add_(WAIT_FOR_HANDSHAKE_SYNACK, deferred.build(), transitions.build());
        }

        //
        // WAIT_FOR_HANDSHAKE_ACK
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(RECVD_SYN, WAIT_FOR_HANDSHAKE_ACK);
            transitions.put(RECVD_SYNACK, WAIT_FOR_HANDSHAKE_ACK);
            transitions.put(RECVD_ACK, WAIT_FOR_HANDSHAKE_ACK);
            transitions.put(HANDSHAKE_COMPLETE, PREP_FOR_BINDING);

            ImmutableSet.Builder<IStateEventType> deferred = ImmutableSet.builder();
            deferred.add(PENDING_OUT_PACKET);

            spec.add_(WAIT_FOR_HANDSHAKE_ACK, deferred.build(), transitions.build());
        }

        //
        // PREP_FOR_BINDING state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(SEL_READ, PREP_FOR_BINDING);
            transitions.put(PREPARED_FOR_BINDING, BINDING);

            spec.add_(PREP_FOR_BINDING, COMMON_DEFERRED_ASYNC_EVENTS, transitions.build());
        }

        //
        // BINDING state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(SEL_READ, BINDING);
            transitions.put(SEL_WRITE, BINDING);
            transitions.put(BOUND, RECVING);

            spec.add_(BINDING, COMMON_DEFERRED_ASYNC_EVENTS, transitions.build());
        }

        //
        // RECVING state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(SEL_READ, BINDING);
            transitions.put(PENDING_OUT_PACKET, SENDING_AND_RECVING);

            ImmutableSet.Builder<IStateEventType> deferred = ImmutableSet.builder();
            deferred.add(RECVD_SYN);
            deferred.add(RECVD_SYNACK);
            deferred.add(RECVD_ACK);

            spec.add_(RECVING, deferred.build(), transitions.build());
        }


        //
        // SENDING_AND_RECVING state
        //

        {
            ImmutableMap.Builder<IStateEventType, IState<ZephyrClientContext>> transitions = ImmutableMap.builder();
            transitions.put(SEL_READ, SENDING_AND_RECVING);
            transitions.put(SEL_WRITE, SENDING_AND_RECVING);
            transitions.put(WRITE_COMPLETE, RECVING);

            spec.add_(SENDING_AND_RECVING, COMMON_DEFERRED_ASYNC_EVENTS, transitions.build());
        }

        STATE_MACHINE_SPEC = spec.build_();
    }

    /** unmodifiable state-machine specification for a ZephyrClient */
    static final StateMachineSpec<ZephyrClientContext> STATE_MACHINE_SPEC;
}
