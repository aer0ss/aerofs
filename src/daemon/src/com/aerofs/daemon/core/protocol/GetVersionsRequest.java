package com.aerofs.daemon.core.protocol;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.collector.SenderFilters.SenderFilterAndIndex;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Contributors;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.db.ver.ImmigrantTickRow;
import com.aerofs.daemon.lib.db.ver.NativeTickRow;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetVersionsRequest;
import com.aerofs.proto.Core.PBGetVersionsRequestBlock;
import com.aerofs.proto.Core.PBGetVersionsResponse;
import com.aerofs.proto.Core.PBGetVersionsResponseBlock;
import com.aerofs.proto.Core.PBStoreHeader;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.AbstractMessageLite;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayListWithCapacity;

public class GetVersionsRequest
{
    private static final Logger l = Loggers.getLogger(GetVersionsRequest.class);

    private static class TickPair {
        Tick _native;
        Tick _imm;
    }

    private final NativeVersionControl _nvc;
    private final ImmigrantVersionControl _ivc;
    private final TransportRoutingLayer _trl;
    private final Metrics _m;
    private final IncomingStreams _iss;
    private final OutgoingStreams _oss;
    private final MapSIndex2Store _sidx2s;
    private final IMapSID2SIndex _sid2sidx;
    private final IMapSIndex2SID _sidx2sid;
    private final IPulledDeviceDatabase _pulleddb;
    private final TokenManager _tokenManager;
    // The following dependency exists only to use and log OAs in
    //  enforceTicksAreMonotonicallyIncreasing(...)
    // TODO (MJ) remove when the use of OAs is not necessary for repairing the db
    private final DirectoryService _ds;
    // The following dependency exists only to repair the db in enforceTicksAreMonotonicallyIncreasing()
    // TODO (MJ) remove when repairing the db is disabled
    private final TransManager _tm;
    private final MapSIndex2Contributors _sidx2contrib;
    private final LocalACL _lacl;
    private final CfgLocalUser _cfgLocalUser;

    @Inject
    public GetVersionsRequest(IncomingStreams iss, OutgoingStreams oss, Metrics m,
            TransportRoutingLayer trl, NativeVersionControl nvc, ImmigrantVersionControl ivc,
            MapSIndex2Store sidx2s, IPulledDeviceDatabase pddb, TokenManager tokenManager,
            DirectoryService ds, TransManager tm, IMapSID2SIndex sid2sidx, IMapSIndex2SID sidx2sid,
            MapSIndex2Contributors sidx2contrib, LocalACL lacl, CfgLocalUser cfgLocalUser)
    {
        _iss = iss;
        _oss = oss;
        _m = m;
        _trl = trl;
        _nvc = nvc;
        _ivc = ivc;
        _sidx2s = sidx2s;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _pulleddb = pddb;
        _tokenManager = tokenManager;
        _ds = ds;
        _tm = tm;
        _sidx2contrib = sidx2contrib;
        _lacl = lacl;
        _cfgLocalUser = cfgLocalUser;
    }

    public void issueRequest_(DID didTo, SIndex sidx)
        throws Exception
    {
        l.debug("{} issue gv request for {}", didTo, sidx);

        // TODO: pass in a Set<SIndex> and stream multiple blocks...
        PBGetVersionsRequest.Builder bd = PBGetVersionsRequest.newBuilder();
        PBCore request = CoreProtocolUtil.newCoreMessage(Type.GET_VERSIONS_REQUEST).setGetVersionsRequest(bd).build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.writeDelimitedTo(out);
        // we modified the proto to allow streaming calls but the transport doesn't support that yet
        // so for now we send a single block and mark it as final
        // TODO: once transport refactor is done, switch to streaming calls
        makeBlock_(sidx, didTo).setIsLastBlock(true).build().writeDelimitedTo(out);

        _trl.sendUnicast_(didTo, CoreProtocolUtil.typeString(request), CoreProtocolUtil.NOT_RPC, out);
    }

    private PBGetVersionsRequestBlock.Builder makeBlock_(SIndex sidx, DID didTo) throws SQLException
    {
        PBGetVersionsRequestBlock.Builder bd = PBGetVersionsRequestBlock.newBuilder();
        bd.setStoreId(_sidx2sid.get_(sidx).toPB());

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

        for (Entry<DID, TickPair> en : map.entrySet()) {
            bd.addDeviceId(en.getKey().toPB());
            TickPair tp = en.getValue();
            bd.addKnowledgeTick(tp._native == null ? 0 : tp._native.getLong());
            bd.addImmigrantKnowledgeTick(tp._imm == null ? 0 : tp._imm.getLong());
        }

        // If Store s has never been pulled from didTo, then we need all
        // object information from BASE onward.
        if (!_pulleddb.contains_(sidx, didTo)) bd.setFromBase(true);
        l.debug("{} create gv request block for {}: {}", didTo, sidx, _pulleddb.contains_(sidx, didTo));

        return bd;
    }

    private static class DeviceEntry {
        DID _did;
        Tick _tickKwlgRemote;
        Tick _tickKwlgLocalES;  // ZERO if _did == Cfg.did()
        Tick _tickImmKwlgRemote;
        Tick _tickImmKwlgLocalES;  // ZERO if _did == Cfg.did()
    }

    public void processRequest_(DigestedMessage msg)
            throws Exception
    {
        Util.checkPB(msg.pb().hasGetVersionsRequest(), PBGetVersionsRequest.class);

        l.debug("{} process incoming gv request over {}", msg.did(), msg.tp());

        PBCore response = CoreProtocolUtil
                .newCoreMessage(Type.GET_VERSIONS_RESPONSE)
                .setGetVersionsResponse(PBGetVersionsResponse.newBuilder().build())
                .build();
        ByteArrayOutputStream os = write_(null, response);

        ////////
        // write blocks

        BlockSender sender = new BlockSender(msg.ep(), response.getRpcid(), CoreProtocolUtil.typeString(response), os);

        try {
            if (msg.streamKey() != null) {
                // NB: this code path should not be taken: streaming calls is not currently possible
                processRequestFromStream_(msg.did(), msg.user(), msg.is(), msg.streamKey(), sender);
            } else {
                processRequestBlock_(msg.did(), msg.user(), msg.is(), sender);
            }

            // EndOfStream marker
            sender.writeBlock_(PBGetVersionsResponseBlock.newBuilder().setIsLastBlock(true).build(), null);
            sender.markSuccessful_();
        } catch (Exception e) {
            sender.markFailed_(e);
        } finally {
            sender.cleanup_();
        }
    }

    private void processRequestFromStream_(DID from, UserID user, InputStream is, StreamKey key, BlockSender sender)
            throws Exception
    {
        try (Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "GetVersReq(" + from + ")")) {
            while (processRequestBlock_(from, user, is, sender)) {
                is = _iss.recvChunk_(key, tk);
            }
        }
    }

    private boolean processRequestBlock_(DID from, UserID user, InputStream is, BlockSender sender)
            throws Exception
    {
        while (is.available() > 0) {
            PBGetVersionsRequestBlock block = PBGetVersionsRequestBlock.parseDelimitedFrom(is);
            processRequestBlock_(from, user, block, sender);
            if (block.getIsLastBlock()) {
                if (is.available() > 0) throw new ExProtocolError();
                return false;
            }
        }
        return true;
    }

    private void processRequestBlock_(DID from, UserID user, PBGetVersionsRequestBlock requestBlock, BlockSender sender)
            throws Exception
    {
        SID sid = new SID(requestBlock.getStoreId());
        SIndex sidx = _sid2sidx.getNullable_(sid);

        // ignore store that is not locally present
        // NB: can't throw because of batching...
        if (sidx == null) {
            l.warn("{} gv request for absent {} {}", from, sid, _sid2sidx.getLocalOrAbsentNullable_(sid));
            return;
        }

        // see Rule 3 in acl.md
        if (!_lacl.check_(_cfgLocalUser.get(), sidx, Permissions.EDITOR)) {
            l.warn("{} we have no editor perm for {}", from, sidx);
            return;
        }

        // see Rule 1 in acl.md
        if (!_lacl.check_(user, sidx, Permissions.VIEWER)) {
            l.warn("{} ({}) has no viewer perm for {}", from, user, sidx);
            return;
        }

        l.info("{} receive gv request for {} {}", from, sidx, requestBlock.getFromBase());

        Util.checkMatchingSizes(
                requestBlock.getDeviceIdCount(),
                requestBlock.getKnowledgeTickCount(),
                requestBlock.getDeviceIdCount(),
                requestBlock.getImmigrantKnowledgeTickCount());

        Version vKwlgRemote = Version.empty();
        Version vImmKwlgRemote = Version.empty();
        // Load vKwlgRemote and vImmKwlgRemote with the contents of msg
        for (int i = 0; i < requestBlock.getDeviceIdCount(); i++) {
            DID did = new DID(requestBlock.getDeviceId(i));
            long tick = requestBlock.getKnowledgeTick(i);
            long tickImm = requestBlock.getImmigrantKnowledgeTick(i);
            checkState(tick != 0 || tickImm != 0);
            if (tick != 0) vKwlgRemote.set_(did, tick);
            if (tickImm != 0) vImmKwlgRemote.set_(did, tickImm);
        }

        Version vKwlgLocalES = _nvc.getKnowledgeExcludeSelf_(sidx);
        Version vImmKwlgLocalES = _ivc.getKnowledgeExcludeSelf_(sidx);
        Set<DID> contrib = _sidx2contrib.getContributors_(sidx);
        Set<DID> didsExcludeRequester = Sets.newHashSet(contrib);
        didsExcludeRequester.remove(from);

        List<DeviceEntry> desExcludeRequester =
                newArrayListWithCapacity(didsExcludeRequester.size());
        for (DID did : didsExcludeRequester) {
            DeviceEntry de = new DeviceEntry();
            de._did = did;
            de._tickKwlgRemote = vKwlgRemote.get_(did);
            de._tickKwlgLocalES = vKwlgLocalES.get_(did);
            de._tickImmKwlgRemote = vImmKwlgRemote.get_(did);
            de._tickImmKwlgLocalES = vImmKwlgLocalES.get_(did);
            // local knowledge ticks corresponding to the local device
            // must be zero. The tick of a non-local device _may_ be zero.
            checkState(!did.equals(Cfg.did()) || (de._tickKwlgLocalES.equals(Tick.ZERO) &&
                    de._tickImmKwlgLocalES.equals(Tick.ZERO)));
            desExcludeRequester.add(de);
        }

        boolean fromBase = requestBlock.getFromBase();
        Store s = _sidx2s.getThrows_(sidx);
        SenderFilterAndIndex sfi = s.senderFilters().get_(from, fromBase);

        l.info("{} issue gv response for {} l {} r {} fs {}", from, sidx, vKwlgLocalES, vKwlgRemote, (sfi == null ? null : sfi._filter));

        ////////
        // write the header

        PBStoreHeader.Builder bd = PBStoreHeader.newBuilder();
        bd.setStoreId(requestBlock.getStoreId());
        if (sfi != null) {
            bd.setSenderFilter(sfi._filter.toPB())
                .setSenderFilterIndex(sfi._sfidx.getLong())
                .setSenderFilterUpdateSeq(sfi._updateSeq);
        }
        PBStoreHeader h = bd.build();

        boolean headerSent = sendBlocks_(sender, sidx, h, desExcludeRequester);

        if (!headerSent && sfi != null) {
            // no block sent, need a dummy one to make sure the filter is sent
            sender.writeBlock_(PBGetVersionsResponseBlock.newBuilder().setStore(h).build(), null);
        }
    }

    /**
     * @param os null to force allocation of a new output stream
     * @return a different stream than os if the old stream is full, and thus a
     * call to flush_() is required.
     */
    // FIXME(AG): this is a transport failure - this class shouldn't have to care about this
    private ByteArrayOutputStream write_(@Nullable ByteArrayOutputStream os,
            AbstractMessageLite msg) throws IOException
    {
        int len = msg.getSerializedSize();
        int maxUcastLen = _m.getMaxUnicastSize_();
        // tune down ENTRIES_PER_BLOCK if the assertion fails
        checkArgument(len < maxUcastLen);

        int osLen = os == null ? maxUcastLen : os.size();

        // INTEGER_SIZE is for the delimiter
        if (osLen + C.INTEGER_SIZE + len > maxUcastLen) {
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
        private final Endpoint _ep;
        private final int _rpcid;
        private final String _msgType;
        private ByteArrayOutputStream _os;

        private OutgoingStream _stream;     // null for atomic messages
        private Token _tk;                  // null for atomic messages
        private boolean _streamOkay;        // invalid for atomic messages
        private @Nullable Throwable _cause; // the reason why a block could not be sent

        BlockSender(Endpoint ep, int rpcid, String msgType, ByteArrayOutputStream os)
        {
            _ep = ep;
            _rpcid = rpcid;
            _msgType = msgType;
            _os = os;
        }

        /**
         * @param iter the method may close the iter if necessary. a null iter
         * indicates that there is no active iter
         */
        void writeBlock_(AbstractMessageLite block, @Nullable IDBIterator<?> iter) throws Exception
        {
            ByteArrayOutputStream os2 = write_(_os, block);

            if (os2 != _os) {
                if (_stream == null) {
                    _tk = _tokenManager.acquireThrows_(Cat.SERVER, "SendVersion(" + _rpcid  + ", " + _ep + ")");
                    _stream = _oss.newStream(_ep, _tk);
                }

                if (iter != null) iter.close_();
                _stream.sendChunk_(_os.toByteArray());
                _os = os2;
            }
        }

        /**
         * called after the last block has been sent
         */
        void markSuccessful_() throws Exception
        {
            if (_stream == null) {
                _trl.sendUnicast_(_ep, _msgType, _rpcid, _os);
            } else {
                _stream.sendChunk_(_os.toByteArray());
                _streamOkay = true;
            }
        }

        /**
         * called in a finally block that ends the life cycle of 'this' object
         */
        void cleanup_()
        {
            if (_stream != null) {
                if (_streamOkay) {
                    l.trace("{} finish sending blocks over {}", _ep.did(), _ep.tp());
                    _stream.end_();
                } else {
                    l.warn("{} abort sending blocks over {} err:{}", _ep.did(), _ep.tp(), _cause != null ? _cause : "unknown");
                    _stream.abort_(InvalidationReason.INTERNAL_ERROR);
                }
            }

            if (_tk != null) {
                _tk.reclaim_();
            }
        }

        public void markFailed_(Throwable cause)
        {
            _cause = cause;
        }
    }

    private boolean sendBlocks_(BlockSender sender, SIndex sidx, PBStoreHeader h,
            Collection<DeviceEntry> desExcludeTo)
            throws Exception
    {
        boolean headerSent = false;

        for (DeviceEntry de : desExcludeTo) {
            PBGetVersionsResponseBlock.Builder bdBlock = PBGetVersionsResponseBlock
                    .newBuilder()
                    .setDeviceId(de._did.toPB());

            if (!headerSent) bdBlock.setStore(h);

            boolean hasNonEntryFields = false;
            int entryCount = 0;

            ////////
            // add versions

            Tick tickLast = Tick.ZERO;
            SOCID socidLast = null;
            IDBIterator<NativeTickRow> it_ver = _nvc.getMaxTicks_(sidx, de._did, de._tickKwlgRemote);
            try {
                while (it_ver.next_()) {
                    NativeTickRow tr = it_ver.get_();

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
                        sender.writeBlock_(bdBlock.build(), it_ver);
                        headerSent = true;
                        bdBlock = PBGetVersionsResponseBlock.newBuilder();
                        entryCount = 0;
                        if (it_ver.closed_()) {
                            it_ver = _nvc.getMaxTicks_(sidx, de._did, tickLast);
                        }
                    }
                }
            } finally {
                it_ver.close_();
            }

            Tick tickKwlgLocal = de._did.equals(Cfg.did()) ? tickLast : de._tickKwlgLocalES;
            if (tickKwlgLocal.getLong() > de._tickKwlgRemote.getLong()) {
                bdBlock.setKnowledgeTick(tickKwlgLocal.getLong());
                hasNonEntryFields = true;
            }

            ////////
            // add immigrant versions

            Tick immTickLast = Tick.ZERO;
            IDBIterator<ImmigrantTickRow> it_imm = _ivc.getMaxTicks_(sidx, de._did,
                    de._tickImmKwlgRemote);
            try {
                while (it_imm.next_()) {
                    ImmigrantTickRow tr = it_imm.get_();
                    bdBlock.addImmigrantObjectId(tr._oid.toPB());
                    bdBlock.addImmigrantComId(tr._cid.getInt());
                    bdBlock.addImmigrantImmTick(tr._immTick.getLong());
                    bdBlock.addImmigrantDeviceId(tr._did.toPB());
                    bdBlock.addImmigrantTick(tr._tick.getLong());
                    immTickLast = tr._immTick;

                    if (++entryCount == ENTRIES_PER_BLOCK) {
                        sender.writeBlock_(bdBlock.build(), it_imm);
                        headerSent = true;
                        bdBlock = PBGetVersionsResponseBlock.newBuilder();
                        entryCount = 0;
                        if (it_imm.closed_()) {
                            it_imm = _ivc.getMaxTicks_(sidx, de._did, immTickLast);
                        }
                    }

                }
            } finally {
                it_imm.close_();
            }

            Tick tickImmKwlgLocal = de._did.equals(Cfg.did()) ? immTickLast :
                de._tickImmKwlgLocalES;
            if (tickImmKwlgLocal.getLong() > de._tickImmKwlgRemote.getLong()) {
                bdBlock.setImmigrantKnowledgeTick(tickImmKwlgLocal.getLong());
                hasNonEntryFields = true;
            }

            if (entryCount != 0 || hasNonEntryFields) {
                sender.writeBlock_(bdBlock.build(), null);
                headerSent = true;
            }
        }

        return headerSent;
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
        checkState(!(tickLast.getLong() > tick.getLong()), loggedData);

        // We don't permit any duplicate ticks to be of the alias type (odd-valued)
        checkState(!tick.isAlias(), loggedData);

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
            l.warn("{} is the target of {}", oa, socid);

            // Assert that oaLast is the target of socid
            checkState(oaLast != null && oa.soid().equals(oaLast.soid()), loggedDataWithOA);

            // delete the tick for socid
            deleteDuplicateTick(socid, new SOCID(oa.soid(), socid.cid()), did, tick,
                    loggedDataWithOA);

        } else if (oaLast != null && !oaLast.soid().equals(socidLast.soid())) {
            l.warn("{} is the target of {}", oaLast, socidLast);

            // Assert that oa is the target of socidLast
            checkState(oa != null && oa.soid().equals(oaLast.soid()), loggedDataWithOA);

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
        checkState(!_ds.hasOA_(socidToDelete.soid()), "%s %s", socidToDelete, loggedData);

        Version vToDelete = Version.of(did, tick);

        // Assume that vToDelete is entirely duplicated in the target object's versions.
        // (so it is safe to simply delete it from the alias object (either KML or local))
        Version vAllTarget = _nvc.getAllVersions_(socidTarget);
        checkState(vToDelete.isEntirelyShadowedBy_(vAllTarget), "%s %s %s",
                vToDelete, vAllTarget, loggedData);

        try (Trans t = _tm.begin_()) {
            if (vToDelete.isEntirelyShadowedBy_(_nvc.getKMLVersion_(socidToDelete))) {
                // vToDelete is a KML for socidToDelete
                _nvc.deleteKMLVersionPermanently_(socidToDelete, vToDelete, t);

                loggedData = "Delete KML " + vToDelete + " of " + socidToDelete + ". " + loggedData;

            } else {
                // vToDelete is not a KML (as it failed the previous branch)
                // so assert that it is in the local versions of socidToDelete
                Version vAllLocalVersions = _nvc.getAllLocalVersions_(socidToDelete);
                checkState(vToDelete.isEntirelyShadowedBy_(vAllLocalVersions),
                        "%s %s %s", vToDelete, vAllLocalVersions, loggedData);

                // Assume the branch of the version to delete is MASTER
                // (otherwise I'm unsure how to resolve this)
                SOCKID sockidToDelete = new SOCKID(socidTarget);
                Version vsockidToDelete = _nvc.getLocalVersion_(sockidToDelete);
                checkState(vToDelete.isEntirelyShadowedBy_(vsockidToDelete),
                        "%s %s %s", vToDelete, vsockidToDelete, loggedData);

                _nvc.deleteLocalVersionPermanently_(sockidToDelete, vToDelete, t);

                loggedData = "Delete " + vToDelete + " of " + sockidToDelete + ". " + loggedData;
            }
            t.commit_();
        } catch (Exception e) {
            l.warn(Util.e(e));
        }

        // Assert that the given alias object has no non-alias ticks remaining
        // (it's possible to fail here; then need to do a more thorough cleaning of the object)
        Version vAllSocidToDelete = _nvc.getAllVersions_(socidToDelete);
        checkState(vAllSocidToDelete.isAliasOnly_(), "%s %s", vAllSocidToDelete, loggedData);

        // Throw an exception to abort the current GetVersionsResponse,
        // but on the next try the db should be fixed.
        ExAborted e = new ExAborted("GVR dup tick repair. " + loggedData);
        newDefectWithLogs("get_versions_request.delete_duplicate_ticks")
                .setMessage("GVR dup tick repair")
                .setException(e)
                .sendAsync();
        throw e;
    }
}
