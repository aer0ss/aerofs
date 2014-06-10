/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.xray.client;

import com.aerofs.base.Loggers;
import com.aerofs.xray.client.exceptions.ExHandshakeFailed;
import com.aerofs.xray.proto.XRay.ZephyrHandshake;
import org.slf4j.Logger;

import javax.annotation.concurrent.NotThreadSafe;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeReturn.CHECK_PEER;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeReturn.NO_ACTION;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeReturn.SEND_ACK;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeReturn.SEND_SYN;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeReturn.SEND_SYNACK;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeState.FAILED;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeState.NOT_STARTED;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeState.SUCCEEDED;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeState.WAITING_FOR_ACK;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeState.WAITING_FOR_SYNACK;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeType.ACK;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeType.SYN;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeType.SYNACK;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;


// FIXME (AG): perhaps this class shouldn't deal in terms of PB messages _or_ zephyr PB is moved out to zephyr.proto

/**
 * Controls the zephyr handshaking process. This is a 3-way-handshake process that's very
 * similar to the once used by TCP. The handshaker is very simple and will simply transition
 * from one state to the next depending on the handshake message received. If an invalid
 * message is received, or an invalid transition attempted, an {@link com.aerofs.zephyr.client.exceptions.ExHandshakeFailed} is thrown.
 * It is the caller's responsibility to determine how long a handshake should take and
 * maintain a timer if necessary.
 *
 */
@NotThreadSafe
public final class ZephyrHandshakeEngine
{
    //
    // types
    //

    public enum HandshakeState
    {
        NOT_STARTED,
        WAITING_FOR_SYNACK,
        WAITING_FOR_ACK,
        SUCCEEDED,
        FAILED,
    }

    public enum HandshakeType
    {
        SYN,
        SYNACK,
        ACK;

        private static HandshakeType fromPB(ZephyrHandshake handshake)
                throws ExHandshakeFailed
        {
            int source = handshake.getSourceZephyrId();
            int destination = handshake.getDestinationZephyrId();

            if (source != ZEPHYR_INVALID_CHAN_ID && destination == ZEPHYR_INVALID_CHAN_ID) {
                return SYN;
            } else if (source != ZEPHYR_INVALID_CHAN_ID) {
                return SYNACK;
            } else if (destination != ZEPHYR_INVALID_CHAN_ID) {
                return ACK;
            }

            throw new ExHandshakeFailed("bad handshake msg sourceZid:" + source + " destZid:" + destination);
        }
    }

    public enum HandshakeReturn
    {
        NO_ACTION,
        CHECK_PEER,
        SEND_SYN,
        SEND_SYNACK,
        SEND_ACK,
    }

    //
    // members
    //

    private static final Logger l = Loggers.getLogger(ZephyrHandshakeEngine.class);

    private HandshakeState state = NOT_STARTED;

    private int localZid = ZEPHYR_INVALID_CHAN_ID;
    private int remoteZid = ZEPHYR_INVALID_CHAN_ID;

    //
    // getters and setters
    //

    public void setLocalZid(int localZid)
    {
        checkState(this.localZid == ZEPHYR_INVALID_CHAN_ID);

        checkArgument(localZid != ZEPHYR_INVALID_CHAN_ID);
        checkArgument(localZid != remoteZid, "l:" + localZid + " r:" + remoteZid);

        this.localZid = localZid;
    }

    public int getLocalZid()
    {
        return localZid;
    }

    private void setRemoteZid(int remoteZid)
    {
        checkArgument(localZid != remoteZid, "l:" + localZid + " r:" + remoteZid);
        this.remoteZid = remoteZid;
    }

    public int getRemoteZid()
    {
        return remoteZid;
    }

    private void setState(HandshakeState state)
    {
        checkArgument(this.state != state, "state new:" + state + " old:" + this.state);
        this.state = state;
    }

    public HandshakeState getState()
    {
        return state;
    }

    //
    // state transition
    //

    /**
     * Initiate the handshaking. Use <strong>ONLY</strong> if you have not received a
     * handshake message from the remote device and want to start the handshaking process
     * yourself
     *
     * @return {@code HandshakeReturn} to be handled by the caller
     */
    public HandshakeReturn startHandshaking()
    {
        checkLocalZidValid();
        checkState(state == NOT_STARTED, "invalid state to start handshaking state:" + state);

        l.trace("start handshaking s:{} l:{} r:{}", state, localZid, remoteZid);

        if (remoteZid == ZEPHYR_INVALID_CHAN_ID) {
            setState(WAITING_FOR_SYNACK);
            return SEND_SYN;
        } else {
            setState(WAITING_FOR_ACK);
            return SEND_SYNACK;
        }
    }

    /**
     * Run the handshaking system based on an incoming handshake message.
     * <br/>
     * <strong>IMPORTANT:</strong> do not attempt to use the handshaker before
     * setting the local zid via {@link ZephyrHandshakeEngine#setLocalZid(int)} (implies that
     * you have successfully registered with zephyr.
     *
     * @param incoming the incoming handshake message
     * @return the action to perform
     * @throws ExHandshakeFailed if the handshaking system is in a bad state, or
     * a bad handshake message was received
     */
    public HandshakeReturn consume(ZephyrHandshake incoming)
            throws ExHandshakeFailed
    {
        HandshakeType type = HandshakeType.fromPB(incoming);
        HandshakeReturn action;

        l.trace("consume s:{} l:{} r:{} t:{} ms:{} md:{}", state, localZid, remoteZid, type,
                incoming.getSourceZephyrId(), incoming.getDestinationZephyrId());

        try {
            switch (state)
            {
            case NOT_STARTED:
                action = handleInNotStartedState(type, incoming);
                break;
            case WAITING_FOR_SYNACK:
                action = handleInWaitingForSynAckState(type, incoming);
                break;
            case WAITING_FOR_ACK:
                action = handleInWaitingForAckState(type, incoming);
                break;
            case SUCCEEDED:
                action = CHECK_PEER; // caller should ping
                break;
            default:
                throw new ExHandshakeFailed("handshake system in state:" + state);
            }
        } catch (ExHandshakeFailed e) {
            state = FAILED;
            throw e;
        }

        return action;
    }

    private HandshakeReturn handleInNotStartedState(HandshakeType type, ZephyrHandshake handshake)
            throws ExHandshakeFailed
    {
        int source = handshake.getSourceZephyrId();

        if (type == SYN) {
            setRemoteZid(source);

            if (localZid != ZEPHYR_INVALID_CHAN_ID) {
                setState(WAITING_FOR_ACK);
                return SEND_SYNACK;
            }
        }

        return NO_ACTION;
    }

    private HandshakeReturn handleInWaitingForSynAckState(HandshakeType type, ZephyrHandshake handshake)
            throws ExHandshakeFailed
    {
        checkLocalZidValid();

        int source = handshake.getSourceZephyrId();
        int destination = handshake.getDestinationZephyrId();

        if (type == SYN) {
            if (source > localZid) { // they're newer; let them win
                setRemoteZid(source);
                setState(WAITING_FOR_ACK);
                return SEND_SYNACK;
            }
        } else if (type == SYNACK) {
            if (destination == localZid) {
                setRemoteZid(source);
                setState(SUCCEEDED);
                return SEND_ACK;
            }
        }

        return NO_ACTION;
    }

    private HandshakeReturn handleInWaitingForAckState(HandshakeType type, ZephyrHandshake handshake)
            throws ExHandshakeFailed
    {
        checkLocalZidValid();

        int destination = handshake.getDestinationZephyrId();

        if (type == ACK) {
            if (destination == localZid) {
                setState(SUCCEEDED);
            } else {
                throw new ExHandshakeFailed("wrong remotezid exp:" + remoteZid + " act:" + destination);
            }
        }

        return NO_ACTION;
    }

    //
    // message generation
    //

    private void checkLocalZidValid()
    {
        checkState(localZid != ZEPHYR_INVALID_CHAN_ID);
    }

    private void checkRemoteZidValid()
    {
        checkState(remoteZid != ZEPHYR_INVALID_CHAN_ID);
    }

    public ZephyrHandshake newSyn()
    {
        checkLocalZidValid();

        return ZephyrHandshake
                .newBuilder()
                .setSourceZephyrId(localZid)
                .setDestinationZephyrId(ZEPHYR_INVALID_CHAN_ID)
                .build();
    }

    public ZephyrHandshake newSynAck()
    {
        checkLocalZidValid();
        checkRemoteZidValid();

        return ZephyrHandshake
                .newBuilder()
                .setSourceZephyrId(localZid)
                .setDestinationZephyrId(remoteZid)
                .build();
    }

    public ZephyrHandshake newAck()
    {
        checkRemoteZidValid();

        return ZephyrHandshake
                .newBuilder()
                .setSourceZephyrId(ZEPHYR_INVALID_CHAN_ID)
                .setDestinationZephyrId(remoteZid)
                .build();
    }
}
