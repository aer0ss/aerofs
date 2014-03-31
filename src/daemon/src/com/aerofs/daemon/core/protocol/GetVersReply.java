package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.Core.PBGetVersReply;
import com.aerofs.proto.Core.PBGetVersReplyBlock;
import com.aerofs.proto.Core.PBStoreHeader;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Queue;

public class GetVersReply
{
    private static final Logger l = Loggers.getLogger(GetVersReply.class);

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

    @Inject
    public GetVersReply(TransManager tm, NativeVersionControl nvc,
            ImmigrantVersionControl ivc, UpdateSenderFilter pusf,
            IncomingStreams iss, MapSIndex2Store sidx2s, IMapSID2SIndex sid2sidx,
            IPulledDeviceDatabase pddb, LocalACL lacl, TokenManager tokenManager)
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
    }

    public void processReply_(DigestedMessage msg) throws Exception
    {
        try {
            if (msg.pb().hasExceptionReply()) throw Exceptions.fromPB(msg.pb().getExceptionReply());
            Util.checkPB(msg.pb().hasGetVersReply(), PBGetVersReply.class);

            if (msg.streamKey() == null) {
                processAtomicReply_(msg.user(), msg.did(), msg.is());
            } else {
                processStreamReply_(msg.user(), msg.did(), msg.streamKey(), msg.is());
            }
        } catch (Exception e) {
            l.info("error processing reply", e);
            throw e;
        } finally {
            // TODO put this statement into a more general method
            if (msg.streamKey() != null) _iss.end_(msg.streamKey());
        }
    }

    private class ReplyContext
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

        ReplyContext(UserID user, DID did)
        {
            this._user = user;
            _from = did;
        }

        void finalizeStore_(Trans t) throws SQLException, ExNotFound
        {
            // ignored store (expelled locally or receiving ticks form user wo/ WRITE perm)
            if (_sidx == null) return;

            l.debug("finalize {} {}", _sidx, _filter);

            // must call add *after* everything else is written to the db
            if (_filter != null) {
                _sidx2s.getThrows_(_sidx).collector().add_(_from, _filter, t);
            }

            // Once all blocks have been processed and are written to the db,
            // locally remember that store s has been pulled from the DID from.
            _pulleddb.insert_(_sidx, _from, t);
        }

        /**
         * @return false if this store should be ignored
         */
        boolean newStore_(PBStoreHeader h) throws SQLException
        {
            SID sid = new SID(h.getStoreId());
            _sidx = _sid2sidx.getNullable_(sid);

            // store was expelled locally between call and reply...
            if (_sidx == null) {
                l.warn("recv from {} for absent: {} {}", _from, sid,
                        _sid2sidx.getLocalOrAbsentNullable_(sid));
                return false;
            }

            // see Rule 2 in acl.md
            if (!_lacl.check_(_user, _sidx, Permissions.EDITOR)) {
                l.warn("{} on {} has no editor perm for {}", _user, _from, _sidx);
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
                _filter = new BFOID(h.getSenderFilter());
                _senderFilterIndex = h.getSenderFilterIndex();
                _senderFilterUpdateSeq = h.getSenderFilterUpdateSeq();
            } else {
                _filter = null;
            }

            l.debug("recv from {} for {} {}", _from, _sidx, _filter);
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

        void process_(PBGetVersReplyBlock block, Trans t)
                throws SQLException, ExNotFound, ExProtocolError
        {
            if (_sidx == null) {
                l.debug("ignored block");
                return;
            }

            _didBlock = processBlock_(_sidx, block, _didBlock, _vKwlgLocal, _vImmKwlgLocal, _from, t);
        }
    }

    private void processAtomicReply_(UserID user, DID from, InputStream is) throws Exception
    {
        Trans t = null;
        ReplyContext cxt = new ReplyContext(user, from);

        try {
            while (true) {
                PBGetVersReplyBlock block = PBGetVersReplyBlock.parseDelimitedFrom(is);
                if (block.hasStore()) {
                    // commit changes for previous store
                    if (t != null) {
                        cxt.finalizeStore_(t);
                        t.commit_();
                        t.end_();
                        t = null;
                        cxt.updateSenderFilter_();
                    }

                    if (cxt.newStore_(block.getStore())) t = _tm.begin_();
                }

                cxt.process_(block, t);

                if (block.getIsLastBlock()) break;
            }
            if (is.available() != 0) throw new ExProtocolError();
            if (t != null) {
                cxt.finalizeStore_(t);
                t.commit_();
            }
        } finally {
            if (t != null) {
                t.end_();
                cxt.updateSenderFilter_();
            }
        }
    }

    private static final int MIN_BLOCKS_PER_TX = 100;

    private void processStreamReply_(UserID user, DID from, StreamKey streamKey, InputStream is)
            throws Exception
    {
        Token tk = null;
        ReplyContext cxt = new ReplyContext(user, from);
        Queue<PBGetVersReplyBlock> qblocks = new ArrayDeque<PBGetVersReplyBlock>(MIN_BLOCKS_PER_TX);

        try {
            while (!processStreamChunk_(cxt, qblocks, is)) {
                if (tk == null) {
                    // push token acquisition as far down as possible to minimize likelihood of
                    // exhaustion in the common cases where tokens are not actually needed
                    // TODO: ideally we wouldn't have to pass a token down to the transport...
                    tk = _tokenManager.acquireThrows_(Cat.HOUSEKEEPING, "gvr " + from);
                }
                is = _iss.recvChunk_(streamKey, tk);
            }
        } finally {
            if (tk != null) tk.reclaim_();
        }
    }

    private boolean processStreamChunk_(ReplyContext cxt, Queue<PBGetVersReplyBlock> qblocks,
            InputStream is) throws Exception
    {
        boolean last = false;
        while (!last && is.available() > 0) {
            PBGetVersReplyBlock block = PBGetVersReplyBlock.parseDelimitedFrom(is);
            if (block.hasStore()) {
                // commit changes for previous store
                processStreamBlocks_(cxt, qblocks, true);

                cxt.newStore_(block.getStore());
            }
            last = block.getIsLastBlock();
            qblocks.add(block);
        }

        if (is.available() != 0) throw new ExProtocolError();

        /**
         * To reduce the number of writes to the DB, we now batch at least
         * MIN_BLOCKS_PER_TX blocks into a single transaction
         */
        if (qblocks.size() >= MIN_BLOCKS_PER_TX || last) {
            processStreamBlocks_(cxt, qblocks, last);
        }

        return last;
    }

    /**
     * @param qblocks is a queue of blocks extracted from a chunk. The queue should not be empty
     */
    private void processStreamBlocks_(ReplyContext cxt, Queue<PBGetVersReplyBlock> qblocks,
            boolean storeBoundary) throws Exception
    {
        assert storeBoundary || !qblocks.isEmpty();

        if (!qblocks.isEmpty()) {
            // when we block for chunk receiving, database may have changed
            cxt.refreshKnowledge_();

            Trans t = _tm.begin_();
            try {
                PBGetVersReplyBlock block;

                l.debug("blocks/tx={}", qblocks.size());
                while (null != (block = qblocks.poll())) {
                    if (block.getIsLastBlock() && !qblocks.isEmpty()) throw new ExProtocolError();
                    cxt.process_(block, t);
                }

                if (storeBoundary) cxt.finalizeStore_(t);
                t.commit_();
            } finally {
                t.end_();
            }
        }

        if (storeBoundary) cxt.updateSenderFilter_();

        assert qblocks.isEmpty();
    }

    /**
     * @return the current device_id specified in the block
     */
    private DID processBlock_(SIndex sidx, PBGetVersReplyBlock block, DID didBlock,
            Version vKwlgLocal, Version vImmKwlgLocal, DID from,
            Trans t) throws SQLException, ExProtocolError
    {
        Util.checkMatchingSizes(block.getObjectIdCount(), block.getComIdCount(),
                block.getTickCount());

        Util.checkMatchingSizes(block.getImmigrantObjectIdCount(), block.getImmigrantComIdCount(),
                block.getImmigrantImmTickCount(), block.getImmigrantDeviceIdCount(),
                block.getImmigrantTickCount());

        if (block.hasDeviceId()) didBlock = new DID(block.getDeviceId());

        if (didBlock != null && didBlock.equals(Cfg.did())) throw new ExProtocolError();

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
            OID oid = new OID(block.getImmigrantObjectId(i));
            CID cid = new CID(block.getImmigrantComId(i));
            Tick immTick = new Tick(block.getImmigrantImmTick(i));
            DID did = new DID(block.getImmigrantDeviceId(i));
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
            _nvc.addKnowledge_(sidx, didBlock, tick, t);
        }

        if (block.hasImmigrantKnowledgeTick() &&
                block.getImmigrantKnowledgeTick() > vImmKwlgLocal.get_(didBlock).getLong()) {
            Tick tick = new Tick(block.getImmigrantKnowledgeTick());
            _ivc.addKnowledge_(sidx, didBlock, tick, t);
        }

        return didBlock;
    }
}
