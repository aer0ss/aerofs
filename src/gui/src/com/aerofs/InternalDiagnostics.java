/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.JsonFormat;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Ritual.TransportPingReply;
import org.slf4j.Logger;

import com.aerofs.base.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDeviceOffline;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class provide helper methods around Ritual's private API (ping, dump, ...)
 */
public class InternalDiagnostics
{
    static final Logger l = Loggers.getLogger(InternalDiagnostics.class);

    public static interface IPingCallback {
        boolean toStop();
        boolean toSuspend();
        long getTimeout();
        /**
         * @param rtt set to:
         *      1) null to avoid updating rtt, or
         *      2) MAX_VALUE if timed out,
         *      ignored if offline
         */
        void update(boolean offline, final Long rtt, int samples);
    }

    /**
     * see IEOTransportPing
     */
    public static void ping(RitualBlockingClient ritual, DID did, boolean ignoreOffline,
            IPingCallback cb)
        throws Exception
    {
        int seqPrev = 0;            // its initial value doesn't matter
        int seqNext = 0;

        boolean newPing = true;
        boolean newSeq = true;
        boolean forceNext = true;
        boolean rttMaybeNull = true;

        long lastPing = 0;          // its initial value doesn't matter
        int samples = 0;

        while (true) {

            while (cb.toSuspend() && !cb.toStop()) {
                ThreadUtil.sleepUninterruptable(1 * C.SEC);
            }

            if (cb.toStop()) break;

            if (newPing) lastPing = System.currentTimeMillis();

            if (newSeq) {
                seqPrev = seqNext;
                seqNext = Util.rand().nextInt();
            }

            boolean offline;
            Long rtt = null;
            try {
                l.debug("ping rpc " + seqPrev + " " + seqNext + " " + forceNext);
                rtt = pingRPC(ritual, did, seqPrev, seqNext, forceNext, ignoreOffline);
                l.debug("ping rpc returns " + rtt);
                offline = false;
            } catch (ExDeviceOffline e) {
                rttMaybeNull = true;
                offline = true;
            }

            if (!rttMaybeNull && rtt == null) {
                throw new Exception("too many pings at the same time or peer" +
                        " transport changed during a ping");
            }

            rttMaybeNull = false;

            if (forceNext) {
                seqPrev = seqNext;
                seqNext = Util.rand().nextInt();
            }

            samples++;
            boolean sleep = true;

            if (offline) {
                newPing = true;
                newSeq = false;
                // to prevent throwing exception above
                forceNext = true;
                rttMaybeNull = true;

            } else if (rtt != null && rtt != LibParam.TRANSPORT_DIAGNOSIS_STATE_PENDING) {
                // has received pong
                assert rtt >= 0;
                newPing = true;
                newSeq = true;
                forceNext = false;

            } else if (System.currentTimeMillis() - lastPing > cb.getTimeout()) {
                // timed out
                newPing = true;
                newSeq = false;
                forceNext = true;
                rtt = Long.MAX_VALUE;
                sleep = false;

            } else {
                checkState(rtt == null || rtt == LibParam.TRANSPORT_DIAGNOSIS_STATE_PENDING);
                // keep waiting
                newPing = false;
                newSeq = false;
                forceNext = false;
                rtt = null;
                samples--;
            }

            cb.update(offline, rtt, samples);

            if (sleep) ThreadUtil.sleepUninterruptable(1 * C.SEC);
        }
    }

    public static String dumpFullDaemonStatus(RitualBlockingClient ritual)
            throws Exception
    {
        PBDumpStat template = PBDumpStat.newBuilder()
                .setUpTime(0)
                .addTransport(PBTransport.newBuilder()
                        .setBytesIn(0)
                        .setBytesOut(0)
                        .addConnection("")
                        .setName("")
                        .setDiagnosis(""))
                .setMisc("")
                .build();

        PBDumpStat reply = ritual.dumpStats(template).getStats();

        return Util.realizeControlChars(JsonFormat.prettyPrint(reply));
    }

    private static Long pingRPC(RitualBlockingClient ritual, DID did, int seqPrev,
            int seqNext, boolean forceNext, boolean ignoreOffline) throws Exception
    {
        TransportPingReply reply = ritual.transportPing(did.toPB(), seqPrev, seqNext, forceNext,
                ignoreOffline);
        return reply.hasRtt() ? reply.getRtt() : null;
    }
}
