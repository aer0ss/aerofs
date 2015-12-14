package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.collector.Collector;
import com.aerofs.daemon.core.net.CoreProtocolReactor;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetVersionsResponse;
import com.aerofs.proto.Core.PBGetVersionsResponseBlock;
import com.aerofs.proto.Core.PBStoreHeader;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;

public class GetVersionsResponse implements CoreProtocolReactor.Handler
{
    private static final Logger l = Loggers.getLogger(GetVersionsResponse.class);

    private final IncomingStreams _iss;
    private final UpdateSenderFilter _pusf;
    private final NativeVersionControl _nvc;
    private final ImmigrantVersionControl _ivc;
    private final TransManager _tm;
    private final MapSIndex2Store _sidx2s;
    private final IMapSID2SIndex _sid2sidx;
    private final IPulledDeviceDatabase _pulleddb;
    private final LocalACL _lacl;
    private final TokenManager _tokenManager;
    private final ProgressIndicators _pi;

    @Inject
    public GetVersionsResponse(
            TransManager tm,
            NativeVersionControl nvc,
            ImmigrantVersionControl ivc,
            UpdateSenderFilter pusf,
            IncomingStreams iss,
            MapSIndex2Store sidx2s,
            IMapSID2SIndex sid2sidx,
            IPulledDeviceDatabase pddb,
            LocalACL lacl,
            TokenManager tokenManager,
            ProgressIndicators pi)
    {
        _tm = tm;
        _nvc = nvc;
        _ivc = ivc;
        _pusf = pusf;
        _iss = iss;
        _sidx2s = sidx2s;
        _sid2sidx = sid2sidx;
        _pulleddb = pddb;
        _lacl = lacl;
        _tokenManager = tokenManager;
        _pi = pi;
    }

    @Override
    public PBCore.Type message() {
        return PBCore.Type.GET_VERSIONS_RESPONSE;
    }

    @Override
    public void handle_(DigestedMessage msg)
            throws Exception
    {
        try {
            if (msg.pb().hasExceptionResponse()) throw Exceptions.fromPB(msg.pb().getExceptionResponse());
            Util.checkPB(msg.pb().hasGetVersionsResponse(), PBGetVersionsResponse.class);

            if (msg.streamKey() == null) {
                processResponseFromDatagram_(msg.user(), msg.did(), msg.is());
            } else {
                processResponseFromStream_(msg.user(), msg.did(), msg.is());
            }
        } finally {
            if (msg.streamKey() != null) {
                _iss.end_(msg.streamKey());
            }
        }
    }

    private class ResponseContext
    {
        final UserID _user;
        final DID _from;

        // Set to null if the store should be ignored. Note that newStore_() also returns false if
        // the store should be ignored.
        // TODO (WW) having two ways to do the same thing is dangerous. fix the design.
        @Nullable SIndex _sidx = null;

        BFOID _filter = null;
        long _senderFilterIndex = 0;
        long _senderFilterUpdateSeq = 0;

        DID _didBlock = null;
        Version _vKwlgLocal = null;
        Version _vImmKwlgLocal = null;

        ResponseContext(UserID user, DID did)
        {
            _user = user;
            _from = did;
        }

        void finalizeStore_(Trans t) throws SQLException, ExNotFound
        {
            // ignored store (expelled locally or receiving ticks form user wo/ WRITE perm)
            if (_sidx == null) return;

            l.debug("finalize {} {}", _sidx, _filter);

            // must call add *after* everything else is written to the db
            if (_filter != null) {
                _sidx2s.getThrows_(_sidx).iface(Collector.class).add_(_from, _filter, t);
            }

            // Once all blocks have been processed and are written to the db,
            // locally remember that store s has been pulled from the DID from.
            _pulleddb.insert_(_sidx, _from, t);
        }

        /**
         * @return false if this store should be ignored
         */
        boolean newStore_(PBStoreHeader h) throws ExRetryLater, SQLException
        {
            SID sid = new SID(BaseUtil.fromPB(h.getStoreId()));
            _sidx = _sid2sidx.getNullable_(sid);
            BFOID filter = h.hasSenderFilter() ? new BFOID(h.getSenderFilter()) : null;

            l.info("{} receive gv response for {} {}", _from, sid, filter);

            // store was expelled locally between request and response
            if (_sidx == null) {
                l.warn("{} receive gv response for absent {} {}", _from, sid, _sid2sidx.getLocalOrAbsentNullable_(sid));
                return false;
            }

            // see Rule 2 in acl.md
            if (!_lacl.check_(_user, _sidx, Permissions.EDITOR)) {
                l.warn("{} ({}) has no editor perm for {}", _from, _user, _sidx);
                // Although we return false to indicate that the store should be ignored, _sidx
                // needs to set to null otherwise later code doesn't properly ignore blocks form
                // the store. See process_()
                // TODO (WW) having two ways to do the same thing is dangerous. fix the design.
                _sidx = null;
                return false;
            }

            _didBlock = null;
            refreshKnowledge_();
            if (h.hasSenderFilter()) {
                if (!(_pulleddb.contains_(_sidx, _from) || h.getFromBase())) {
                    // RACE RACE RACE
                    // if we send a GetVers request and then discard collector filters before
                    // receiving the response we MUST discard any filter in the response.
                    // The only safe way to discard filters is to discard the entire response
                    throw new ExRetryLater("race");
                }
                _filter = filter;
                _senderFilterIndex = h.getSenderFilterIndex();
                _senderFilterUpdateSeq = h.getSenderFilterUpdateSeq();
            } else {
                _filter = null;
            }

            return true;
        }

        void updateSenderFilter_() throws Exception
        {
            if (_filter != null) {
                // Now that everything has been committed, it's safe to ask
                // the peer to update the sender filter for us.
                _pusf.send_(_sidx, _senderFilterIndex, _senderFilterUpdateSeq, _from);
            }
        }

        void refreshKnowledge_() throws SQLException
        {
            if (_sidx != null) {
                _vKwlgLocal = _nvc.getKnowledgeExcludeSelf_(_sidx);
                _vImmKwlgLocal = _ivc.getKnowledgeExcludeSelf_(_sidx);
            }
        }

        void process_(PBGetVersionsResponseBlock block, Trans t)
                throws SQLException, ExNotFound, ExProtocolError
        {
            if (_sidx == null) {
                l.debug("{} ignore gv response block", _from);
                return;
            }

            _didBlock = processResponseBlock_(_sidx, block, _didBlock, _vKwlgLocal, _vImmKwlgLocal, _from, t);
        }

        boolean processBlocks_(Supplier<PBGetVersionsResponseBlock> s) throws Exception {
            Trans t = _sidx == null ? null : _tm.begin_();
            boolean last = false;
            try {
                PBGetVersionsResponseBlock block;
                while (null != (block = s.get())) {
                    if (block.hasStore()) {
                        // commit changes for previous store
                        if (_sidx != null) {
                            if (t == null) t = _tm.begin_();
                            finalizeStore_(t);
                            t.commit_();
                            t.end_();
                            t = null;
                            updateSenderFilter_();
                        }

                        if (newStore_(block.getStore())) t = _tm.begin_();
                    }

                    if (t != null) {
                        process_(block, t);
                    }
                    last = block.getIsLastBlock();
                    if (last) break;
                }
                if (last && _sidx != null) {
                    if (t == null) t = _tm.begin_();
                    finalizeStore_(t);
                }
                if (t != null) {
                    t.commit_();
                }
            } finally {
                if (t != null) {
                    t.end_();
                }
            }
            if (last) updateSenderFilter_();
            return last;
        }
    }

    private void processResponseFromDatagram_(UserID user, DID from, InputStream is) throws Exception
    {
        boolean last = new ResponseContext(user, from)
                .processBlocks_(() -> {
                    try {
                        return PBGetVersionsResponseBlock.parseDelimitedFrom(is);
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                });
        if (!last) throw new ExProtocolError();
    }

    private static final int MIN_BLOCKS_PER_TX = 10;

    private void processResponseFromStream_(UserID user, DID from, InputStream is)
            throws Exception
    {
        ResponseContext cxt = new ResponseContext(user, from);
        Queue<PBGetVersionsResponseBlock> qblocks = new ArrayDeque<>(MIN_BLOCKS_PER_TX);

        try (Token tk = _tokenManager.acquireThrows_(Cat.HOUSEKEEPING, "gvr: " + from)) {
            boolean last = false;
            do {
                TCB tcb = tk.pseudoPause_("gvr-recv");
                try {
                    last = readBlocks(is, qblocks);
                } finally {
                    tcb.pseudoResumed_();
                }

                processReceivedBlocks_(cxt, qblocks);
            } while (!last);
        }
    }

    private boolean readBlocks(InputStream is, Queue<PBGetVersionsResponseBlock> qblocks)
            throws Exception {
        boolean last = false;
        while (!last && qblocks.size() < MIN_BLOCKS_PER_TX) {
            PBGetVersionsResponseBlock block = PBGetVersionsResponseBlock.parseDelimitedFrom(is);
            // end of stream is not supposed to be reached before getting a last block
            // but it has been observed in the wild...
            if (block == null) {
                l.warn("stream ended before last block");
                break;
            }
            qblocks.add(block);
            last = block.getIsLastBlock();
            _pi.incrementMonotonicProgress();
        }
        return last;
    }

    /**
     * @param qblocks is a queue of blocks extracted from a chunk. The queue should not be empty
     */
    private void processReceivedBlocks_(ResponseContext cxt, Queue<PBGetVersionsResponseBlock> qblocks)
            throws Exception
    {
        if (qblocks.isEmpty()) return;
        // when we block for chunk receiving, database may have changed
        cxt.refreshKnowledge_();

        l.debug("blocks={}", qblocks.size());
        cxt.processBlocks_(qblocks::poll);
    }

    /**
     * @return the current device_id specified in the block
     */
    private DID processResponseBlock_(
            SIndex sidx,
            PBGetVersionsResponseBlock block,
            @Nullable DID didBlock,
            Version vKwlgLocal,
            Version vImmKwlgLocal,
            DID from,
            Trans t)
            throws SQLException, ExProtocolError
    {
        Util.checkMatchingSizes(block.getObjectIdCount(), block.getComIdCount(), block.getTickCount());
        Util.checkMatchingSizes(
                block.getImmigrantObjectIdCount(),
                block.getImmigrantComIdCount(),
                block.getImmigrantImmTickCount(),
                block.getImmigrantDeviceIdCount(),
                block.getImmigrantTickCount());

        if (block.hasDeviceId()) {
            DID did = new DID(BaseUtil.fromPB(block.getDeviceId()));
            if (did.equals(didBlock)) throw new ExProtocolError();
            didBlock = did;
        }

        if (didBlock == null) {
            // a final block with no device can occur which contains only the filter
            if (block.getObjectIdCount() > 0 || block.getImmigrantObjectIdCount() > 0
                || block.hasKnowledgeTick() || block.hasImmigrantKnowledgeTick()) {
                throw new ExProtocolError();
            }
            return null;
        }
        if (didBlock.equals(Cfg.did())) throw new ExProtocolError();

        // for debugging only
        Tick tickPrev = Tick.ZERO;
        SOCID socidPrev = null;

        for (int i = 0; i < block.getObjectIdCount(); i++) {
            OID oid = new OID(block.getObjectId(i).toByteArray());
            CID cid = new CID(block.getComId(i));
            Tick tick = new Tick(block.getTick(i));
            SOCID socid = new SOCID(sidx, oid, cid);
            _nvc.tickReceived_(socid, didBlock, tick, t);

            // ticks must be monotonically increasing
            assert tickPrev.getLong() <= tick.getLong() :
                from + " " + socidPrev + " " + tickPrev + " v " + socid + " " + tick;
            tickPrev = tick;
            socidPrev = socid;
        }

        Tick immTickPrev = Tick.ZERO;  // for debugging only
        for (int i = 0; i < block.getImmigrantObjectIdCount(); i++) {
            OID oid = new OID(BaseUtil.fromPB(block.getImmigrantObjectId(i)));
            CID cid = new CID(block.getImmigrantComId(i));
            Tick immTick = new Tick(block.getImmigrantImmTick(i));
            DID did = new DID(BaseUtil.fromPB(block.getImmigrantDeviceId(i)));
            Tick tick = new Tick(block.getImmigrantTick(i));
            SOCID socid = new SOCID(sidx, oid, cid);

            if (_ivc.immigrantTickReceived_(socid, didBlock, immTick, did, tick, t)) {
                _nvc.tickReceived_(socid, did, tick, t);
            }

            // ticks must be strictly increasing
            assert immTickPrev.getLong() < immTick.getLong();
            immTickPrev = immTick;
        }

        if (block.hasKnowledgeTick() &&
                block.getKnowledgeTick() > vKwlgLocal.get_(didBlock).getLong()) {
            Tick tick = new Tick(block.getKnowledgeTick());
            l.info("{} +k {} {} {}", from, sidx, didBlock, tick);
            _nvc.addKnowledge_(sidx, didBlock, tick, t);
        }

        if (block.hasImmigrantKnowledgeTick() && (block.getImmigrantKnowledgeTick() > vImmKwlgLocal.get_(didBlock).getLong())) {
            Tick tick = new Tick(block.getImmigrantKnowledgeTick());
            l.info("{} +ik {} {} {}", from, sidx, didBlock, tick);
            _ivc.addKnowledge_(sidx, didBlock, tick, t);
        }

        return didBlock;
    }
}
