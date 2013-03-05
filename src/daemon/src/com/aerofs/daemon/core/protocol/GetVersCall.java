package com.aerofs.daemon.core.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.sv.client.SVClient;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.SenderFilters.SenderFilterAndIndex;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.ver.ImmigrantTickRow;
import com.aerofs.daemon.lib.db.ver.NativeTickRow;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetVersCall;
import com.aerofs.proto.Core.PBGetVersReply;
import com.aerofs.proto.Core.PBGetVersReplyBlock;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import com.google.protobuf.AbstractMessageLite;

import javax.annotation.Nullable;

import static com.google.common.collect.Lists.newArrayListWithCapacity;

public class GetVersCall
{
    private static final Logger l = Loggers.getLogger(GetVersCall.class);

    private static class TickPair {
        Tick _native;
        Tick _imm;
    }

    private final NativeVersionControl _nvc;
    private final ImmigrantVersionControl _ivc;
    private final RPC _rpc;
    private final NSL _nsl;
    private final GetVersReply _pgvr;
    private final Metrics _m;
    private final OutgoingStreams _oss;
    private final MapSIndex2Store _sidx2s;
    private final IPulledDeviceDatabase _pulleddb;
    private final TokenManager _tokenManager;
    // The following dependency exists only to use and log OAs in
    //  enforceTicksAreMonotonicallyIncreasing(...)
    // TODO (MJ) remove when the use of OAs is not necessary for repairing the db
    private final DirectoryService _ds;
    // The following dependency exists only to repair the db in enforceTicksAreMonotonicallyIncreasing()
    // TODO (MJ) remove when repairing the db is disabled
    private final TransManager _tm;

    @Inject
    public GetVersCall(OutgoingStreams oss, Metrics m, GetVersReply pgvr, NSL nsl, RPC rpc,
            NativeVersionControl nvc, ImmigrantVersionControl ivc, MapSIndex2Store sidx2s,
            IPulledDeviceDatabase pddb, TokenManager tokenManager, DirectoryService ds,
            TransManager tm)
    {
        _oss = oss;
        _m = m;
        _pgvr = pgvr;
        _rpc = rpc;
        _nsl = nsl;
        _nvc = nvc;
        _ivc = ivc;
        _sidx2s = sidx2s;
        _pulleddb = pddb;
        _tokenManager = tokenManager;
        _ds = ds;
        _tm = tm;
    }

    public void rpc_(SIndex sidx, DID didTo, Token tk)
        throws Exception
    {
        Version vKwlgLocalES = _nvc.getKnowledgeExcludeSelf_(sidx);
        Version vImmKwlgLocalES = _ivc.getKnowledgeExcludeSelf_(sidx);

        HashMap<DID, TickPair> map = Maps.newHashMap();
        for (Entry<DID, Tick> en : vImmKwlgLocalES.getAll_().entrySet()) {
            TickPair tp = new TickPair();
            tp._imm = en.getValue();
            map.put(en.getKey(), tp);
        }
        for (Entry<DID, Tick> en : vKwlgLocalES.getAll_().entrySet()) {
            TickPair tp = map.get(en.getKey());
            if (tp == null) {
                tp = new TickPair();
                map.put(en.getKey(), tp);
            }
            tp._native = en.getValue();
        }

        PBGetVersCall.Builder bd = PBGetVersCall.newBuilder();
        for (Entry<DID, TickPair> en : map.entrySet()) {
            bd.addDeviceId(en.getKey().toPB());
            TickPair tp = en.getValue();
            bd.addKnowledgeTick(tp._native == null ? 0 : tp._native.getLong());
            bd.addImmigrantKnowledgeTick(tp._imm == null ? 0 : tp._imm.getLong());
        }

        // If Store s has never been pulled from didTo, then we need all
        // object information from BASE onward.
        if (!_pulleddb.contains_(sidx, didTo)) bd.setFromBase(true);

        PBCore call = CoreUtil.newCall(Type.GET_VERS_CALL).setGetVersCall(bd).build();

        DigestedMessage msg = _rpc.do_(didTo, sidx, call, tk, "gv for " + sidx);
        _pgvr.processReply_(msg, tk);
    }

    private static class DeviceEntry {
        DID _did;
        boolean _hasVersions;
        Tick _tickKwlgRemote;
        Tick _tickKwlgLocalES;  // ZERO if _did == Cfg.did()
        boolean _hasImmVersions;
        Tick _tickImmKwlgRemote;
        Tick _tickImmKwlgLocalES;  // ZERO if _did == Cfg.did()
    }

    public void processCall_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().hasGetVersCall(), PBGetVersCall.class);
        PBGetVersCall pb = msg.pb().getGetVersCall();
        assert pb.getDeviceIdCount() == pb.getKnowledgeTickCount() &&
                pb.getDeviceIdCount() == pb.getImmigrantKnowledgeTickCount();

        l.debug("process from " + msg.ep() + " 4 " + msg.sidx());

        Version vKwlgRemote = Version.empty();
        Version vImmKwlgRemote = Version.empty();
        // Load vKwlgRemote and vImmKwlgRemote with the contents of msg
        for (int i = 0; i < pb.getDeviceIdCount(); i++) {
            DID did = new DID(pb.getDeviceId(i));
            long tick = pb.getKnowledgeTick(i);
            long tickImm = pb.getImmigrantKnowledgeTick(i);
            assert tick != 0 || tickImm != 0;
            if (tick != 0) vKwlgRemote.set_(did, tick);
            if (tickImm != 0) vImmKwlgRemote.set_(did, tickImm);
        }

        SIndex sidx = msg.sidx();
        Version vKwlgLocalES = _nvc.getKnowledgeExcludeSelf_(sidx);
        Version vImmKwlgLocalES = _ivc.getKnowledgeExcludeSelf_(sidx);
        Set<DID> verDids = _nvc.getAllVersionDIDs_(sidx);
        Set<DID> immVerDids = _ivc.getAllVersionDIDs_(sidx);
        Set<DID> didsExcludeRequester = new HashSet<DID>(verDids);
        didsExcludeRequester.addAll(immVerDids);
        didsExcludeRequester.remove(msg.did());

        List<DeviceEntry> desExcludeRequester =
                newArrayListWithCapacity(didsExcludeRequester.size());
        for (DID did : didsExcludeRequester) {
            DeviceEntry de = new DeviceEntry();
            de._did = did;
            de._hasVersions = verDids.contains(did);
            de._tickKwlgRemote = vKwlgRemote.get_(did);
            de._tickKwlgLocalES = vKwlgLocalES.get_(did);
            de._hasImmVersions = immVerDids.contains(did);
            de._tickImmKwlgRemote = vImmKwlgRemote.get_(did);
            de._tickImmKwlgLocalES = vImmKwlgLocalES.get_(did);
            // local knowledge ticks corresponding to the local device
            // must be zero. The tick of a non-local device _may_ be zero.
            assert !did.equals(Cfg.did()) || (de._tickKwlgLocalES.equals(Tick.ZERO) &&
                    de._tickImmKwlgLocalES.equals(Tick.ZERO));
            desExcludeRequester.add(de);
        }

        boolean fromBase = pb.getFromBase();
        Store s = _sidx2s.getThrows_(msg.sidx());
        SenderFilterAndIndex sfi = s.senderFilters().get_(msg.did(), fromBase);

        if (l.isDebugEnabled()) l.debug("send 2 " + msg.ep() + " 4 " + msg.sidx() +
                " l " + vKwlgLocalES + " r " + vKwlgRemote + " fs " +
                (sfi == null ? null : sfi._filter));

        ////////
        // write the header

        PBGetVersReply.Builder bd = PBGetVersReply.newBuilder();
        if (sfi != null) {
            bd.setSenderFilter(sfi._filter.toPB())
                .setSenderFilterIndex(sfi._sfidx.getLong())
                .setSenderFilterUpdateSeq(sfi._updateSeq);
        }
        PBCore core = CoreUtil.newReply(msg.pb())
                .setGetVersReply(bd.build())
                .build();
        ByteArrayOutputStream os = write_(null, core);

        ////////
        // write blocks

        BlockSender sender = new BlockSender(msg.sidx(), msg.ep(), core.getRpcid(),
                CoreUtil.typeString(core), os);
        try {
            sendBlocks_(sender, msg.sidx(), desExcludeRequester);
        } finally {
            sender.doFinally_();
        }
    }

    /**
     * @param os null to force allocation of a new output stream
     * @return a different stream than os if the old stream is full, and thus a
     * call to flush_() is required.
     */
    private ByteArrayOutputStream write_(@Nullable ByteArrayOutputStream os,
            AbstractMessageLite msg) throws IOException
    {
        int len = msg.getSerializedSize();
        int maxUcastLen = _m.getMaxUnicastSize_();
        // tune down ENTRIES_PER_BLOCK if the assertion fails
        assert len < maxUcastLen;

        int osLen = os == null ? maxUcastLen : os.size();

        // Integer.SIZE is for the delimiter
        if (osLen + Integer.SIZE + len > maxUcastLen) {
            ByteArrayOutputStream os2 = new ByteArrayOutputStream(
                    _m.getMaxUnicastSize_());
            msg.writeDelimitedTo(os2);
            return os2;
        } else {
            msg.writeDelimitedTo(os);
            return os;
        }
    }

    private static final int ENTRIES_PER_BLOCK = 50;

    private class BlockSender
    {
        private final SIndex _sidx;
        private final Endpoint _ep;
        private final int _rpcid;
        private final String _msgType;
        private ByteArrayOutputStream _os;

        private OutgoingStream _stream;     // null for atomic messages
        private Token _tk;                  // null for atomic messages
        private boolean _streamOkay;        // invalid for atomic messages

        BlockSender(SIndex sidx, Endpoint ep, int rpcid, String msgType,
                ByteArrayOutputStream os)
        {
            _sidx = sidx;
            _ep = ep;
            _rpcid = rpcid;
            _msgType = msgType;
            _os = os;
        }

        /**
         * @param iter the method may close the iter if necessary. a null iter
         * indicates that there is no active iter
         */
        void writeBlock_(PBGetVersReplyBlock block, @Nullable IDBIterator<?> iter)
                throws Exception
        {
            ByteArrayOutputStream os2 = write_(_os, block);

            if (os2 != _os) {
                if (_stream == null) {
                    _tk = _tokenManager.acquireThrows_(Cat.SERVER, "GVSendReply");
                    _stream = _oss.newStream(_ep, _sidx, _tk);
                }

                if (iter != null) iter.close_();
                _stream.sendChunk_(_os.toByteArray());
                _os = os2;
            }
        }

        /**
         * called after the last block has been sent
         */
        void done_() throws Exception
        {
            if (_stream == null) {
                _nsl.sendUnicast_(_ep.did(), _sidx, _msgType, _rpcid, _os);
            } else {
                _stream.sendChunk_(_os.toByteArray());
                _streamOkay = true;
            }
        }

        /**
         * called in a finally block that ends the life cycle of 'this' object
         */
        void doFinally_()
        {
            if (_stream != null) {
                if (_streamOkay) _stream.end_();
                else _stream.abort_(InvalidationReason.INTERNAL_ERROR);
            }

            if (_tk != null) _tk.reclaim_();
        }
    }

    private void sendBlocks_(BlockSender sender, SIndex sidx, Collection<DeviceEntry> desExcludeTo)
            throws Exception
    {
        int deviceCount = 0;
        for (DeviceEntry de : desExcludeTo) {
            PBGetVersReplyBlock.Builder bdBlock = PBGetVersReplyBlock
                    .newBuilder()
                    .setDeviceId(de._did.toPB());
            boolean hasNonEntryFields = false;
            int entryCount = 0;

            ////////
            // add versions

            if (de._hasVersions) {
                Tick tickLast = Tick.ZERO;
                SOCID socidLast = null;
                IDBIterator<NativeTickRow> iter = _nvc.getMaxTicks_(sidx, de._did,
                        de._tickKwlgRemote);
                try {
                    while (iter.next_()) {
                        NativeTickRow tr = iter.get_();

                        // ticks must be monotonically increasing
                        SOCID socid = new SOCID(sidx, tr._oid, tr._cid);
                        enforceTicksAreMonotonicallyIncreasing(tickLast, socidLast, tr._tick, socid,
                                de._did);
                        socidLast = socid;
                        tickLast = tr._tick;

                        bdBlock.addObjectId(tr._oid.toPB());
                        bdBlock.addComId(tr._cid.getInt());
                        bdBlock.addTick(tr._tick.getLong());

                        if (++entryCount == ENTRIES_PER_BLOCK) {
                            sender.writeBlock_(bdBlock.build(), iter);
                            bdBlock = PBGetVersReplyBlock.newBuilder();
                            entryCount = 0;
                            if (iter.closed_()) {
                                iter = _nvc.getMaxTicks_(sidx, de._did, tickLast);
                            }
                        }
                    }
                } finally {
                    iter.close_();
                }

                Tick tickKwlgLocal = de._did.equals(Cfg.did()) ? tickLast :
                    de._tickKwlgLocalES;
                if (tickKwlgLocal.getLong() > de._tickKwlgRemote.getLong()) {
                    bdBlock.setKnowledgeTick(tickKwlgLocal.getLong());
                    hasNonEntryFields = true;
                }
            }

            ////////
            // add immigrant versions

            if (de._hasImmVersions) {
                Tick immTickLast = Tick.ZERO;
                IDBIterator<ImmigrantTickRow> iter = _ivc.getMaxTicks_(sidx, de._did,
                        de._tickImmKwlgRemote);
                try {
                    while (iter.next_()) {
                        ImmigrantTickRow tr = iter.get_();
                        bdBlock.addImmigrantObjectId(tr._oid.toPB());
                        bdBlock.addImmigrantComId(tr._cid.getInt());
                        bdBlock.addImmigrantImmTick(tr._immTick.getLong());
                        bdBlock.addImmigrantDeviceId(tr._did.toPB());
                        bdBlock.addImmigrantTick(tr._tick.getLong());
                        immTickLast = tr._immTick;

                        if (++entryCount == ENTRIES_PER_BLOCK) {
                            sender.writeBlock_(bdBlock.build(), iter);
                            bdBlock = PBGetVersReplyBlock.newBuilder();
                            entryCount = 0;
                            if (iter.closed_()) {
                                iter = _ivc.getMaxTicks_(sidx, de._did, immTickLast);
                            }
                        }

                    }
                } finally {
                    iter.close_();
                }

                Tick tickImmKwlgLocal = de._did.equals(Cfg.did()) ? immTickLast :
                    de._tickImmKwlgLocalES;
                if (tickImmKwlgLocal.getLong() > de._tickImmKwlgRemote.getLong()) {
                    bdBlock.setImmigrantKnowledgeTick(tickImmKwlgLocal.getLong());
                    hasNonEntryFields = true;
                }
            }

            if (++deviceCount == desExcludeTo.size()) {
                bdBlock.setIsLastBlock(true);
                hasNonEntryFields = true;
            }

            if (entryCount != 0 || hasNonEntryFields) {
                sender.writeBlock_(bdBlock.build(), null);
            }
        }

        if (desExcludeTo.isEmpty()) {
            // there is no devices. the above big loop is not executed at all
            sender.writeBlock_(PBGetVersReplyBlock.newBuilder()
                .setIsLastBlock(true)
                .build(), null);
        }

        sender.done_();
    }

    /**
     * Ticks from getMaxTicks are assumed to be monotonically increasing. They are no longer
     * strictly increasing due to the aliasing algorithm (see Comment A in
     * NativeVersionControl)
     * This method asserts that monotonicity, logs useful aliasing information,
     * and attempts to repair the db if two consecutive ticks are equal,
     * and one of them can be deleted. The latter is a temporary measure to clean up db's prior
     * to our bug fix.
     * @param tickLast Tick from the previous iteration
     * @param tick Tick from this iteration
     * @param did Device ID for the bucket of ticks
     */
    private void enforceTicksAreMonotonicallyIncreasing(final Tick tickLast, final SOCID socidLast,
            final Tick tick, final SOCID socid, final DID did)
            throws SQLException, ExAborted
    {
        // If the ticks are strictly increasing, enforcement succeeded, exit early
        if (tickLast.getLong() < tick.getLong()) return;

        // Collect logging information for any failed assertions or defect reports
        final String loggedData = "socidLast " + socidLast + " socid " + socid
                + " tickLast " + tickLast + " tick " + tick;

        // Do not permit decreasing tick order (i.e. enforce monotonic increments)
        assert !(tickLast.getLong() > tick.getLong()) : loggedData;

        // We don't permit any duplicate ticks to be of the alias type (odd-valued)
        assert !tick.isAlias() : loggedData;

        // Get the target (or original) OA for the two SOCIDs to determine whether the DB needs
        // repairing
        final OA oa = _ds.getAliasedOANullable_(socid.soid());
        final OA oaLast = _ds.getAliasedOANullable_(socidLast.soid());
        final String loggedDataWithOA = loggedData + " oaLast [" + oaLast + "] oa [" + oa + "]";

        // It should not be possible for a (locally known) alias object to have META data.
        // However, this case can still be observed in devices that received non-alias ticks
        // incorrectly for alias objects (see NativeVersionControl.tickReceived()).
        // We repair these devices' DBs by deleting the tick for the known aliased object.
        if (oa != null && !oa.soid().equals(socid.soid())) {
            l.warn(oa + " is the target of " + socid);

            // Assert that oaLast is the target of socid
            assert oaLast != null && oa.soid().equals(oaLast.soid()) : loggedDataWithOA;

            // delete the tick for socid
            deleteDuplicateTick(socid, new SOCID(oa.soid(), socid.cid()), did, tick,
                    loggedDataWithOA);

        } else if (oaLast != null && !oaLast.soid().equals(socidLast.soid())) {
            l.warn(oaLast + " is the target of " + socidLast);

            // Assert that oa is the target of socidLast
            assert oa != null && oa.soid().equals(oaLast.soid()) : loggedDataWithOA;

            // delete the tick for socidLast
            deleteDuplicateTick(socidLast, new SOCID(oaLast.soid(), socidLast.cid()), did, tick,
                    loggedDataWithOA);
        }

        // If execution arrived here, either
        // 1) One or both of the OAs are null, implying >= 1 of the duplicate ticks is a KML; OR
        // 2) Both OIDs have a downloaded component (ie the OAs are non-null) but
        //    neither OID seems to be aliased to the other.
        // Both are legitimate cases that require no database repair. See Comment A in
        // NativeVersionControl
    }

    private void deleteDuplicateTick(SOCID socidToDelete, SOCID socidTarget, DID did, Tick tick,
            String loggedData)
            throws SQLException, ExAborted
    {
        // The alias object socid should have no oa
        assert !_ds.hasOA_(socidToDelete.soid()) : socidToDelete + " " + loggedData;

        Version vToDelete = Version.of(did, tick);

        // Assume that vToDelete is entirely duplicated in the target object's versions.
        // (so it is safe to simply delete it from the alias object (either KML or local))
        Version vAllTarget = _nvc.getAllVersions_(socidTarget);
        assert vToDelete.isEntirelyShadowedBy_(vAllTarget) : vToDelete + " " + vAllTarget
                + " " + loggedData;

        Trans t = _tm.begin_();
        try {
            if (vToDelete.isEntirelyShadowedBy_(_nvc.getKMLVersion_(socidToDelete))) {
                // vToDelete is a KML for socidToDelete
                _nvc.deleteKMLVersionPermanently_(socidToDelete, vToDelete, t);

                loggedData = "Delete KML " + vToDelete + " of " + socidToDelete + ". " + loggedData;

            } else {
                // vToDelete is not a KML (as it failed the previous branch)
                // so assert that it is in the local versions of socidToDelete
                Version vAllLocalVersions = _nvc.getAllLocalVersions_(socidToDelete);
                assert vToDelete.isEntirelyShadowedBy_(vAllLocalVersions)
                        : vToDelete + " " + vAllLocalVersions + " " + loggedData;

                // Assume the branch of the version to delete is MASTER
                // (otherwise I'm unsure how to resolve this)
                SOCKID sockidToDelete = new SOCKID(socidTarget);
                Version vsockidToDelete = _nvc.getLocalVersion_(sockidToDelete);
                assert vToDelete.isEntirelyShadowedBy_(vsockidToDelete)
                        : vToDelete + " " + vsockidToDelete + " " + loggedData;

                _nvc.deleteLocalVersionPermanently_(sockidToDelete, vToDelete, t);

                loggedData = "Delete " + vToDelete + " of " + sockidToDelete + ". " + loggedData;
            }
            t.commit_();
        } catch (Exception e) {
            l.warn(Util.e(e));
        } finally {
            t.end_();
        }

        // Assert that the given alias object has no non-alias ticks remaining
        // (it's possible to fail here; then need to do a more thorough cleaning of the object)
        Version vAllSocidToDelete = _nvc.getAllVersions_(socidToDelete);
        assert vAllSocidToDelete.withoutAliasTicks_().isZero_() :
                vAllSocidToDelete + " " + loggedData;

        // Throw an exception to abort the current GetVersCall response,
        // but on the next try the db should be fixed.
        ExAborted e = new ExAborted("GVC dup tick repair. " + loggedData);
        SVClient.logSendDefectAsync(true, "GVC dup tick repair", e);
        throw e;
    }
}
