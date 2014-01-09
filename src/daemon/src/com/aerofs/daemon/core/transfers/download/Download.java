/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.core.transfers.download.dependence.DownloadDeadlockResolver;
import com.aerofs.daemon.core.transfers.download.dependence.DownloadDependenciesGraph;
import com.aerofs.daemon.core.transfers.download.dependence.DownloadDependenciesGraph.ExDownloadDeadlock;
import com.aerofs.daemon.core.transfers.download.dependence.NameConflictDependencyEdge;
import com.aerofs.daemon.core.transfers.download.dependence.ParentDependencyEdge;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.ex.ExWrapped;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.protocol.*;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Set;
import java.util.Stack;

/**
 * Base download class that contains state and logic for low-level download
 */
class Download
{
    protected static final Logger l = Loggers.getLogger(Download.class);

    final SOCID _socid;
    protected final To _from;
    protected final Token _tk;

    protected Cxt _cxt;

    protected final Factory _f;

    // wrappers to distinguish between exception provenance

    /**
     * Exception thrown before a remote GetComponentCall completed
     */
    class ExRemoteCallFailed extends ExWrapped
    {
        private static final long serialVersionUID = 0L;
        ExRemoteCallFailed(Exception e) { super(e); }
    }

    /**
     * Exception thrown when processing a reply to a GetComponentCall
     * TODO: further distinguish between remote (specified in reply) and local exceptions
     */
    class ExProcessReplyFailed extends ExWrapped
    {
        private static final long serialVersionUID = 0L;
        public final DID _did;
        ExProcessReplyFailed(DID did, Exception e) { super(e); _did = did; }
    }

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
        final Stack<SOCID> chain = new Stack<SOCID>();
        final Set<SOCID> resolved = Sets.newHashSet();
        final DownloadDependenciesGraph deps = new DownloadDependenciesGraph();

        @Override
        public String toString()
        {
            return "{" + did + "," + chain + "}";
        }

        @Override
        public Token token()
        {
            return _tk;
        }

        @Override
        public void downloadSync_(SOCID socid, DependencyType type) throws ExUnsatisfiedDependency
        {
            assert type == DependencyType.PARENT || type == DependencyType.UNSPECIFIED : type;
            l.info("dl dep {} {} {}", chain.peek(), socid, type);

            downloadSync_(type == DependencyType.PARENT
                    ? new ParentDependencyEdge(chain.peek(), socid)
                    : new DependencyEdge(chain.peek(), socid));
        }

        private void downloadSync_(DependencyEdge edge) throws ExUnsatisfiedDependency
        {
            l.info("cxt.dlsync {}", edge);
            try {
                try {
                    deps.addEdge_(edge);
                    try {
                        assert !resolved.contains(edge.dst) : edge.dst + " " + this;

                        new Download(_f, edge.dst, _f._factTo.create_(did), this)
                                .download_();

                        assert resolved.contains(edge.dst) : edge.dst + " " + this;
                        l.info("dep solved");
                    } finally {
                        deps.removeEdge_(edge);
                    }
                } catch (ExDownloadDeadlock e) {
                    // rethrow if cycle cannot be broken
                    // rationale: transient cycles have been encountered during emigration
                    // The daemon is killed and restarted which burns CPU/disk (scanning files,
                    // re-establishing network connections, ...) and the short downtime is enough
                    // to cause the occasional CI failure.
                    // TODO: figure out how to prevent such transient cycles from appearing
                    // (investigate race-conditions or restructure emigration)
                    if (!_f._ddr.resolveDeadlock_(e._cycle, this)) throw e;
                }
            } catch (Exception e) {
                l.info("dep error {} {}", edge.dst, e);
                throw new ExUnsatisfiedDependency(edge.dst, did, e);
            }
        }

        @Override
        public boolean hasResolved_(SOCID socid)
        {
            return resolved.contains(socid);
        }
    }

    protected static class Factory
    {
        protected final To.Factory _factTo;
        protected final DirectoryService _ds;
        protected final Downloads _dls;
        protected final DownloadState _dlstate;
        protected final GetComponentCall _gcc;
        protected final GetComponentReply _gcr;
        protected final DownloadDeadlockResolver _ddr;
        protected final IMapSIndex2SID _sidx2sid;

        @Inject
        protected Factory(DirectoryService ds, DownloadState dlstate, Downloads dls,
                To.Factory factTo, GetComponentCall gcc, GetComponentReply gcr,
                DownloadDeadlockResolver ddr, IMapSIndex2SID sidx2sid)
        {
            _ds = ds;
            _dls = dls;
            _dlstate = dlstate;
            _gcc = gcc;
            _gcr = gcr;
            _ddr = ddr;
            _factTo = factTo;
            _sidx2sid = sidx2sid;
        }
    }

    protected Download(Factory f, SOCID socid, To from, @Nonnull Token tk)
    {
        _f = f;
        _socid = socid;
        _from = from;
        _tk = tk;
    }

    private Download(Factory f, SOCID socid, To from, Cxt cxt)
    {
        this(f, socid, from, cxt.token());
        _cxt = cxt;
    }

    protected DID download_() throws SQLException, ExAborted, ExUnsatisfiedDependency,
            ExNoAvailDevice, ExRemoteCallFailed, ExProcessReplyFailed
    {
        boolean ok = false;
        try {
            l.debug("dl {} from {}", _socid, _from);
            DID did = downloadImpl_();
            ok = true;
            return did;
        } finally {
            l.debug("end {} {}", _socid, ok);
        }
    }

    private DID downloadImpl_() throws SQLException, ExAborted, ExUnsatisfiedDependency,
            ExNoAvailDevice, ExRemoteCallFailed, ExProcessReplyFailed
    {
        while (true) {
            checkForMeta();

            // must not break out of context when re-trying an object after resolving a dependency
            DID did = _cxt.did != null ? _cxt.did : _from.pick_();

            l.info("fetch {} {}", _socid, _cxt);

            if (fetchComponent_(did)) return did;
        }
    }

    /**
     * Check for content->meta dependency and expulsion. Even though GetComponentReply will check
     * again, we do it here to avoid useless round-trips with remote peers when possible.
     */
    private void checkForMeta() throws SQLException, ExAborted, ExUnsatisfiedDependency
    {
        if (_socid.cid().isMeta()) return;

        if (_f._sidx2sid.getNullable_(_socid.sidx()) == null) {
            throw new ExAborted("store expelled: " + _socid.sidx());
        }

        final OA oa = _f._ds.getAliasedOANullable_(_socid.soid());
        if (oa == null) {
            SOCID dst = new SOCID(_socid.soid(), CID.META);
            _f._dls.downloadSync_(dst, _from.allDIDs(), _cxt);
        } else if (oa.isExpelled()) {
            throw new ExAborted("object expelled: " + _socid);
        }
    }

    private boolean fetchComponent_(DID did)
            throws SQLException, ExAborted, ExUnsatisfiedDependency,
            ExNoAvailDevice, ExRemoteCallFailed, ExProcessReplyFailed
    {
        boolean ok = false;
        DigestedMessage msg = remoteCall_(did);
        assert did.equals(msg.did()) : did + " " + msg.did();

        try {
            assert _cxt.did == null || did.equals(_cxt.did) : _cxt + " " + msg;
            _cxt.did = did;
            _cxt.chain.push(_socid);
            _cxt.resolved.add(_socid);

            l.debug("gcr {} from {}", _socid, msg.did());

            ok = processReply_(msg, _cxt);
            return ok;
        } finally {
            if (!ok) _cxt.resolved.remove(_socid);
            _cxt.chain.pop();
        }
    }

    private DigestedMessage remoteCall_(DID did)
            throws SQLException, ExAborted, ExNoAvailDevice, ExRemoteCallFailed
    {
        try {
            return _f._gcc.remoteRequestComponent_(_socid, did, _tk);
        } catch (SQLException e) {
            throw e;
        } catch (ExAborted e) {
            throw e;
        } catch (ExNoAvailDevice e) {
            throw e;
        } catch (Exception e) {
            throw new ExRemoteCallFailed(e);
        }
    }

    private boolean processReply_(DigestedMessage msg, Cxt cxt)
            throws SQLException, ExAborted, ExUnsatisfiedDependency, ExProcessReplyFailed
    {
        try {
            boolean failed = true;
            try {
                _f._gcr.processReply_(_socid, msg, cxt);
                failed = false;
            } finally {
                l.debug("ended {} from {} {}", _socid, msg.ep(), failed ? "FAILED" : "OK");
                _f._dlstate.ended_(_socid, msg.ep(), failed);
            }
            return true;
        } catch (ExNameConflictDependsOn e) {
            SOCID src = cxt.chain.peek();
            SOCID dst = new SOCID(src.sidx(), e._ocid);
            // name conflict dep needs to be resolved within same dl context
            try {
                cxt.downloadSync_(NameConflictDependencyEdge.fromException(src, dst, e));
            } catch (ExUnsatisfiedDependency ex) {
                if (ex._e instanceof ExProcessReplyFailed &&
                    ((ExProcessReplyFailed)ex._e)._e instanceof ExNoComponentWithSpecifiedVersion) {
                    // this exception indicate that the local version of the dependency dominates
                    // the remote one, therefore we can proceed with name conflict resolution
                    l.warn("local {} dominates that of {}", dst, cxt.did);
                    cxt.resolved.add(dst);
                } else {
                    throw ex;
                }
            }
        } catch (ExDependsOn e) {
            // ideally PARENT dependencies could be resolved outside of the download context
            // however that would require maintaining a per-device dependency graph as well as
            // a per-device map of ongoing downloads and that is beyond the scope of this commit
            // as it does not impact correctness but is mostly a corner case optimisation
            // TODO: avoid duplicate concurrent requests w/ per-device tracking of ongoing downloads
            cxt.downloadSync_(new SOCID(_socid.sidx(), e._ocid), e._type);
        } catch (ExUpdateInProgress e) {
            onUpdateInProgress();
        } catch (SQLException e) {
            throw e;
        } catch (ExAborted e) {
            throw e;
        } catch (ExUnsatisfiedDependency e) {
            throw e;
        } catch (ExRestartWithHashComputed e) {
            // retry
        } catch (ExStreamInvalid e) {
            if (e.getReason() == InvalidationReason.UPDATE_IN_PROGRESS) {
                onUpdateInProgress();
            } else {
                // hmm, this may be more of a remote error, should the wrapping be changed?
                throw new ExProcessReplyFailed(cxt.did, e);
            }
        } catch (ExUnsolvedMetaMetaConflict e) {
            // TODO: metric/defect?
            throw new ExProcessReplyFailed(cxt.did, e);
        } catch (Exception e) {
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

    private void onUpdateInProgress() throws ExAborted
    {
        // too many retries: abort dl to free token
        // the collector will retry at a later time (i.e. on next iteration)
        if (++_updateRetry > MAX_UPDATE_RETRY) {
            l.warn("{}: update in prog for too long. abort", _socid);
            throw new ExAborted("update in progress");
        }

        l.info("{}: update in prog. retry later", _socid);
        _tk.sleep_(UPDATE_RETRY_DELAY, "retry dl (update in prog)");
    }
}
