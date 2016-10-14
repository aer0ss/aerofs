/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.ex.*;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.protocol.*;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;

import static com.aerofs.daemon.core.transfers.download.IAsyncDownload.ExProcessReplyFailed;
import static com.aerofs.daemon.core.transfers.download.IAsyncDownload.ExRemoteCallFailed;

/**
 * Base download class that contains state and logic for low-level download
 */
class Download
{
    protected static final Logger l = Loggers.getLogger(Download.class);

    final SOID _soid;
    protected final To _from;
    protected final Token _tk;

    protected Cxt _cxt;

    protected final Factory _f;

    /**
     * A download context is used to resolve download dependencies arising from name conflicts,
     * emigration and missing parents. It only deals with META->META dependencies. All CONTENT->META
     * dependencies are garanteed not to lead to deadlock and can be resolved from any peer so they
     * are resolved by a standalone {@link AsyncDownload}.
     *
     * Each download context maintains its own dependency graph to detect and resolve deadlocks. In
     * addition it keeps track of a set of resolved dependencies for name conflict purposes.
     *
     * A download context is tied to the DID that was picked to request the first object (from which
     * dependencies arose). This is strictly required for Aliasing and Emigration and simplifies
     * Parent resolution as a global download dependency graph is no longer required
     */
    protected class Cxt implements IDownloadContext
    {
        private DID did;

        @Override
        public String toString()
        {
            return "{" + did + "}";
        }

        @Override
        public Token token()
        {
            return _tk;
        }
    }

    protected static class Factory
    {
        protected final To.Factory _factTo;
        protected final DirectoryService _ds;
        protected final Downloads _dls;
        protected final GetContentRequest  _pgcc;
        protected final GetContentResponse _pgcr;
        protected final IMapSIndex2SID _sidx2sid;

        @Inject
        protected Factory(DirectoryService ds, Downloads dls,
                To.Factory factTo, IMapSIndex2SID sidx2sid,
                GetContentRequest pgcc, GetContentResponse pgcr)
        {
            _ds = ds;
            _dls = dls;
            _pgcc = pgcc;
            _pgcr = pgcr;
            _factTo = factTo;
            _sidx2sid = sidx2sid;
        }
    }

    protected Download(Factory f, SOID soid, To from, @Nonnull Token tk)
    {
        _f = f;
        _soid = soid;
        _from = from;
        _tk = tk;
    }

    protected DID download_() throws SQLException, ExAborted,
            ExNoAvailDevice, ExRemoteCallFailed, ExProcessReplyFailed
    {
        boolean ok = false;
        try {
            l.debug("dl {} from {}", _soid, _from);
            DID did = downloadImpl_();
            ok = true;
            return did;
        } finally {
            l.debug("end {} {}", _soid, ok);
        }
    }

    private DID downloadImpl_() throws SQLException, ExAborted,
            ExNoAvailDevice, ExRemoteCallFailed, ExProcessReplyFailed
    {
        while (true) {
            checkForMeta();

            // must not break out of context when re-trying an object after resolving a dependency
            DID did = _cxt.did != null ? _cxt.did : _from.pick_();

            l.info("{} fetch {} {}", did, _soid, _cxt);

            if (fetchComponent_(did)) return did;
        }
    }

    /**
     * Check for content->meta dependency and expulsion. Even though GetComponentReply will check
     * again, we do it here to avoid useless round-trips with remote peers when possible.
     */
    private void checkForMeta() throws SQLException, ExAborted
    {
        if (_f._sidx2sid.getNullable_(_soid.sidx()) == null) {
            throw new ExAborted("store expelled: " + _soid.sidx());
        }

        OA oa = _f._ds.getAliasedOANullable_(_soid);
        if (oa == null) {
            throw new ExAborted("meta dl failed");
        }

        if (oa.isExpelled()) {
            throw new ExAborted("object expelled: " + _soid);
        }
    }

    private boolean fetchComponent_(DID did)
            throws SQLException, ExAborted,
            ExNoAvailDevice, ExRemoteCallFailed, ExProcessReplyFailed
    {
        DigestedMessage msg = remoteCall_(did);
        if (!did.equals(msg.did())) {
            l.error("did mismatch {} {}", did, msg.did());
            throw new ExProcessReplyFailed(did,
                    new ExProtocolError("did mismatch " + did + " " + msg.did()));
        }

        assert _cxt.did == null || did.equals(_cxt.did) : _cxt + " " + msg;
        _cxt.did = did;

        l.debug("{} gcr {}", msg.did(), _soid);

        return processReply_(msg, _cxt);
    }

    private DigestedMessage remoteCall_(DID did)
            throws SQLException, ExAborted, ExNoAvailDevice, ExRemoteCallFailed
    {
        try {
            return _f._pgcc.remoteRequestContent_(_soid, did, _tk);
        } catch (SQLException | ExAborted | ExNoAvailDevice e) {
            throw e;
        } catch (Exception e) {
            throw new ExRemoteCallFailed(e);
        }
    }

    private boolean processReply_(DigestedMessage msg, Cxt cxt)
            throws SQLException, ExAborted, ExProcessReplyFailed
    {
        try {
            boolean failed = true;
            try {
                if (_f._sidx2sid.getNullable_(_soid.sidx()) == null) {
                    throw new ExExpelled("store " + _soid.sidx() + " not longer present");
                }
                _f._pgcr.processResponse_(_soid, msg, cxt.token());
                failed = false;
            } finally {
                l.debug("{} ended {} {} over {}", msg.did(), _soid, failed ? "FAILED" : "OK", msg.tp());
            }
            return true;
        } catch (ExUpdateInProgress e) {
            onUpdateInProgress(msg.did());
        } catch (SQLException | ExAborted e) {
            throw e;
        } catch (ExStreamInvalid e) {
            if (e.getReason() == InvalidationReason.UPDATE_IN_PROGRESS) {
                onUpdateInProgress(msg.did());
            } else {
                // hmm, this may be more of a remote error, should the wrapping be changed?
                throw new ExProcessReplyFailed(cxt.did, e);
            }
        } catch (Exception e) {
            // TODO: metric/defect?
            throw new ExProcessReplyFailed(cxt.did, e);
        }
        return false;
    }

    /**
     * Because it is technically possible that some file end up in a perpetual update state
     * (e.g. some frequently written to log files) and also because even some seemingly
     * innocuous files have been reported to mistakenly generate UPLOAD_IN_PROGRESS we must
     * bound the number of immediate retries to prevent the apparition of a livelock (if all
     * inflight dl touch such an unstable file)
     */
    private int _updateRetry = 0;
    private static final int MAX_UPDATE_RETRY = 2;
    private static final long UPDATE_RETRY_DELAY = 3 * C.SEC;

    private void onUpdateInProgress(DID did) throws ExAborted
    {
        // too many retries: abort dl to free token
        // the collector will retry at a later time (i.e. on next iteration)
        if (++_updateRetry > MAX_UPDATE_RETRY) {
            l.warn("{} {}: update in prog for too long. abort", did, _soid);
            throw new ExAborted("update in progress");
        }

        l.info("{} {}: update in prog. retry later", did, _soid);
        _tk.sleep_(UPDATE_RETRY_DELAY, "retry dl (update in prog)");
    }
}
