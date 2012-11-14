/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import java.io.IOException;
import java.sql.SQLException;
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
import com.aerofs.daemon.core.net.proto.GetComponentReply;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoAvailDevice;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExOutOfSpace;
import com.aerofs.lib.ex.ExUpdateInProgress;
import com.aerofs.lib.ex.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.exception.ExDependsOn;
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
    private static final FrequentDefectSender _defectSender = new FrequentDefectSender();

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

    // To track the reasons each device failed to download {@code _socid}, this map stores the
    // <DID, Exception> pairs following download failures.
    private final Map<DID, Exception> _did2e = Maps.newTreeMap();
    private final Factory _f;

    public static class Factory
    {
        private final TC _tc;
        private final DirectoryService _ds;
        private final NativeVersionControl _nvc;
        private final MapSIndex2Store _sidx2s;
        private final Downloads _dls;
        private final DownloadState _dlstate;
        private final GetComponentCall _gcc;
        private final GetComponentReply _gcr;
        private final To.Factory _factTo;

        @Inject
        public Factory(TC tc, DirectoryService ds, NativeVersionControl nvc, MapSIndex2Store sidx2s,
                Downloads dls, DownloadState dlstate, GetComponentCall gcc, GetComponentReply gcr,
                To.Factory factTo)
        {
            _tc = tc;
            _ds = ds;
            _nvc = nvc;
            _sidx2s = sidx2s;
            _dls = dls;
            _dlstate = dlstate;
            _gcc = gcc;
            _gcr = gcr;
            _factTo = factTo;
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
                    l.onPerDeviceErrors_(_socid, _did2e);
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

            @Nullable DID replier = null;
            boolean started = false;
            try {
                throwIfContentIsMissingMetaOrExpelled();

                started = true;
                _f._dlstate.started_(_socid);

                DigestedMessage msg = _f._gcc.remoteRequestComponent_(_socid, _src, _tk);
                replier = msg.did();

                // TODO (MJ) I have a dream: that we can distinguish between locally vs. remotely
                // generated exceptions
                _f._gcr.processReply_(_socid, msg, _requested, _tk);

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

            } catch (ExNoComponentWithSpecifiedVersion e) {
                reenqueue(started);
                if (l.isInfoEnabled()) {
                    l.info(_socid + ":recv " + ExNoComponentWithSpecifiedVersion.class + " & "
                            + "kml = " + _f._nvc.getKMLVersion_(_socid));
                }
                avoidDevice_(replier, e);

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

            } catch (IOException e) {
                reenqueue(started);
                _defectSender.logSendAsync("ioex in dl", e);
                onGeneralException(e, replier);

                // If there was a local IOException, then trying another device won't help. Just
                // re-throw and let the collector try again later
                throw e;

            } catch (Exception e) {
                reenqueue(started);
                onGeneralException(e, replier);
            }
        }
    }

    /**
     * Check for content->meta dependency and expulsion. Even though GetComponentReply will check
     * again, we do it here to avoid useless round-trips with remote peers when possible.
     */
    private void throwIfContentIsMissingMetaOrExpelled()
            throws SQLException, ExDependsOn, ExAborted
    {
        if (_socid.cid().isMeta()) return;

        final OA oa = _f._ds.getAliasedOANullable_(_socid.soid());
        if (oa == null) {
            throw new ExDependsOn(new OCID(_socid.oid(), CID.META), null,
                    DependencyType.UNSPECIFIED, false);
        } else if (oa.isExpelled()) {
            throw new ExAborted(_socid + " is expelled");
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
        if (e instanceof RuntimeException) SystemUtil.fatal(e);

        // RTN: retry now
        l.warn(_socid + ": " + Util.e(e) + " " + replier + " RTN");
        if (replier != null) avoidDevice_(replier, e);
    }

    private void onDependency_(DependencyEdge dependency, ExDependsOn e)
            throws Exception
    {
        To to = e._did == null ? _f._factTo.create_(_src) : _f._factTo.create_(e._did);
        l.info("dl dep " + dependency);
        try {
            _f._dls.downloadSync_(dependency, to, _tk);
        } catch (Exception e2) {
            // TODO (MJ) This is dangerous for name conflict resolution? I realize that
            // on *any* exception here, if ignoreError is true (as it is for a name conflict),
            // we will record that data has been requested and n'acked for e._ocid.
            // Then when we loop around and try to download this object again, we will take
            // the name-conflict resolution path in ReceiveAndApplyUpdate.resolveNameConflict_.
            // In many cases this could be the aliasing code path
            // but it might be undesired: what if the remote peer actually renamed the conflicted
            // file, but threw an exception for some reason instead of sending the file.
            // Instead we should catch ExNoComponentWithSpecifiedVersion, and only add the OCID to
            // _requested in that case.
            if (e._ignoreError) l.info("dl dep error, ignored: " + Util.e(e2));
            else throw e2;
        }
        l.info("dep " + dependency + " solved");
        assert dependency.dst.oid().equals(e._ocid.oid());
        _requested.add(e._ocid);
    }

    private void avoidDevice_(DID replier, Exception e)
    {
        _src.avoid_(replier);
        _did2e.put(replier, e);
    }

    @Override
    public String toString()
    {
        return _socid + " prio " + _prio;
    }
}
