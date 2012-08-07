package com.aerofs.daemon.core.net;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.proto.ExSenderHasNoPerm;
import com.aerofs.daemon.core.net.proto.GetComponentCall;
import com.aerofs.daemon.core.store.IMapSIndex2Store;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.*;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExIncrementalDownload;
import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.notifier.Listeners;

import javax.annotation.Nullable;

public class Download
{
    private static final Logger l = Util.l(Download.class);

    private final To _src;
    private final Listeners<IDownloadCompletionListener> _ls = Listeners.newListeners();
    // A global directed graph representing dependencies from ongoing downloads. It is used to
    // prevent dependency deadlocks, and avoid redownloading a completed dependency.
    private final DownloadDependenciesGraph<SOCID> _dlOngoingDependencies;
    private final Token _tk;
    private final SOCID _socid;
    private Prio _prio;
    private final Factory _f;

    public static class Factory
    {
        private final TC _tc;
        private final DownloadState _dlstate;
        private final IMapSIndex2Store _sidx2s;
        private final DirectoryService _ds;
        private final Downloads _dls;
        private final GetComponentCall _pgcc;
        private final NativeVersionControl _nvc;
        private final To.Factory _factTo;
        private final DownloadDependenciesGraph<SOCID> _dlOngoingDependencies;

        @Inject
        public Factory(NativeVersionControl nvc, GetComponentCall pgcc, Downloads dls,
                DirectoryService ds, DownloadState dlstate, TC tc,
                To.Factory factTo, IMapSIndex2Store sidx2s, DownloadDependenciesGraph<SOCID> dldg)
        {

            _nvc = nvc;
            _pgcc = pgcc;
            _dls = dls;
            _ds = ds;
            _dlstate = dlstate;
            _tc = tc;
            _factTo = factTo;
            _sidx2s = sidx2s;
            _dlOngoingDependencies = dldg;
        }

        Download create_(SOCID socid, To src, IDownloadCompletionListener listener, Token tk)
        {
            return new Download(this, socid, src, listener, tk, _dlOngoingDependencies);
        }
    }

    private Download(Factory f, SOCID socid, To src, @Nullable IDownloadCompletionListener l, Token tk,
            DownloadDependenciesGraph<SOCID> dldg)
    {
        _f = f;

        _socid = socid;
        _tk = tk;
        _src = src;
        _prio = _f._tc.prio();
        if (l != null) _ls.addListener_(l);
        _dlOngoingDependencies = dldg;
    }

    public SOCID socid()
    {
        return _socid;
    }

    public void include_(To src, @Nullable IDownloadCompletionListener listener)
    {
        _src.addAll_(src);
        _prio = Prio.higher(_prio, _f._tc.prio());
        if (listener != null) _ls.addListener_(listener);

        if (_f._tc.prio() != _prio) {
            l.info("prio changed. take effect in the next round");
        }
    }

    private static interface IDownloadCompletionListenerVisitor
    {
        void notify_(IDownloadCompletionListener l);
    }

    private void notifyListeners_(IDownloadCompletionListenerVisitor nl)
    {
        // because this download object is not used after the listeners
        // are notified, it's important not to miss the listeners that
        // are added during the iteration,
        Set<IDownloadCompletionListener> prev = Collections.emptySet();
        while (true) {
            Set<IDownloadCompletionListener> cur = _ls.beginIterating_();
            for (IDownloadCompletionListener l : cur) {
                if (!prev.contains(l)) nl.notify_(l);
            }
            if (!_ls.endIterating_()) break;
            else prev = new HashSet<IDownloadCompletionListener>(cur);
        }
    }

    /**
     * @return null if the download is successful and there' no kml version left
     */
    @Nullable Exception do_()
    {
        Prio prioOrg = _f._tc.prio();
        try {
            final DID from = doImpl_();
            notifyListeners_(new IDownloadCompletionListenerVisitor()
            {
                @Override
                public void notify_(IDownloadCompletionListener l)
                {
                    l.okay_(_socid, from);
                }
            });
            _f._dlstate.ended_(_socid, true);
            return null;

        } catch (final Exception e) {
            l.warn(_socid + ": " + Util.e(e, ExNoAvailDevice.class, ExNoPerm.class));
            notifyListeners_(new IDownloadCompletionListenerVisitor()
            {
                @Override
                public void notify_(IDownloadCompletionListener l)
                {
                    l.error_(_socid, e);
                }
            });
            _f._dlstate.ended_(_socid, false);
            return e;
        } finally {
            _dlOngoingDependencies.removeOutwardEdges_(_socid);
            _f._tc.setPrio(prioOrg);
        }
    }

    DID doImpl_() throws Exception
    {
        while (true) {

            if (_f._sidx2s.getNullable_(_socid.sidx()) == null) throw new ExAborted("no store");

            _f._tc.setPrio(_prio);

            DID replier = null;
            boolean started = false;
            try {
                // Check for dependency and expulsion. Even though GetComReply will check again,
                // we do it here to avoid useless round-trips with remote peers when possible.
                if (!_socid.cid().equals(CID.META)) {
                    OA oa = _f._ds.getAliasedOANullable_(_socid.soid());
                    if (oa == null) {
                        throw new ExDependsOn(new OCID(_socid.oid(), CID.META), null, false);
                    } else if (oa.isExpelled()) {
                        throw new ExAborted(_socid + " is expelled");
                    }
                }

                started = true;
                _f._dlstate.started_(_socid);
                DigestedMessage msg = _f._pgcc.rpc1_(_socid, _src, _tk);
                replier = msg.did();
                _f._pgcc.rpc2_(_socid, _src, msg, _tk);

                if (_f._nvc.getKMLVersion_(_socid).isZero_()) return replier;

                l.info("kml > 0. dl again");
                _src.avoid_(replier);

                reenqueue(started);

            } catch (ExAborted e) {
                throw e;

            } catch (ExOutOfSpace e) {
                throw e;

            } catch (ExNoAvailDevice e) {
                throw e;

            } catch (ExIncrementalDownload e) {
                reenqueue(started);
                l.warn("inc dl failed. full dl now.");

            } catch (ExDependsOn e) {
                reenqueue(started);
                final SOCID dep = new SOCID(_socid.sidx(), e.ocid());
                l.info(_socid + " depends on " + dep);
                _dlOngoingDependencies.addEdge_(_socid, dep);
                To to = e.did() == null ? _f._factTo.create_(_src) : _f._factTo.create_(e.did());
                try {
                    _f._dls.downloadSync_(dep, to, _tk, _socid);
                } catch (Exception e2) {
                    if (e.ignoreError()) l.info("dl dependency error, ignored: " + Util.e(e2));
                    else throw e2;
                }
                l.info("dependency " + _socid + " -> " + dep + " solved");

            } catch (ExStreamInvalid e) {
                reenqueue(started);
                if (e.getReason_() == InvalidationReason.UPDATE_IN_PROGRESS) {
                    onUpdateInProgress();
                } else {
                    onGeneralException(e, replier, false);
                }

            } catch (ExNoResource e) {
                reenqueue(started);
                l.info(_socid + ": " + Util.e(e));
                _tk.sleep_(3 * C.SEC, "retry dl (no rsc)");

            } catch (ExNoNewUpdate e) {
                reenqueue(started);
                if (_f._nvc.getKMLVersion_(_socid).isZero_()) {
                    l.info("recv " + ExNoNewUpdate.class + " & kml = 0. done");
                    return replier;
                } else {
                    l.info("recv " + ExNoNewUpdate.class + " & kml != 0. retry");
                    _src.avoid_(replier);
                }

            } catch (ExUpdateInProgress e) {
                reenqueue(started);
                onUpdateInProgress();

            } catch (ExNoPerm e) {
                // collector should only collect permitted components. no_perm may happen when other
                // user just changed the permission before this call.
                l.error(_socid + ": we have no perm");
                throw e;

            } catch (ExSenderHasNoPerm e) {
                reenqueue(started);
                l.error(_socid + ": sender has no perm");
                _src.avoid_(replier);

            } catch (Exception e) {
                reenqueue(started);
                onGeneralException(e, replier, true);
            }
        }
    }

    private void reenqueue(boolean started)
    {
        if (started) _f._dlstate.enqueued_(_socid);
    }

    private void onUpdateInProgress() throws ExNoResource, ExAborted
    {
        l.info(_socid + ": update in prog. retry later");
        // TODO exponential retry
        _tk.sleep_(3 * C.SEC, "retry dl (update in prog)");
    }

    private void onGeneralException(Exception e, DID replier, boolean mayPrintStack)
    {
        if (e instanceof RuntimeException) Util.fatal(e);

        //String eStr = mayPrintStack ? Util.e(e) : e.toString();
        // ignore the stack for now for less log output
        // RTN: retry now
        l.warn(_socid + ": " + Util.e(e) + " " + replier + " RTN");
        if (replier != null) _src.avoid_(replier);
    }

    @Override
    public String toString()
    {
        return _socid + " prio " + _prio;
    }
}
