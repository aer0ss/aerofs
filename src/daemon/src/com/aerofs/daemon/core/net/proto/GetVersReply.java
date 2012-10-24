package com.aerofs.daemon.core.net.proto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Queue;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ex.ExAborted;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.Core.PBGetVersReply;
import com.aerofs.proto.Core.PBGetVersReplyBlock;

public class GetVersReply
{
    private static final Logger l = Util.l(GetVersReply.class);

    private final IncomingStreams _iss;
    private final UpdateSenderFilter _pusf;
    private final NativeVersionControl _nvc;
    private final ImmigrantVersionControl _ivc;
    private final TransManager _tm;
    private final MapSIndex2Store _sidx2s;
    private final IPulledDeviceDatabase _pulleddb;

    @Inject
    public GetVersReply(TransManager tm, NativeVersionControl nvc,
            ImmigrantVersionControl ivc, UpdateSenderFilter pusf,
            IncomingStreams iss, MapSIndex2Store sidx2s,
            IPulledDeviceDatabase pddb)
    {
        _tm = tm;
        _nvc = nvc;
        _ivc = ivc;
        _pusf = pusf;
        _iss = iss;
        _sidx2s = sidx2s;
        _pulleddb = pddb;
    }

    void processReply_(DigestedMessage msg, Token tk) throws Exception
    {
        try {
            Util.checkPB(msg.pb().hasGetVersReply(), PBGetVersReply.class);

            PBGetVersReply pb = msg.pb().getGetVersReply();
            if (pb.hasSenderFilter() != pb.hasSenderFilterIndex()) {
                throw new ExProtocolError("sf fields mismatch");
            }

            BFOID filter = pb.hasSenderFilter() ?
                    new BFOID(pb.getSenderFilter()) : null;

            SIndex sidx = msg.sidx();
            if (l.isDebugEnabled()) {
                l.debug("recv from " + msg.ep() + " for " + sidx + " " + filter);
            }

            if (msg.streamKey() == null) {
                processAtomicReply_(sidx, msg.did(), msg.is(), filter);
            } else {
                processStreamReply_(sidx, msg.did(), msg.streamKey(), msg.is(), filter, tk);
            }

            if (pb.hasSenderFilterIndex()) {
                assert pb.hasSenderFilter();
                assert pb.hasSenderFilterUpdateSeq();
                // Now that everything has been committed, it's safe to ask
                // the peer to update the sender filter for us.
                _pusf.send_(sidx, pb.getSenderFilterIndex(), pb.getSenderFilterUpdateSeq(),
                        msg.did());
            }

        } finally {
            // TODO put this statement into a more general method
            if (msg.streamKey() != null) _iss.end_(msg.streamKey());
        }
    }

    /**
     * @param filter may be null
     */
    private void processAtomicReply_(SIndex sidx, DID from, InputStream is, BFOID filter)
            throws SQLException, ExProtocolError, IOException, ExNotFound
    {
        Version vKwlgLocal = _nvc.getKnowledgeExcludeSelf_(sidx);
        Version vImmKwlgLocal = _ivc.getKnowledgeExcludeSelf_(sidx);

        Trans t = _tm.begin_();
        try {
            DID didBlock = null;
            while (true) {
                PBGetVersReplyBlock block = PBGetVersReplyBlock
                        .parseDelimitedFrom(is);
                didBlock = processBlock_(sidx, block, didBlock, vKwlgLocal, vImmKwlgLocal, from,
                        filter, t);
                if (block.getIsLastBlock()) break;
            }
            assert is.available() == 0;

            t.commit_();
        } finally {
            t.end_();
        }
    }

    private static final int MIN_BLOCKS_PER_TX = 100;

    /**
     * @param filter may be null
     */
    private void processStreamReply_(SIndex sidx, DID from, StreamKey streamKey,
            ByteArrayInputStream is, BFOID filter, Token tk)
        throws SQLException, IOException, ExProtocolError, ExTimeout, ExStreamInvalid, ExAborted,
            ExNoResource, ExNotFound
    {
        Queue<PBGetVersReplyBlock> qblocks =
                new ArrayDeque<PBGetVersReplyBlock>(MIN_BLOCKS_PER_TX);

        boolean eob = false;
        DID didBlock = null;

        /**
         * Convert input streams to PB blocks; collect the blocks into a queue.
         * When MIN_BLOCKS_PER_TX blocks have been acquired (or eos reached),
         * then process the blocks in a DB transaction.
         * This structure means we write more data in a given transaction,
         * wasting less time waiting for disk.
         */
        while (true) {
            while (is.available() > 0) {
                PBGetVersReplyBlock block = PBGetVersReplyBlock.parseDelimitedFrom(is);

                // Flag the end of the stream
                if (block.getIsLastBlock()) {
                    assert is.available() == 0;
                    eob = true;
                }

                qblocks.add(block);
            }

            /**
             * To reduce the number of writes to the DB, we now batch at least
             * MIN_BLOCKS_PER_TX blocks into a single transaction
             */
            if (qblocks.size() >= MIN_BLOCKS_PER_TX || eob) {
                didBlock = processStreamBlocks_(sidx, from, didBlock, qblocks, filter);
            }

            if (eob) break;

            is = _iss.recvChunk_(streamKey, tk);
        }
    }

    /**
     * @param qblocks is a queue of blocks extracted from a chunk. The queue should not be empty
     * @return the current device_id
     */
    private DID processStreamBlocks_(SIndex sidx, DID from, DID didBlock,
            Queue<PBGetVersReplyBlock> qblocks, BFOID filter) throws SQLException, ExNotFound
    {
        assert !qblocks.isEmpty();

        // when we block for chunk receiving, database may have changed
        Version vKwlgLocal = _nvc.getKnowledgeExcludeSelf_(sidx);
        Version vImmKwlgLocal = _ivc.getKnowledgeExcludeSelf_(sidx);

        Trans t = _tm.begin_();
        try {
            PBGetVersReplyBlock block;

            l.debug("blocks/tx=" + qblocks.size());
            while (null != (block = qblocks.poll())) {
                assert !block.getIsLastBlock() || qblocks.isEmpty();
                didBlock = processBlock_(sidx, block, didBlock, vKwlgLocal, vImmKwlgLocal, from,
                        filter, t);
            }
            t.commit_();
        } finally {
            t.end_();
        }

        assert qblocks.isEmpty();
        return didBlock;
    }

    /**
     * @return the current device_id specified in the block
     */
    private DID processBlock_(SIndex sidx, PBGetVersReplyBlock block, DID didBlock,
            Version vKwlgLocal, Version vImmKwlgLocal, DID from, BFOID filter,
            Trans t) throws SQLException, ExNotFound
    {
        assert block.getObjectIdCount() == block.getComIdCount() &&
                block.getTickCount() == block.getComIdCount();
        assert block.getImmigrantObjectIdCount() == block.getImmigrantComIdCount() &&
                block.getImmigrantImmTickCount() == block.getImmigrantComIdCount() &&
                block.getImmigrantDeviceIdCount() == block.getImmigrantComIdCount() &&
                block.getImmigrantTickCount() == block.getImmigrantComIdCount();

        if (block.hasDeviceId()) didBlock = new DID(block.getDeviceId());
        assert didBlock == null || !didBlock.equals(Cfg.did());

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

        if (block.getIsLastBlock()) {
            // must call add *after* everything else is written to the db
            if (filter != null) _sidx2s.getThrows_(sidx).collector().add_(from, filter, t);

            // Once all blocks have been processed and are written to the db,
            // locally remember that store s has been pulled from the DID from.
            _pulleddb.add_(sidx, from, t);
        }

        return didBlock;
    }
}
