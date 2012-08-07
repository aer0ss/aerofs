package com.aerofs.lib.fsi;

import java.io.File;
import java.security.PrivateKey;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import com.aerofs.lib.C;
import com.aerofs.lib.JsonFormat;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Fsi.PBFSICall;
import com.aerofs.proto.Fsi.PBFSICall.Type;
import com.aerofs.proto.Fsi.PBSetPrivateKeyCall;
import com.aerofs.proto.Fsi.PBTransportPingCall;
import com.aerofs.proto.Fsi.PBTransportPingReply;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.google.protobuf.ByteString;

/**
 *
 * NOTE: THIS CLASS IS OBSOLETE. DO NOT USE IT IN NEW CODE.
 *
 * A utility class for FSI access and FSI protocol buffer manipulation.
 *
 * Note: Go to SPUtil for functions involving both FSI and SP.
 *
 * <p>
 * A majority of the methods are for PBPath object access and manipulation.
 * Note that {@link #equals(Common.PBPath, Common.PBPath)} and
 * {@link #hashCode(Common.PBPath)}
 * are preferred to PBPath's own {@code equals} and {@code hashCode} methods,
 * as the former exploits semantics of the data structure's and therefore is
 * expected to outperform the latter.
 *
 */
public class FSIUtil
{
    static final Logger l = Util.l(FSIUtil.class);

    public static boolean equals(PBSOCID pbsocid1, PBSOCID pbsocid2)
    {
        if (pbsocid1 == pbsocid2) return true;
        if (pbsocid1 == null || pbsocid2 == null) return false;

        if (!pbsocid1.getOid().equals(pbsocid2.getOid())) return false;
        if (pbsocid1.getSidx() != pbsocid2.getSidx()) return false;
        if (pbsocid1.getCid() != pbsocid2.getCid()) return false;
        return true;
    }

    public static int hashCode(PBSOCID pbsocid)
    {
        return pbsocid.getOid().hashCode();
    }

    public static int compare(PBPath p1, PBPath p2)
    {
        int i;
        for (i = 0; i < p1.getElemCount(); i++) {
            if (i >= p2.getElemCount()) return 1;
            int comp = p1.getElem(i).compareTo(
                    p2.getElem(i));
            if (comp != 0) return comp;
        }
        return i < p2.getElemCount() ? -1 : 0;
    }

    /**
     * Determines equality of two PBPath objects
     *
     * @param p1 the first path
     * @param p2 the second path
     * @return <code>true</code> if p1 and p2 refer to an identical path
     */
    public static boolean equals(PBPath p1, PBPath p2)
    {
        if (p1 == null ^ p2 == null) return false;

        if (p1 == null) return true;

        if (p1.getElemCount() != p2.getElemCount()) {
            return false;
        }

        for (int i = 0; i < p1.getElemCount(); i++) {
            if (!p1.getElem(i).equals(p2.getElem(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the hash code of PBPath
     * @param path
     * @return the hash code
     */
    public static int hashCode(PBPath path)
    {
        return path.getElemCount() == 0 ? 0 :path.getElem(path.getElemCount() -
                1).hashCode();
    }

    public static PBPath build(String path)
    {
        PBPath.Builder bd = PBPath.newBuilder();

        StringTokenizer st = new StringTokenizer(path, "/\\");
        if (!st.hasMoreTokens()) {
            bd.addElem(path);
        } else {
            while (st.hasMoreTokens()) bd.addElem(st.nextToken());
        }

        return bd.build();
    }

    public static PBPath build(String ... elems)
    {
        PBPath.Builder bd = PBPath.newBuilder();
        for (String elem : elems) bd.addElem(elem);
        return bd.build();
    }

    /**
     * Returns a new PBPath object that is identical to <code>parent</code>
     * except that <code>elems</code> are added to the end of its object path.
     *
     * <p>
     * Example: given <code>parent = { store_name: "haha", object_path: [ "a", "b" ]}</code>
     * and <code>elems = "c", "d", "e".</code> The return value would be
     * <code> { store_name: "haha", object_path: [ "a", "b", "c", "d", "e"].</code>
     *
     * @param parent the base of the new path
     * @param elems the suffix of the new path
     * @return a new path object consisting both the base and the suffix
     */
    public static PBPath build(PBPath parent, String ... elems)
    {
        PBPath.Builder bd = PBPath.newBuilder().mergeFrom(parent);
        for (String elem : elems) bd.addElem(elem);
        return bd.build();
    }

    /**
     * Convert PBPath to String. The output is in the format of
     * "a/b/c/d", where "a" is the store name and "b/c/d" is the object path
     * specified in the parameter
     * @param path the object to convert
     * @return the string representation of the path
     */
    public static String toString(PBPath path)
    {
        StringBuilder sb = new StringBuilder();
        for (String elem : path.getElemList()) {
            sb.append(File.separatorChar);
            sb.append(elem);
        }
        return sb.toString();
    }

    public static String toString(List<String> path)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String elem : path) {
            if (first) first = false;
            else sb.append(File.separatorChar);
            sb.append(elem);
        }
        return sb.toString();
    }

    /**
     * @return null if the path is empty
     */
    public static String getLast(PBPath path)
    {
        return path.getElemCount() > 0 ? path.getElem(path.getElemCount() - 1) :
                null;
    }

    /**
     * Returns a new PBPath object identical to the given path, except that
     * the last element is removed. This method is useful
     * to retrieve a path's parent. The caller must ensure that the specified path
     * contains at least one object path element.
     *
     * @param path
     * @return a new PBPath object representing the parameter's parent path
     */
    public static PBPath removeLast(PBPath path)
    {
        assert path.getElemCount() > 0;

        PBPath.Builder bd = PBPath.newBuilder();
        for (int i = 0; i < path.getElemCount() - 1; i++) {
            bd.addElem(path.getElem(i));
        }
        return bd.build();
    }

    /**
     * An internal helper method. Typical FSI users shall not use this method
     */
    public static void setDaemonPrivateKey_(PrivateKey privKey, FSIClient fsi) throws Exception
    {
        PBFSICall call = PBFSICall.newBuilder()
            .setType(Type.SET_PRIVATE_KEY)
            .setSetPrivateKey(PBSetPrivateKeyCall.newBuilder()
                    .setKey(ByteString.copyFrom(SecUtil.encodePrivateKey(privKey))))
            .build();

        fsi.rpc_(call);
    }

    public static String dumpStatForDefectLogging()
    {
        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            return dumpStatForDefectLogging(ritual);
        } finally {
            ritual.close();
        }
    }

    public static String dumpStatForDefectLogging(RitualBlockingClient ritual)
    {
        try {
            PBDumpStat template = PBDumpStat.newBuilder()
                    .setUpTime(0)
                    .addTp(PBTransport.newBuilder()
                            .setBytesIn(0)
                            .setBytesOut(0)
                            .addConnection("")
                            .setName("")
                            .setDiagnosis(""))
                    .setMisc("")
                    .build();

            PBDumpStat reply = ritual.dumpStats(template).getStats();

            return Util.realizeControlChars(JsonFormat.prettyPrint(reply));
        } catch (Exception e) {
            return "(cannot dumpstat: " + Util.e(e) + ")";
        }
    }

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
     * @param transportID may be null
     */
    public static void ping(DID did, FSIClient fsi, boolean ignoreOffline,
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
                Util.sleepUninterruptable(1 * C.SEC);
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
                l.info("ping rpc " + seqPrev + " " + seqNext + " " + forceNext);
                rtt = pingRPC(did, fsi, seqPrev, seqNext, forceNext, ignoreOffline);
                l.info("ping rpc returns " + rtt);
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

            } else if (rtt != null && rtt != C.TRANSPORT_DIAGNOSIS_STATE_PENDING) {
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
                assert rtt == null || rtt == C.TRANSPORT_DIAGNOSIS_STATE_PENDING;
                // keep waiting
                newPing = false;
                newSeq = false;
                forceNext = false;
                rtt = null;
                samples--;
            }

            cb.update(offline, rtt, samples);

            if (sleep) Util.sleepUninterruptable(1 * C.SEC);
        }
    }


    private static Long pingRPC(DID did, FSIClient fsi, int seqPrev,
            int seqNext, boolean forceNext, boolean ignoreOffline) throws Exception
    {
        PBTransportPingCall.Builder bd = PBTransportPingCall.newBuilder()
            .setDeviceId(did.toPB())
            .setSeqPrev(seqPrev)
            .setSeqNext(seqNext)
            .setForceNext(forceNext)
            .setIgnoreOffline(ignoreOffline);
        PBFSICall call = PBFSICall.newBuilder()
            .setType(Type.TRANSPORT_PING)
            .setTransportPing(bd)
            .build();
        PBTransportPingReply reply = fsi.rpc_(call).getTransportPing();
        return reply.hasRtt() ? reply.getRtt() : null;
    }
}
