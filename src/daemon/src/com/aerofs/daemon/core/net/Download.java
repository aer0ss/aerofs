package com.aerofs.daemon.core.net;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.dependence.DependencyEdge;
import com.aerofs.daemon.core.net.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.core.net.dependence.NameConflictDependencyEdge;
import com.aerofs.daemon.core.net.dependence.ParentDependencyEdge;
import com.aerofs.daemon.core.net.proto.ExSenderHasNoPerm;
import com.aerofs.daemon.core.net.proto.GetComponentCall;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.*;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
    private final Token _tk;
    private final SOCID _socid;
    private Prio _prio;

    // Before resolving a name conflict with a remote object, the local peer requests information
    // from the remote peer about its knowledge of the local name-conflicting object. The following
    // set represents all OCIDs *this* download object has requested---its download "memory";
    // if an encountered OCID has a name conflict and it has already been requested from the
    // remote peer, then we should proceed to resolve the name conflict, not ask for more
    // information.
    // N.B. it is tempting to use the DownloadDependenciesGraph to act as the "download memory,"
    // but that would require removing edges at the end of Download.do_, which is forbidden
    // (see Downloads.downloadSync_)
    private final Set<OCID> _requested = Sets.newTreeSet();
    private final Map<DID, Exception> _deviceFailures = Maps.newTreeMap();
    private final Factory _f;

    public static class Factory
    {
        private final TC _tc;
        private final DownloadState _dlstate;
        private final MapSIndex2Store _sidx2s;
        private final DirectoryService _ds;
        private final Downloads _dls;
        private final GetComponentCall _pgcc;
        private final NativeVersionControl _nvc;
        private final To.Factory _factTo;

        @Inject
        public Factory(NativeVersionControl nvc, GetComponentCall pgcc, Downloads dls,
                DirectoryService ds, DownloadState dlstate, TC tc,
                To.Factory factTo, MapSIndex2Store sidx2s)
        {

            _nvc = nvc;
            _pgcc = pgcc;
            _dls = dls;
            _ds = ds;
            _dlstate = dlstate;
            _tc = tc;
            _factTo = factTo;
            _sidx2s = sidx2s;
        }

        Download create_(SOCID socid, To src, IDownloadCompletionListener listener, Token tk)
        {
            return new Download(this, socid, src, listener, tk);
        }
    }

    private Download(Factory f, SOCID socid, To src, IDownloadCompletionListener l, Token tk)
    {
        _f = f;
        _socid = socid;
        _tk = tk;
        _src = src;
        _prio = _f._tc.prio();
        _ls.addListener_(l);
    }

    public void include_(To src, IDownloadCompletionListener listener)
    {
        _src.addAll_(src);
        _prio = Prio.higher(_prio, _f._tc.prio());
        _ls.addListener_(listener);

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
            else prev = Sets.newHashSet(cur);
        }
    }

    /**
     * @return null if the download is successful and there are no kml versions left
     */
    @Nullable Exception do_()
    {
        Prio prioOrg = _f._tc.prio();
        Exception returnValue = null;
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
            returnValue = null;

        } catch (final ExNoAvailDevice e) {
            l.warn(_socid + ": " + Util.e(e, ExNoAvailDevice.class));

            // This download object tracked all reasons (Exceptions) for why each device was
            // avoided. Thus if the To object indicated no devices were available, then inform
            // the listener about all attempted devices, and why they failed to deliver the socid.
            notifyListeners_(new IDownloadCompletionListenerVisitor()
            {
                @Override
                public void notify_(IDownloadCompletionListener l)
                {
                    l.onPerDeviceErrors_(_socid, _deviceFailures);
                }
            });
            returnValue = e;

        } catch (final Exception e) {
            l.warn(_socid + ": " + Util.e(e, ExNoPerm.class));
            notifyListeners_(new IDownloadCompletionListenerVisitor()
            {
                @Override
                public void notify_(IDownloadCompletionListener l)
                {
                    l.onGeneralError_(_socid, e);
                }
            });
            returnValue = e;

        } finally {
            _f._tc.setPrio(prioOrg);
        }

        _f._dlstate.ended_(_socid, (returnValue == null));
        return returnValue;
    }

    DID doImpl_() throws Exception
    {
        while (true) {

            if (_f._sidx2s.getNullable_(_socid.sidx()) == null) throw new ExAborted("no store");

            _f._tc.setPrio(_prio);

            DID replier = null;
            boolean started = false;
            try {
                // Check for dependency and expulsion. Even though GetComponentReply will check
                // again, we do it here to avoid useless round-trips with remote peers when
                // possible.
                if (!_socid.cid().isMeta()) {
                    OA oa = _f._ds.getAliasedOANullable_(_socid.soid());
                    if (oa == null) {
                        throw new ExDependsOn(new OCID(_socid.oid(), CID.META), null,
                                DependencyType.UNSPECIFIED, false);
                    } else if (oa.isExpelled()) {
                        throw new ExAborted(_socid + " is expelled");
                    }
                }

                started = true;
                _f._dlstate.started_(_socid);
                DigestedMessage msg = _f._pgcc.rpc1_(_socid, _src, _tk);
                replier = msg.did();
                _f._pgcc.rpc2_(_socid, msg, _requested, _tk);

                // If there are more KMLs for _socid, the Collector algorithm will ensure a new
                // Download object is created to resolve the KMLs
                // TODO (MJ) see if any DownloadState code can be simplified.
                return replier;

            } catch (ExAborted e) {
                throw e;

            } catch (ExOutOfSpace e) {
                throw e;

            } catch (ExNoAvailDevice e) {
                throw e;

            } catch (ExIncrementalDownload e) {
                reenqueue(started);
                l.warn("inc dl failed. full dl now.");

            } catch (ExNameConflictDependsOn e) {
                // N.B. this exception specializes ExDependsOn and thus must precede the
                // catch for ExDependsOn
                reenqueue(started);

                SOCID dst = new SOCID(_socid.sidx(), e._ocid);
                DependencyEdge dependency = new NameConflictDependencyEdge(_socid, dst, e._did,
                        e._parent, e._vRemote, e._meta, e._soidMsg, e._requested);
                onDependency_(dependency, e);

            } catch (ExDependsOn e) {
                reenqueue(started);

                SOCID dst = new SOCID(_socid.sidx(), e._ocid);
                DependencyEdge dependency = null;
                switch(e._type) {
                case PARENT:
                    dependency = new ParentDependencyEdge(_socid, dst);
                    break;
                // ExDependsOn should never be thrown with type NAME_CONFLICT, use
                // ExNameConflictDependsOn instead (TODO (MJ) this should be enforced by types...)
                case NAME_CONFLICT: assert false; break;
                default:
                    dependency = new DependencyEdge(_socid, dst);
                    break;
                }
                onDependency_(dependency, e);

            } catch (ExStreamInvalid e) {
                reenqueue(started);
                if (e.getReason_() == InvalidationReason.UPDATE_IN_PROGRESS) {
                    onUpdateInProgress();
                } else {
                    onGeneralException(e, replier);
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
                    avoidDevice_(replier, e);
                }

            } catch (ExUpdateInProgress e) {
                reenqueue(started);
                onUpdateInProgress();

            } catch (ExRestartWithHashComputed e) {
                reenqueue(started);

            } catch (ExNoPerm e) {
                // collector should only collect permitted components. no_perm may happen when other
                // user just changed the permission before this call.
                l.error(_socid + ": we have no perm");
                throw e;

            } catch (ExSenderHasNoPerm e) {
                reenqueue(started);
                l.error(_socid + ": sender has no perm");
                avoidDevice_(replier, e);

            } catch (Exception e) {
                reenqueue(started);
                onGeneralException(e, replier);
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

    private void onGeneralException(Exception e, DID replier)
    {
        if (e instanceof RuntimeException) Util.fatal(e);

        // RTN: retry now
        l.warn(_socid + ": " + Util.e(e) + " " + replier + " RTN");
        if (replier != null) avoidDevice_(replier, e);
    }

    private void onDependency_(DependencyEdge dependency, ExDependsOn e)
            throws Exception
    {
        To to = e._did == null ? _f._factTo.create_(_src) : _f._factTo.create_(e._did);
        l.info("download dependency " + dependency);
        try {
            _f._dls.downloadSync_(dependency, to, _tk);
        } catch (Exception e2) {
            if (e._ignoreError) l.info("dl dependency error, ignored: " + Util.e(e2));
            else throw e2;
        }
        l.info("dependency " + dependency + " solved");
        assert dependency.dst.oid().equals(e._ocid.oid());
        _requested.add(e._ocid);
    }

    private void avoidDevice_(DID replier, Exception reason)
    {
        _src.avoid_(replier);
        _deviceFailures.put(replier, reason);
        //assert e == null : replier + " " + e + " " + reason;
    }

    @Override
    public String toString()
    {
        return _socid + " prio " + _prio;
    }
}
