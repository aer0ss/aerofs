/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IState;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEvent.*;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.*;

/**
 * State machine specification for a ZephyrClient
 */
class ZephyrClientSpec
{
    static {
        //
        // construct the full state machine
        // 1. construct a temporary map
        // 2. make immutable and store into fullmap
        // 3. make fullmap immutable and store into _sm
        //
        // in all cases, returning HALT from a state terminates the state machine
        // and PARK simply waits for an external io event in the current state
        // hmm...is this pretty much an hsm?
        //

        Map<IState<ZephyrClientContext>, Map<IStateEvent, IState<ZephyrClientContext>>> fullmap =
            new HashMap<IState<ZephyrClientContext>, Map<IStateEvent, IState<ZephyrClientContext>>>();

        //
        // NEW state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> newStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        newStateMap.put(BEGIN_CONNECT, PREP_FOR_CONNECT);

        fullmap.put(NEW, Collections.unmodifiableMap(newStateMap));

        //
        // PREP_FOR_CONNECT state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> prepForConnectStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        prepForConnectStateMap.put(PREPARED_FOR_CONNECT, CONNECTING);

        fullmap.put(PREP_FOR_CONNECT, Collections.unmodifiableMap(prepForConnectStateMap));

        //
        // CONNECTING state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> connectingStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        connectingStateMap.put(SEL_CONNECT, CONNECTING);
        connectingStateMap.put(CONNECTED, PREP_FOR_REGISTRATION);

        fullmap.put(CONNECTING, connectingStateMap);

        //
        // PREP_FOR_REGISTRATION state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> prepForRegistrationStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        prepForRegistrationStateMap.put(PREPARED_FOR_REGISTRATION, REGISTERING);

        fullmap.put(PREP_FOR_REGISTRATION, Collections.unmodifiableMap(prepForRegistrationStateMap));

        //
        // REGISTERING state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> registeringStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        registeringStateMap.put(SEL_READ, REGISTERING);
        registeringStateMap.put(REGISTERED, RECVING);

        fullmap.put(REGISTERING, Collections.unmodifiableMap(registeringStateMap));

        //
        // RECVING state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> recvingStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        recvingStateMap.put(SEL_READ, RECVING);
        recvingStateMap.put(PENDING_OUT_PACKET, PREP_FOR_BINDING);

        fullmap.put(RECVING, Collections.unmodifiableMap(recvingStateMap));

        //
        // PREP_FOR_BINDING state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> prepForBindingStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        prepForBindingStateMap.put(SEL_READ, PREP_FOR_BINDING); // wait....
        prepForBindingStateMap.put(PENDING_OUT_PACKET, PREP_FOR_BINDING); // ignore until we get to SENDING_AND_RECVING...
        prepForBindingStateMap.put(RECVD_REMOTE_CHAN_ID, PREP_FOR_BINDING);
        prepForBindingStateMap.put(PREPARED_FOR_BINDING, BINDING);

        fullmap.put(PREP_FOR_BINDING, Collections.unmodifiableMap(prepForBindingStateMap));

        //
        // BINDING state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> bindingStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        bindingStateMap.put(SEL_READ, BINDING);
        bindingStateMap.put(SEL_WRITE, BINDING);
        bindingStateMap.put(PENDING_OUT_PACKET, BINDING); // ignore until we get to SENDING_AND_RECVING...
        bindingStateMap.put(BOUND, SENDING_AND_RECVING);

        fullmap.put(BINDING, Collections.unmodifiableMap(bindingStateMap));

        //
        // SENDING_AND_RECVING state
        //

        Map<IStateEvent, IState<ZephyrClientContext>> sendingAndRecvingStateMap =
            new HashMap<IStateEvent, IState<ZephyrClientContext>>();
        sendingAndRecvingStateMap.put(SEL_READ, SENDING_AND_RECVING);
        sendingAndRecvingStateMap.put(SEL_WRITE, SENDING_AND_RECVING);
        sendingAndRecvingStateMap.put(PENDING_OUT_PACKET, SENDING_AND_RECVING);

        fullmap.put(SENDING_AND_RECVING, Collections.unmodifiableMap(sendingAndRecvingStateMap));

        //
        // make the final immutable state-machine map and store into _sm
        //

        STATE_MACHINE_SPEC = Collections.unmodifiableMap(fullmap);
    }

    /** unmodifiable state-machine specification for a ZephyrClient */
    static final Map<IState<ZephyrClientContext>, Map<IStateEvent, IState<ZephyrClientContext>>> STATE_MACHINE_SPEC;
}
