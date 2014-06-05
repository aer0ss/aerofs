/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.IntegerID;
import com.aerofs.daemon.event.net.EIPulseStopped;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Transport;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.aerofs.proto.Transport.PBTPHeader;
import static com.aerofs.proto.Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL;
import static com.aerofs.proto.Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_REPLY;

/**
 * Stores a set of in-progress pulses, contains a number of pulse-related
 * utility functions, classes and interfaces, and implements some of the logic
 * related to pulsing.
 * <br></br>
 * Important points:
 * <ul>
 *     <li>We have to concept of a pulse sequence. A pulse sequence starts when
 *     the core sends a pulse request for a remote peer to the transport.
 *     When the transport services this request it assigns it a
 *     <strong>pulse sequence id</strong>. Every subsequent pulse to the remote
 *     peer uses this pulse sequence id. This pulse sequence is stopped when the
 *     remote peer's presence changes (via disconnection, pulse response, or other
 *     form of presence change).</li>
 *     <li>Each pulse message going out has a monotonically increasing pulse id.
 *     This is not guaranteed to be globally unique.</li>
 * </ul>
 */
public class PulseManager
{
    /**
     * Add a {@link GenericPulseDeletionWatcher} to the list of listeners
     * to be notified if a pulse was deleted.
     *
     * @param tp {@link ITransport} that owns this <code>PulseManager</code>
     * @param tpsink <code>IEvent</code> into which the transport enqueues events to the core
     */
    public synchronized void addGenericPulseDeletionWatcher(ITransport tp, IBlockingPrioritizedEventSink<IEvent> tpsink)
    {
        addPulseDeletionWatcher(new GenericPulseDeletionWatcher(tp, tpsink));
    }

    /**
     * Add a class to the list of listeners to be notified if an in-progress
     * pulse is deleted.
     *
     * @param w an object that implements {@link IPulseDeletionWatcher}
     */
    public synchronized void addPulseDeletionWatcher(IPulseDeletionWatcher w)
    {
        _watchers.add(w);
    }

    /**
     * Add an entry indicating that we will send <code>TRANSPORT_CHECK_PULSE_CALL</code>
     * messages to a peer. This method has the following semantics:
     * <ul>
     *     <li>If <code>prevtok</code> is null, we assume that this is the
     *     start of a new pulse sequence. We assert that there is no outstanding
     *     pulse and then create a new pulse sequence id and a new pulse id.</li>
     *     <li>If <code>prevtok</code> is non-null this means that this request
     *     is part of an existing pulse sequence. We then create the entry
     *     <em>only if there is an outstanding unanswered pulse!</em>. If there
     *     is no outstanding unanswered pulse, this means that a presence change
     *     has invalidated this pulse sequence.</li>
     * </ul>
     *
     * @param did {@link DID} of the remote peer being pulsed
     * @param prevtok {@link PulseToken} representing this pulse sequence (use
     * null to signal a new pulse sequence)
     * @return null if <code>prevtok</code> is non-null and there is no outstanding
     * pulse or if an old pulse sequence was invalidated and a new one started up.
     * Otherwise a {@link AddPulseResult} object that contains the
     * <code>PulseToken</code> representing this pulse sequence and a new pulse id.
     */
    public synchronized AddPulseResult addInProgressPulse(DID did, PulseToken prevtok)
    {
        int tokid = 0;

        if (prevtok != null) {
            assert _pulsetokens.containsKey(did) : ("d:" + did + " msgid but no tokid"); // invariant: pulse token => pulse seq id

            if (prevtok.id() != _pulsetokens.get(did)) return null; // old handler
            if (!_msgids.containsKey(did)) return null; // pulse ended

            tokid = _pulsetokens.get(did);
        } else {
            assert !_msgids.containsKey(did) : ("d:" + did + " null prevtok but msgid"); // core signalled us twice

            if (_pulsetokens.containsKey(did)) {
                tokid = _pulsetokens.get(did);
                tokid++;
            }
        }

        int msgid = _pulsemsgid++;

        _pulsetokens.put(did, tokid);
        _msgids.put(did, msgid);

        l.info("{} generate pulse id {} for pulse seq num {}", did, msgid, tokid);

        return new AddPulseResult(tokid, msgid);
    }

    /**
     * Retrieves the outstanding pulse id for a <code>did</code>
     *
     * @param did {@link com.aerofs.base.id.DID} of the remote peer for whom you want to get a pulse id
     * @return null if there is no outstanding pulse for this remote peer.
     * <code>Integer</code> pulse id otherwise.
     */
    public synchronized Integer getInProgressPulse(DID did)
    {
        return _msgids.get(did);
    }

    /**
     * Deletes the outstanding pulse id for a <code>did</code>. Indicates that we
     * are no longer sending <code>TRANSPORT_CHECK_PULSE_CALL</code> messages to
     * a peer
     *
     * @param did {@link DID} of the remote peer for whom you are deleting pulse ids
     * @return true if there was an outstanding pulse and it was deleted, false
     * otherwise
     */
    public synchronized boolean delInProgressPulse(DID did)
    {
        return _msgids.remove(did) != null;
    }

    /**
     * Convenience method a transport or event handler can use to stop pulsing
     * a remote peer
     *
     * @param did {@link DID} of the remote peer for whom we want to stop pulsing
     * @param forcenotify true if we want to notify the core regardless of whether
     * an outstanding pulse exists for this remote peer or not, false if we only
     * want to notify the core if an outstanding pulse exists.
     * @return true if a pulse was stopped, false if no outstanding pulse existed
     */
    public synchronized boolean stopPulse(DID did, boolean forcenotify)
    {
        boolean existed = delInProgressPulse(did);
        if (existed || forcenotify) notifyWatchers_(did);
        if (existed) l.info("{} stopped pulse", did);
        return existed;
    }

    /**
     * Removes a peer from the set of peers which have outstanding pulses.
     * Remove occurs only if the pulse id in the received
     * <code>TRANSPORT_CHECK_PULSE_REPLY</code> contains the pulse id of the
     * <strong>last</strong> outgoing <code>TRANSPORT_CHECK_PULSE_CALL</code>.
     *
     * @param did {@link DID} of the remote peer from whom the pulse id was received
     * @param msgpulseid pulse id that was sent to us from this remote peer
     */
    public synchronized void processIncomingPulseId(DID did, int msgpulseid)
    {
        l.info("{} rcv pulse rep with pulse id {}", did, msgpulseid);

        Integer pulseid = getInProgressPulse(did);
        if (pulseid == null) {
            l.info("{} no in-progress pulse; drop pulse rep", did);
            return;
        }

        //
        // after this point do not touch internal data structures
        // watchers may call back into us to remove pulses
        //

        if (pulseid == msgpulseid) {
            l.info("{} matched pulse id {} - del pulse + notify core", did, msgpulseid);
            stopPulse(did, false);
        }
    }

    /**
     * Notify all {@link IPulseDeletionWatcher} objects that we have deleted an
     * in-progress pulse for a remote peer
     *
     * @param did {@link DID} of the remote peer for whom the pulse was deleted
     */
    private void notifyWatchers_(DID did)
    {
        for (IPulseDeletionWatcher w : _watchers) {
            w.pulsedeleted_(did);
        }
    }

    //
    // utility functions
    //

    /**
     * Generate a new <code>TRANSPORT_CHECK_PULSE_CALL</code> message
     *
     * @param pulseid pulse id to encode into the message
     * @return {@link PBTPHeader} of type <code>TRANSPORT_CHECK_PULSE_CALL</code>
     */
    public static PBTPHeader newCheckPulse(int pulseid)
    {
        return PBTPHeader.newBuilder()
            .setType(TRANSPORT_CHECK_PULSE_CALL)
            .setCheckPulse(Transport.PBCheckPulse.newBuilder()
                    .setPulseId(pulseid))
            .build();
    }

    /**
     * Generate a {@link PBTPHeader} of type <code>TRANSPORT_CHECK_PULSE_REPLY</code>
     *
     * @param msgpulseid pulse id to encode into the <code>TRANSPORT_CHECK_PULSE_REPLY</code>
     * @return PBTPHeader of type <code>TRANSPORT_CHECK_PULSE_REPLY</code>
     */
    public static PBTPHeader newCheckPulseReply(int msgpulseid)
    {
        return PBTPHeader.newBuilder().
            setType(TRANSPORT_CHECK_PULSE_REPLY).
            setCheckPulse(Transport.PBCheckPulse.newBuilder().
                setPulseId(msgpulseid)).
            build();
    }

    //
    // types
    //

    /**
     * Represents the return value of an <code>addInProgressPulse</code> call.
     * Created because Java doesn't have multi-value returns. [sigh] I wish I
     * were coding in Go.
     */
    public static class AddPulseResult
    {
        AddPulseResult(int tokid, int msgid)
        {
            this.tok = new PulseToken(tokid);
            this.msgid = msgid;
        }

        public PulseToken tok()
        {
            return tok;
        }

        public int msgid()
        {
            return msgid;
        }

        private final PulseToken tok;
        private final int msgid;
    }

    /**
     * Represents a single pulse sequence
     *
     * When we start pulsing a remote peer, we generate an pulse sequence id.
     * This pulse id remains live regardless of how many times the pulses fail.
     * It is only invalidated when the remote peer's presence changes to online
     * or offline.
     */
    public static class PulseToken extends IntegerID
    {
        PulseToken(int pulseseq)
        {
            super(pulseseq);
        }

        public int id()
        {
            return getInt();
        }
    }

    /**
     * To be implemented by objects that want to be notified when an outstanding
     * pulse to a remote peer is deleted
     */
    public static interface IPulseDeletionWatcher
    {
        /**
         * Called when the <code>PulseManager</code> deletes an outstanding pulse
         *
         * @param did {@link DID} of the remote peer for whom the pulse was deleted
         */
        public void pulsedeleted_(DID did);
    }

    /**
     * Utility class that simply notifies the core when a pulse is deleted. This
     * is the absolute minimum that an <code>IPulseDeletionWatcher</code> has to
     * do when the <code>pulsedeleted_</code> method is called.
     */
    public static class GenericPulseDeletionWatcher implements IPulseDeletionWatcher
    {
        /**
         * Constructor
         *
         * @param tp {@link ITransport} via which pulses are being sent
         * @param tpsink <code>IEvent</code> into which the transport enqueues events to the core
         */
        public GenericPulseDeletionWatcher(ITransport tp, IBlockingPrioritizedEventSink<IEvent> tpsink)
        {
            this._tp = tp;
            this._tpsink = tpsink;
        }

        @Override
        public void pulsedeleted_(DID did)
        {
            _tpsink.enqueueBlocking(new EIPulseStopped(did, _tp), Prio.LO);
        }

        // FIXME: have to define an equals() method, for this, the transport and the transport's sink

        private final ITransport _tp;
        private final IBlockingPrioritizedEventSink<IEvent> _tpsink;
    }

    //
    //  members
    //

    private int _pulsemsgid = Util.rand().nextInt(Integer.MAX_VALUE);
    private final Set<IPulseDeletionWatcher> _watchers = new HashSet<IPulseDeletionWatcher>();
    private final Map<DID, Integer> _msgids = new HashMap<DID, Integer>(); // msg ids that are still unanswered
    private final Map<DID, Integer> _pulsetokens = new HashMap<DID, Integer>();

    private final Logger l = Loggers.getLogger(PulseManager.class);
}
